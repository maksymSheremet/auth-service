package my.code.auth.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import my.code.auth.config.security.JwtProperties;
import my.code.auth.database.entity.Role;
import my.code.auth.database.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JwtService}.
 * Uses real cryptographic keys — no mocking of JWT internals.
 * No Spring context required.
 */
class JwtServiceTest {

    private JwtService jwtService;
    private JwtProperties jwtProperties;
    private SecretKey signingKey;
    private User user;

    private static final String ISSUER = "auth-service";
    private static final long ACCESS_EXPIRATION_MS = 3600000L;   // 1 hour
    private static final long REFRESH_EXPIRATION_MS = 604800000L; // 7 days

    @BeforeEach
    void setUp() {
        signingKey = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256);
        String base64Secret = Base64.getEncoder().encodeToString(signingKey.getEncoded());

        jwtProperties = new JwtProperties();
        jwtProperties.setSecret(base64Secret);
        jwtProperties.setExpiration(ACCESS_EXPIRATION_MS);
        jwtProperties.setRefreshExpiration(REFRESH_EXPIRATION_MS);
        jwtProperties.setIssuer(ISSUER);

        jwtService = new JwtService(jwtProperties);
        jwtService.initKey();

        user = User.builder()
                .id(42L)
                .email("john@example.com")
                .firstname("John")
                .lastname("Doe")
                .role(Role.USER)
                .provider("LOCAL")
                .providerId("LOCAL_123")
                .enabled(true)
                .build();
    }

    @Nested
    @DisplayName("generateToken (access)")
    class GenerateAccessToken {

        @Test
        @DisplayName("returns valid 3-part JWT")
        void returnsValidJwt() {
            String token = jwtService.generateToken(user);

            assertNotNull(token);
            assertFalse(token.isBlank());
            assertEquals(3, token.split("\\.").length);
        }

        @Test
        @DisplayName("subject is user email")
        void subjectIsEmail() {
            String token = jwtService.generateToken(user);
            assertEquals("john@example.com", jwtService.extractUsername(token));
        }

        @Test
        @DisplayName("contains userId claim")
        void containsUserId() {
            String token = jwtService.generateToken(user);
            assertEquals(42L, jwtService.extractUserId(token));
        }

        @Test
        @DisplayName("contains ROLE_USER")
        void containsRole() {
            String token = jwtService.generateToken(user);
            assertEquals("ROLE_USER", jwtService.extractRole(token));
        }

        @Test
        @DisplayName("tokenType is ACCESS")
        void tokenTypeIsAccess() {
            String token = jwtService.generateToken(user);
            assertEquals("ACCESS", jwtService.extractTokenType(token));
        }

        @Test
        @DisplayName("isRefreshToken → false")
        void isNotRefreshToken() {
            String token = jwtService.generateToken(user);
            assertFalse(jwtService.isRefreshToken(token));
        }

        @Test
        @DisplayName("expiration ≈ now + 1 hour")
        void expirationIsCorrect() {
            Instant before = Instant.now();
            String token = jwtService.generateToken(user);

            Instant expiration = jwtService.extractExpiration(token);

            assertTrue(expiration.isAfter(before.plusMillis(ACCESS_EXPIRATION_MS - 2000)));
            assertTrue(expiration.isBefore(before.plusMillis(ACCESS_EXPIRATION_MS + 2000)));
        }
    }

    @Nested
    @DisplayName("generateRefreshToken")
    class GenerateRefreshToken {

        @Test
        @DisplayName("returns non-blank JWT")
        void returnsToken() {
            String token = jwtService.generateRefreshToken(user);
            assertNotNull(token);
            assertFalse(token.isBlank());
        }

        @Test
        @DisplayName("subject is user email")
        void subjectIsEmail() {
            String token = jwtService.generateRefreshToken(user);
            assertEquals("john@example.com", jwtService.extractUsername(token));
        }

        @Test
        @DisplayName("tokenType is REFRESH")
        void tokenTypeIsRefresh() {
            String token = jwtService.generateRefreshToken(user);
            assertEquals("REFRESH", jwtService.extractTokenType(token));
        }

        @Test
        @DisplayName("isRefreshToken → true")
        void isRefreshToken() {
            String token = jwtService.generateRefreshToken(user);
            assertTrue(jwtService.isRefreshToken(token));
        }

        @Test
        @DisplayName("expires later than access token")
        void longerExpiration() {
            String access = jwtService.generateToken(user);
            String refresh = jwtService.generateRefreshToken(user);

            assertTrue(jwtService.extractExpiration(refresh)
                    .isAfter(jwtService.extractExpiration(access)));
        }
    }

    @Nested
    @DisplayName("isTokenValid")
    class IsTokenValid {

        @Test
        @DisplayName("valid access token + matching user → true")
        void validToken() {
            String token = jwtService.generateToken(user);
            assertTrue(jwtService.isTokenValid(token, user));
        }

        @Test
        @DisplayName("valid refresh token + matching user → true")
        void validRefreshToken() {
            String token = jwtService.generateRefreshToken(user);
            assertTrue(jwtService.isTokenValid(token, user));
        }

        @Test
        @DisplayName("wrong user email → false")
        void wrongUser() {
            String token = jwtService.generateToken(user);

            User other = User.builder()
                    .id(99L).email("other@example.com").role(Role.USER)
                    .provider("LOCAL").providerId("LOCAL_X").enabled(true).build();

            assertFalse(jwtService.isTokenValid(token, other));
        }

        @Test
        @DisplayName("expired token → false")
        void expiredToken() {
            JwtProperties zeroProps = new JwtProperties();
            zeroProps.setSecret(jwtProperties.getSecret());
            zeroProps.setExpiration(0L);
            zeroProps.setRefreshExpiration(0L);
            zeroProps.setIssuer(ISSUER);

            JwtService zeroService = new JwtService(zeroProps);
            zeroService.initKey();

            String token = zeroService.generateToken(user);
            assertFalse(zeroService.isTokenValid(token, user));
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("malformed token → throws")
        void malformedToken() {
            assertThrows(Exception.class, () -> jwtService.extractUsername("not.a.jwt"));
        }

        @Test
        @DisplayName("different signing key → throws")
        void differentKey() {
            SecretKey otherKey = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256);
            String foreign = Jwts.builder()
                    .subject("john@example.com").issuer(ISSUER)
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plusSeconds(3600)))
                    .signWith(otherKey).compact();

            assertThrows(Exception.class, () -> jwtService.extractUsername(foreign));
        }

        @Test
        @DisplayName("wrong issuer → throws")
        void wrongIssuer() {
            String bad = Jwts.builder()
                    .subject("john@example.com").issuer("wrong-issuer")
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plusSeconds(3600)))
                    .signWith(signingKey).compact();

            assertThrows(Exception.class, () -> jwtService.extractUsername(bad));
        }

        @Test
        @DisplayName("ADMIN role is stored correctly")
        void adminRole() {
            User admin = User.builder()
                    .id(1L).email("admin@example.com").role(Role.ADMIN)
                    .provider("LOCAL").providerId("LOCAL_ADM").enabled(true).build();

            assertEquals("ROLE_ADMIN", jwtService.extractRole(jwtService.generateToken(admin)));
        }

        @Test
        @DisplayName("different users → different tokens")
        void differentUsers() {
            User user2 = User.builder()
                    .id(2L).email("user2@example.com").role(Role.USER)
                    .provider("LOCAL").providerId("LOCAL_2").enabled(true).build();

            assertNotEquals(jwtService.generateToken(user), jwtService.generateToken(user2));
        }

        @Test
        @DisplayName("access ≠ refresh for same user")
        void accessNotEqualsRefresh() {
            assertNotEquals(jwtService.generateToken(user), jwtService.generateRefreshToken(user));
        }
    }
}
