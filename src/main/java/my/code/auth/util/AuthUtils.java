package my.code.auth.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.code.auth.entity.User;
import my.code.auth.exception.InvalidTokenException;
import my.code.auth.service.JwtService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthUtils {

    private final JwtService jwtService;

    public String getCurrentUserEmail(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user.getEmail();
        }

        String token = extractTokenFromHeader(request);
        return jwtService.extractUsername(token);
    }

    public String getCurrentUserRole(HttpServletRequest request) {
        String token = extractTokenFromHeader(request);
        return jwtService.extractUserRole(token);
    }

    public Long getCurrentUserId(HttpServletRequest request) {
        String token = extractTokenFromHeader(request);
        return jwtService.extractUserId(token);
    }

    private String extractTokenFromHeader(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.error("Missing or invalid Authorization header");
            throw new InvalidTokenException("Missing or invalid Authorization header");
        }
        return authHeader.substring(7);
    }
}
