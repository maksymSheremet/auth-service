package my.code.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.code.auth.dto.AuthenticationResponse;
import my.code.auth.entity.User;
import my.code.auth.repository.UserRepository;
import my.code.auth.service.JwtService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

import static my.code.auth.util.OAuth2Provider.fromString;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        if (authentication == null || authentication.getPrincipal() == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed");
            return;
        }

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;

        String provider = oauthToken.getAuthorizedClientRegistrationId().toUpperCase();
        Object providerIdRaw = oauth2User.getAttributes().get(fromString(provider).getNameAttributeKey());
        String email = (String) oauth2User.getAttributes().get("email");

        if (providerIdRaw == null) {
            log.error("No provider ID found in OAuth2 user attributes for provider: {}", provider);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "No provider ID available");
            return;
        }

        String providerId = String.valueOf(providerIdRaw);

        Optional<User> userOptional = "GITHUB".equals(provider)
                ? userRepository.findByProviderAndProviderId(provider, providerId)
                : findByEmail(email, provider);

        if (userOptional.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not found");
            return;
        }

        User user = userOptional.get();
        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(
                new AuthenticationResponse(accessToken, refreshToken)
        ));
        response.getWriter().flush();
    }

    private Optional<User> findByEmail(String email, String provider) {
        if (email == null) {
            log.error("No email found in OAuth2 user attributes for provider: {}", provider);
            return Optional.empty();
        }
        return userRepository.findByEmail(email);
    }
}
