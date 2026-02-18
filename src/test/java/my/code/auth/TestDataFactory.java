package my.code.auth;

import my.code.auth.database.entity.Role;
import my.code.auth.database.entity.Token;
import my.code.auth.database.entity.TokenType;
import my.code.auth.database.entity.User;
import my.code.auth.dto.AuthenticationRequest;
import my.code.auth.dto.ChangePasswordRequest;
import my.code.auth.dto.RegisterRequest;

import java.time.Instant;

/**
 * Centralized factory for test data.
 * Keeps test entities consistent and eliminates duplication across tests.
 */
public final class TestDataFactory {

    public static final String DEFAULT_EMAIL = "test@example.com";
    public static final String DEFAULT_PASSWORD = "Test123!";
    public static final String DEFAULT_ENCODED_PASSWORD = "$2a$10$dummyEncodedPasswordForTesting";
    public static final String DEFAULT_FIRST_NAME = "John";
    public static final String DEFAULT_LAST_NAME = "Doe";

    private TestDataFactory() {
    }

    // ======================== User ========================

    public static User.UserBuilder defaultUserBuilder() {
        return User.builder()
                .firstname(DEFAULT_FIRST_NAME)
                .lastname(DEFAULT_LAST_NAME)
                .email(DEFAULT_EMAIL)
                .password(DEFAULT_ENCODED_PASSWORD)
                .role(Role.USER)
                .provider("LOCAL")
                .providerId("LOCAL_" + System.nanoTime())
                .enabled(true);
    }

    public static User createUser() {
        return defaultUserBuilder().build();
    }

    public static User createUser(String email) {
        return defaultUserBuilder()
                .email(email)
                .build();
    }

    public static User createUser(String email, Role role) {
        return defaultUserBuilder()
                .email(email)
                .role(role)
                .build();
    }

    public static User createUserWithId(Long id, String email) {
        return defaultUserBuilder()
                .id(id)
                .email(email)
                .build();
    }

    public static User createAdmin() {
        return defaultUserBuilder()
                .email("admin@example.com")
                .role(Role.ADMIN)
                .build();
    }

    // ======================== Token ========================

    public static Token createToken(User user, String jwt, TokenType type) {
        return Token.builder()
                .token(jwt)
                .tokenType(type)
                .user(user)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    public static Token createAccessToken(User user, String jwt) {
        return createToken(user, jwt, TokenType.BEARER);
    }

    public static Token createRefreshToken(User user, String jwt) {
        return createToken(user, jwt, TokenType.REFRESH);
    }

    public static Token createRevokedToken(User user, String jwt) {
        Token token = createAccessToken(user, jwt);
        token.setRevoked(true);
        token.setExpired(true);
        return token;
    }

    // ======================== DTOs ========================

    public static RegisterRequest createRegisterRequest() {
        return new RegisterRequest(DEFAULT_FIRST_NAME, DEFAULT_LAST_NAME, DEFAULT_EMAIL, DEFAULT_PASSWORD);
    }

    public static RegisterRequest createRegisterRequest(String email) {
        return new RegisterRequest(DEFAULT_FIRST_NAME, DEFAULT_LAST_NAME, email, DEFAULT_PASSWORD);
    }

    public static AuthenticationRequest createAuthRequest() {
        return new AuthenticationRequest(DEFAULT_EMAIL, DEFAULT_PASSWORD);
    }

    public static AuthenticationRequest createAuthRequest(String email, String password) {
        return new AuthenticationRequest(email, password);
    }

    public static ChangePasswordRequest createChangePasswordRequest(String currentPassword, String newPassword) {
        return new ChangePasswordRequest(currentPassword, newPassword);
    }
}
