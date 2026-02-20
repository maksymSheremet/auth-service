package my.code.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * Provides a dummy ClientRegistrationRepository so SecurityConfig's
 * oauth2FilterChain can initialize without real OAuth2 provider credentials.
 */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    public ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration dummyRegistration = ClientRegistration.withRegistrationId("test")
                .clientId("test-client-id")
                .clientSecret("test-client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://example.com/oauth2/authorize")
                .tokenUri("https://example.com/oauth2/token")
                .userInfoUri("https://example.com/oauth2/userinfo")
                .userNameAttributeName("sub")
                .build();
        return new InMemoryClientRegistrationRepository(dummyRegistration);
    }
}
