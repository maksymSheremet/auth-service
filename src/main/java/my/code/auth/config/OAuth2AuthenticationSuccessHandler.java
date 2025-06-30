package my.code.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.code.auth.dto.AuthenticationResponse;
import my.code.auth.entity.User;
import my.code.auth.exception.UserNotFoundException;
import my.code.auth.repository.UserRepository;
import my.code.auth.service.JwtService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

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
        if (authentication != null && authentication.getPrincipal() != null) {
            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;

            String provider = oauthToken.getAuthorizedClientRegistrationId().toUpperCase();
            String providerId = String.valueOf(oauth2User.getAttributes().get(fromString(provider).getNameAttributeKey()));
            String email = (String) oauth2User.getAttributes().get("email");

            if (providerId == null) {
                log.error("No provider ID found in OAuth2 user attributes");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "No provider ID available");
                return;
            }

            User user;
            if ("GITHUB".equals(provider)) {
                user = userRepository.findByProviderAndProviderId(provider, providerId)
                        .orElseThrow(() -> {
                            log.warn("User not found for provider: {}, providerId: {}", provider, providerId);
                            return new UserNotFoundException("User not found with provider: " + provider + ", providerId: " + providerId);
                        });
            } else {
                if (email == null) {
                    log.error("No email found in OAuth2 user attributes for provider: {}", provider);
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "No email available");
                    return;
                }
                user = userRepository.findByEmail(email)
                        .orElseThrow(() -> {
                            log.warn("User not found for provider: {}, email: {}", provider, email);
                            return new UserNotFoundException("User not found with email: " + email);
                        });
            }

            String accessToken = jwtService.generateToken(user);
            String refreshToken = jwtService.generateRefreshToken(user);

            try {
                response.setContentType("application/json");
                response.getWriter().write(objectMapper.writeValueAsString(
                        new AuthenticationResponse(accessToken, refreshToken)
                ));
                response.getWriter().flush();
            } catch (IOException e) {
                log.error("Failed to write JSON response", e);
                throw new RuntimeException("Failed to write JSON response", e);
            }
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed");
        }
    }
}
