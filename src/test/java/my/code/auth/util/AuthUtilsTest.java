package my.code.auth.util;

import my.code.auth.database.entity.Role;
import my.code.auth.database.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AuthUtils}.
 * Directly manipulates SecurityContextHolder — no Spring context needed.
 */
class AuthUtilsTest {

    private AuthUtils authUtils;

    @BeforeEach
    void setUp() {
        authUtils = new AuthUtils();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthenticatedUser(User user) {
        var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private User createTestUser() {
        return User.builder()
                .id(42L)
                .email("john@example.com")
                .firstname("John")
                .lastname("Doe")
                .role(Role.USER)
                .provider("LOCAL")
                .providerId("LOCAL_42")
                .enabled(true)
                .build();
    }

    @Nested
    @DisplayName("getCurrentUser")
    class GetCurrentUser {

        @Test
        @DisplayName("authenticated user → returns Optional with user")
        void authenticatedUser() {
            User user = createTestUser();
            setAuthenticatedUser(user);

            Optional<User> result = authUtils.getCurrentUser();

            assertTrue(result.isPresent());
            assertEquals(42L, result.get().getId());
            assertEquals("john@example.com", result.get().getEmail());
        }

        @Test
        @DisplayName("no authentication → returns empty Optional")
        void noAuthentication() {
            Optional<User> result = authUtils.getCurrentUser();

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("non-User principal (e.g. String) → returns empty Optional")
        void nonUserPrincipal() {
            var auth = new UsernamePasswordAuthenticationToken("just-a-string", null);
            SecurityContextHolder.getContext().setAuthentication(auth);

            Optional<User> result = authUtils.getCurrentUser();

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("getCurrentEmail")
    class GetCurrentEmail {

        @Test
        @DisplayName("authenticated → returns email")
        void returnsEmail() {
            setAuthenticatedUser(createTestUser());

            assertEquals("john@example.com", authUtils.getCurrentEmail());
        }

        @Test
        @DisplayName("no authentication → returns null")
        void returnsNull() {
            assertNull(authUtils.getCurrentEmail());
        }
    }

    @Nested
    @DisplayName("getCurrentUserId")
    class GetCurrentUserId {

        @Test
        @DisplayName("authenticated → returns user id")
        void returnsId() {
            setAuthenticatedUser(createTestUser());

            assertEquals(42L, authUtils.getCurrentUserId());
        }

        @Test
        @DisplayName("no authentication → returns null")
        void returnsNull() {
            assertNull(authUtils.getCurrentUserId());
        }
    }

    @Nested
    @DisplayName("getCurrentRole")
    class GetCurrentRole {

        @Test
        @DisplayName("USER role → returns ROLE_USER")
        void returnsUserRole() {
            setAuthenticatedUser(createTestUser());

            assertEquals("ROLE_USER", authUtils.getCurrentRole());
        }

        @Test
        @DisplayName("ADMIN role → returns ROLE_ADMIN")
        void returnsAdminRole() {
            User admin = User.builder()
                    .id(1L).email("admin@example.com").role(Role.ADMIN)
                    .provider("LOCAL").providerId("LOCAL_ADM").enabled(true).build();
            setAuthenticatedUser(admin);

            assertEquals("ROLE_ADMIN", authUtils.getCurrentRole());
        }

        @Test
        @DisplayName("no authentication → returns null")
        void returnsNull() {
            assertNull(authUtils.getCurrentRole());
        }
    }
}