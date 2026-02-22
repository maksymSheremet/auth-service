package my.code.auth.service;

import my.code.auth.database.entity.Role;
import my.code.auth.database.entity.Token;
import my.code.auth.database.entity.TokenType;
import my.code.auth.database.entity.User;
import my.code.auth.database.repository.TokenRepository;
import my.code.auth.exception.InvalidTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TokenService}
 * Mocks: TokenRepository, JwtService.
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private TokenService tokenService;

    @Captor
    private ArgumentCaptor<Token> tokenCaptor;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("john@example.com")
                .role(Role.USER)
                .provider("LOCAL")
                .providerId("LOCAL_1")
                .enabled(true)
                .build();
    }

    @Nested
    @DisplayName("sevaAccessToken")
    class SaveAccessToken {

        @Test
        @DisplayName("revokes existing tokens, then saves BEARER token")
        void revokesAndSaves() {
            String jwt = "access-jwt-token";
            Instant expiration = Instant.now().plusSeconds(3600);
            when(jwtService.extractExpiration(jwt)).thenReturn(expiration);

            tokenService.saveAccessToken(user, jwt);

            verify(tokenRepository).revokeAllTokensByUser(1L);
            verify(tokenRepository).save(tokenCaptor.capture());

            Token saved = tokenCaptor.getValue();
            assertEquals(jwt, saved.getTokenValue());
            assertEquals(TokenType.BEARER, saved.getTokenType());
            assertEquals(user, saved.getUser());
            assertEquals(expiration, saved.getExpiresAt());
            assertNotNull(saved.getExpiresAt());
        }
    }

    @Nested
    @DisplayName("saveRefreshToken")
    class SaveRefreshToken {

        @Test
        @DisplayName("saves REFRESH token without revoking existing")
        void saveRefreshToken() {
            String jwt = "refresh-jwt-token";
            Instant expiration = Instant.now().plusSeconds(604800);
            when(jwtService.extractExpiration(jwt)).thenReturn(expiration);

            tokenService.saveRefreshToken(user, jwt);

            verify(tokenRepository, never()).revokeAllTokensByUser(anyLong());
            verify(tokenRepository).save(tokenCaptor.capture());

            Token saved = tokenCaptor.getValue();
            assertEquals(jwt, saved.getTokenValue());
            assertEquals(TokenType.REFRESH, saved.getTokenType());
            assertEquals(user, saved.getUser());
        }
    }

    @Nested
    @DisplayName("revokeAllUserTokens")
    class RevokeAllUserTokens {

        @Test
        @DisplayName("delegates to repository bulk update")
        void delegatesToRepository() {
            when(tokenRepository.revokeAllTokensByUser(1L)).thenReturn(3);

            tokenService.revokeAllUserTokens(1L);

            verify(tokenRepository).revokeAllTokensByUser(1L);
        }

        @Test
        @DisplayName("works when no tokens to revoke")
        void noTokensToRevoke() {
            when(tokenRepository.revokeAllTokensByUser(1L)).thenReturn(0);

            assertDoesNotThrow(() -> tokenService.revokeAllUserTokens(1L));
        }
    }

    @Nested
    @DisplayName("revokeByRefreshToken")
    class RevokeByRefreshToken {

        @Test
        @DisplayName("valid refresh token -> revokes all user tokens")
        void validRefreshToken() {
            Token refreshToken = Token.builder()
                    .id(10L)
                    .tokenValue("refresh-jwt")
                    .tokenType(TokenType.REFRESH)
                    .user(user)
                    .createdAt(Instant.now())
                    .build();

            when(tokenRepository.findByTokenValue("refresh-jwt")).thenReturn(Optional.of(refreshToken));

            tokenService.revokeByRefreshToken("refresh-jwt");

            verify(tokenRepository).findByTokenValue("refresh-jwt");
        }

        @Test
        @DisplayName("token not found -> throws InvalidTokenException")
        void tokenNotFound() {
            when(tokenRepository.findByTokenValue("unknown")).thenReturn(Optional.empty());

            assertThrows(InvalidTokenException.class, () -> tokenService.revokeByRefreshToken("unknown"));
        }

        @Test
        @DisplayName("BEARER token (not REFRESH) -> throws InvalidTokenException")
        void bearerTokenThrows() {
            Token bearerToken = Token.builder()
                    .id(5L)
                    .tokenValue("bearer-jwt")
                    .tokenType(TokenType.BEARER)
                    .user(user)
                    .createdAt(Instant.now())
                    .build();

            when(tokenRepository.findByTokenValue("bearer-jwt")).thenReturn(Optional.of(bearerToken));

            assertThrows(InvalidTokenException.class, () -> tokenService.revokeByRefreshToken("bearer-jwt"));
        }
    }

    @Nested
    @DisplayName("isTokenActiveInDb")
    class IsTokenActiveInDb {

        @Test
        @DisplayName("active token (not expired, not revoked) -> true")
        void activeToken() {
            Token active = Token.builder()
                    .tokenValue("active-jwt")
                    .expired(false)
                    .revoked(false)
                    .createdAt(Instant.now())
                    .build();

            when(tokenRepository.findByTokenValue("active-jwt")).thenReturn(Optional.of(active));

            assertTrue(tokenService.isTokenActiveInDb("active-jwt"));
        }

        @Test
        @DisplayName("expired token -> false")
        void expiredToken() {
            Token expired = Token.builder()
                    .tokenValue("expired-jwt")
                    .expired(true)
                    .revoked(false)
                    .createdAt(Instant.now())
                    .build();

            when(tokenRepository.findByTokenValue("expired-jwt")).thenReturn(Optional.of(expired));

            assertFalse(tokenService.isTokenActiveInDb("expired-jwt"));
        }

        @Test
        @DisplayName("revoked token -> false")
        void revokedToken() {
            Token revoked = Token.builder()
                    .tokenValue("revoked-jwt")
                    .expired(false)
                    .revoked(true)
                    .createdAt(Instant.now())
                    .build();

            when(tokenRepository.findByTokenValue("revoked-jwt")).thenReturn(Optional.of(revoked));

            assertFalse(tokenService.isTokenActiveInDb("revoked-jwt"));
        }

        @Test
        @DisplayName("token not found -> false")
        void tokenNotFound() {
            when(tokenRepository.findByTokenValue("missing")).thenReturn(Optional.empty());

            assertFalse(tokenService.isTokenActiveInDb("missing"));
        }
    }
}