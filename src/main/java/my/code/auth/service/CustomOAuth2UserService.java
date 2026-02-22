package my.code.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.code.auth.database.entity.Role;
import my.code.auth.database.entity.User;
import my.code.auth.database.repository.UserRepository;
import my.code.auth.event.UserRegisteredEvent;
import my.code.auth.kafka.OutboxEventPublisher;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import static my.code.auth.util.OAuth2Provider.fromString;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String provider = userRequest.getClientRegistration().getRegistrationId().toUpperCase();

        OAuth2UserAttributes userAttributes = extractUserAttributes(attributes, provider);

        User user = userRepository.findByProviderAndProviderId(provider, userAttributes.providerId())
                .map(existing -> updateIfNeeded(existing, userAttributes))
                .orElseGet(() -> createNewUser(userAttributes, provider));

        return new DefaultOAuth2User(
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole().getAuthority())),
                attributes,
                fromString(provider).getNameAttributeKey()
        );
    }

    private User createNewUser(OAuth2UserAttributes attrs, String provider) {
        User user = User.builder()
                .email(attrs.email())
                .firstname(attrs.firstName() != null ? attrs.firstName() : attrs.email().split("@")[0])
                .lastname(attrs.lastName())
                .password(null)
                .provider(provider)
                .providerId(attrs.providerId())
                .role(Role.USER)
                .enabled(true)
                .build();
        User saved = userRepository.save(user);

        UserRegisteredEvent event = new UserRegisteredEvent(
                saved.getId(),
                saved.getEmail(),
                saved.getFirstname() + " " + (saved.getLastname() != null ? saved.getLastname() : ""),
                null, null, null,
                Instant.now()
        );
        outboxEventPublisher.publish(String.valueOf(saved.getId()), "USER_REGISTERED", event);

        log.info("Created new OAuth2 user: email={}, provider={}", saved.getEmail(), provider);
        return saved;
    }

    private User updateIfNeeded(User user, OAuth2UserAttributes attrs) {
        boolean updated = false;

        if (attrs.firstName() != null && !attrs.firstName().equals(user.getFirstname())) {
            user.setFirstname(attrs.firstName());
            updated = true;
        }
        if (attrs.lastName() != null && !attrs.lastName().equals(user.getLastname())) {
            user.setLastname(attrs.lastName());
            updated = true;
        }

        if (updated) {
            user = userRepository.save(user);
            log.debug("Updated OAuth2 user: email={}", user.getEmail());
        }
        return user;
    }

    private OAuth2UserAttributes extractUserAttributes(Map<String, Object> attributes, String provider) {
        return switch (provider) {
            case "GOOGLE" -> extractGoogleAttributes(attributes);
            case "GITHUB" -> extractGitHubAttributes(attributes);
            default -> throw new IllegalArgumentException("Unsupported OAuth2 provider: " + provider);
        };
    }

    private OAuth2UserAttributes extractGoogleAttributes(Map<String, Object> attrs) {
        String email = (String) attrs.get("email");
        String providerId = (String) attrs.get("sub");

        if (email == null) throw new IllegalArgumentException("Email not provided by Google");
        if (providerId == null) throw new IllegalArgumentException("Provider ID (sub) not provided by Google");

        String firstName = (String) attrs.get("given_name");
        String lastName = (String) attrs.get("family_name");

        if (firstName == null) {
            String fullName = (String) attrs.get("name");
            if (fullName != null) {
                String[] parts = fullName.split(" ", 2);
                firstName = parts[0];
                lastName = parts.length > 1 ? parts[1] : null;
            }
        }

        return new OAuth2UserAttributes(email, firstName, lastName, providerId);
    }

    private OAuth2UserAttributes extractGitHubAttributes(Map<String, Object> attrs) {
        String providerId = String.valueOf(attrs.get("id"));
        String login = (String) attrs.get("login");
        String email = (String) attrs.get("email");

        if (email == null) {
            log.warn("Email not provided by GitHub for login={}, using fallback", login);
            email = "github-" + providerId + "@placeholder.local";
        }

        String firstName = login;
        String lastName = null;
        String fullName = (String) attrs.get("name");

        if (fullName != null && fullName.contains(" ")) {
            String[] parts = fullName.split(" ", 2);
            firstName = parts[0];
            lastName = parts[1];
        }

        return new OAuth2UserAttributes(email, firstName, lastName, providerId);
    }

    private record OAuth2UserAttributes(String email, String firstName, String lastName, String providerId) {
        OAuth2UserAttributes {
            Objects.requireNonNull(providerId, "Provider ID cannot be null");
            Objects.requireNonNull(email, "Email cannot be null");
        }
    }
}
