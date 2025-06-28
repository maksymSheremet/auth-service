package my.code.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.code.auth.entity.Role;
import my.code.auth.entity.User;
import my.code.auth.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String provider = userRequest.getClientRegistration().getRegistrationId();

        try {
            String email = extractEmail(attributes, provider);
            String name = extractName(attributes, provider);

            User user = userRepository.findByEmail(email)
                    .orElseGet(() -> createUser(email, name, provider));

            user = userRepository.save(user);

            return new DefaultOAuth2User(
                    Collections.singleton(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                    attributes,
                    getNameAttributeKey(provider)
            );
        } catch (IllegalArgumentException e) {
            log.error("Failed to process OAuth2 user for provider {}: {}", provider, e.getMessage());
            throw e;
        }
    }

    private String extractEmail(Map<String, Object> attributes, String provider) {
        if ("google".equals(provider)) {
            String email = (String) attributes.get("email");
            if (email == null) {
                throw new IllegalArgumentException("Email not provided by Google");
            }
            return email;
        } else if ("github".equals(provider)) {
            String email = (String) attributes.get("email");
            if (email == null) {
                String login = (String) attributes.get("login");
                return login + "@github.com";
            }
            return email;
        }
        throw new IllegalArgumentException("Unsupported provider: " + provider);
    }

    private String extractName(Map<String, Object> attributes, String provider) {
        if ("google".equals(provider)) {
            return (String) attributes.get("name");
        } else if ("github".equals(provider)) {
            return (String) attributes.get("login");
        }
        return null;
    }

    private User createUser(String email, String name, String provider) {
        return User.builder()
                .email(email)
                .firstname(name != null ? name : email.split("@")[0])
                .password(null) // OAuth2 users don't need a password
                .provider(provider)
                .role(Role.USER)
                .enabled(true)
                .build();
    }

    private String getNameAttributeKey(String provider) {
        if ("google".equals(provider)) {
            return "sub";
        } else if ("github".equals(provider)) {
            return "id";
        }
        return "id";
    }
}
