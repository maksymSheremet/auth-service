package my.code.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.code.auth.database.entity.Role;
import my.code.auth.database.entity.User;
import my.code.auth.database.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import static my.code.auth.util.OAuth2Provider.fromString;


@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        log.info("Processing OAuth2 user request for provider: {}", userRequest.getClientRegistration().getRegistrationId());
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String provider = userRequest.getClientRegistration().getRegistrationId().toUpperCase();

        try {
            OAuth2UserAttributes userAttributes = extractUserAttributes(attributes, provider);
            User user = userRepository.findByProviderAndProviderId(provider, userAttributes.providerId())
                    .orElseGet(() -> createOrUpdateUser(userAttributes, provider));

            user = userRepository.save(user);

            return new DefaultOAuth2User(
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                    attributes,
                    fromString(provider).getNameAttributeKey()
            );
        } catch (IllegalArgumentException e) {
            log.error("Failed to process OAuth2 user for provider {}: {}", provider, e.getMessage());
            throw e;
        }
    }

    private OAuth2UserAttributes extractUserAttributes(Map<String, Object> attributes, String provider) {
        return switch (provider) {
            case "GOOGLE" -> {
                String email = (String) attributes.get("email");

                if (email == null) {
                    throw new IllegalArgumentException("Email not provided by Google");
                }

                String firstName = (String) attributes.get("given_name");
                String lastName = (String) attributes.get("family_name");
                String providerId = (String) attributes.get("sub");

                if (providerId == null) {
                    throw new IllegalArgumentException("Provider ID (sub) not provided by Google");
                }

                if (firstName == null || lastName == null) {
                    String fullName = (String) attributes.get("name");
                    if (fullName != null) {
                        String[] nameParts = fullName.split(" ", 2);
                        firstName = nameParts[0];
                        lastName = nameParts.length > 1 ? nameParts[1] : null;
                    }
                }

                yield new OAuth2UserAttributes(email, firstName, lastName, providerId);
            }
            case "GITHUB" -> {
                String email = (String) attributes.get("email");
                String firstName = (String) attributes.get("login");
                String providerId = String.valueOf(attributes.get("id"));

                if (providerId == null) {
                    throw new IllegalArgumentException("Provider ID (id) not provided by GitHub");
                }

                if (email == null) {
                    log.warn("Email not provided by GitHub for user with login: {}, using default email", firstName);
                    email = "github-user-" + providerId + "@default.com";
                }

                String fullName = (String) attributes.get("name");
                String lastName = null;

                if (fullName != null && fullName.contains(" ")) {
                    String[] nameParts = fullName.split(" ", 2);
                    firstName = nameParts[0];
                    lastName = nameParts.length > 1 ? nameParts[1] : null;
                    log.info("Splitting login {} into firstName: {}, lastName: {}", fullName, firstName, lastName);
                }

                yield new OAuth2UserAttributes(email, firstName, lastName, providerId);
            }
            default -> throw new IllegalArgumentException("Unsupported provider: " + provider);
        };
    }

    private User createOrUpdateUser(OAuth2UserAttributes attributes, String provider) {
        return userRepository.findByEmail(attributes.email())
                .map(existingUser -> updateUser(existingUser, attributes))
                .orElseGet(() -> createNewUser(attributes, provider));
    }

    private User createNewUser(OAuth2UserAttributes attributes, String provider) {
        log.info("Creating new user with email: {}", attributes.email());
        return User.builder()
                .email(attributes.email())
                .firstname(attributes.firstName() != null ? attributes.firstName() : attributes.email() != null ? attributes.email().split("@")[0] : "Unknown")
                .lastname(attributes.lastName())
                .password(null)
                .provider(provider)
                .providerId(attributes.providerId())
                .role(Role.USER)
                .enabled(true)
                .build();
    }

    private User updateUser(User existingUser, OAuth2UserAttributes attributes) {
        log.info("Updating user with email: {}", existingUser.getEmail());
        boolean needsUpdate = !existingUser.getProviderId().equals(attributes.providerId()) ||
                              (attributes.firstName() != null && !attributes.firstName().equals(existingUser.getFirstname())) ||
                              (attributes.lastName() != null && !attributes.lastName().equals(existingUser.getLastname())) ||
                              (attributes.email() != null && !attributes.email().equals(existingUser.getEmail()));

        if (needsUpdate) {
            existingUser.setProviderId(attributes.providerId());
            if (attributes.firstName() != null) {
                existingUser.setFirstname(attributes.firstName());
            }
            if (attributes.lastName() != null) {
                existingUser.setLastname(attributes.lastName());
            }
            if (attributes.email() != null) {
                existingUser.setEmail(attributes.email());
            }
        }
        return existingUser;
    }

    private record OAuth2UserAttributes(String email, String firstName, String lastName, String providerId) {
        public OAuth2UserAttributes {
            if (!"GITHUB".equals(providerId) && email == null) {
                throw new IllegalArgumentException("Email cannot be null for provider other than GitHub");
            }
            Objects.requireNonNull(providerId, "ProviderId cannot be null");
        }
    }
}
