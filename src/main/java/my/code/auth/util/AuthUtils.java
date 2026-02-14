package my.code.auth.util;

import my.code.auth.database.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Utility for extracting authenticated user info from SecurityContext.
 */
@Component
public class AuthUtils {

    /**
     * Returns the authenticated User entity, if available.
     */
    public Optional<User> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public String getCurrentEmail() {
        return getCurrentUser()
                .map(User::getEmail)
                .orElse(null);
    }

    public Long getCurrentUserId() {
        return getCurrentUser()
                .map(User::getId)
                .orElse(null);
    }

    public String getCurrentRole() {
        return getCurrentUser()
                .map(user -> user.getRole().getAuthority())
                .orElse(null);
    }
}
