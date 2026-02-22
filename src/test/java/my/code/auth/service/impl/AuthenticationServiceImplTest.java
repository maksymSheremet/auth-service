package my.code.auth.service.impl;

import my.code.auth.database.entity.Role;
import my.code.auth.database.entity.User;
import my.code.auth.database.repository.UserRepository;
import my.code.auth.dto.AuthenticationRequest;
import my.code.auth.dto.AuthenticationResponse;
import my.code.auth.dto.ChangePasswordRequest;
import my.code.auth.dto.RefreshTokenRequest;
import my.code.auth.dto.RegisterRequest;
import my.code.auth.exception.InvalidPasswordException;
import my.code.auth.exception.InvalidTokenException;
import my.code.auth.exception.UserNotFoundException;
import my.code.auth.kafka.OutboxEventPublisher;
import my.code.auth.service.JwtService;
import my.code.auth.service.TokenService;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthenticationServiceImpl}
 * All dependencies mocked - no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    TokenService tokenService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @InjectMocks
    private AuthenticationServiceImpl authService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private User savedUser;

    @BeforeEach
    void setUp() {
        savedUser = User.builder()
                .id(1L)
                .firstname("John")
                .lastname("Doe")
                .email("john@example.com")
                .password("encoded-password")
                .role(Role.USER)
                .provider("LOCAL")
                .providerId("LOCAL_123")
                .enabled(true)
                .build();
    }

    @Nested
    @DisplayName("register")
    class Register {

        private final RegisterRequest request = new RegisterRequest("John", "Doe", "john@example.com", "Pass123!");

        @Test
        @DisplayName("successful registration → returns tokens")
        void successfulRegistration() {
            when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Pass123!")).thenReturn("encoded-password");
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(jwtService.generateToken(savedUser)).thenReturn("access-jwt");
            when(jwtService.generateRefreshToken(savedUser)).thenReturn("refresh-jwt");

            AuthenticationResponse response = authService.register(request);

            assertEquals("access-jwt", response.accessToken());
            assertEquals("refresh-jwt", response.refreshToken());
        }

        @Test
        @DisplayName("saves user with correct fields")
        void savesUserWithCorrectFields() {
            when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Pass123!")).thenReturn("encoded-password");
            when(userRepository.save(userCaptor.capture())).thenReturn(savedUser);
            when(jwtService.generateToken(any())).thenReturn("jwt");
            when(jwtService.generateRefreshToken(any())).thenReturn("refresh");

            authService.register(request);

            User captured = userCaptor.getValue();
            assertEquals("John", captured.getFirstname());
            assertEquals("Doe", captured.getLastname());
            assertEquals("john@example.com", captured.getEmail());
            assertEquals("encoded-password", captured.getPassword());
            assertEquals(Role.USER, captured.getRole());
            assertEquals("LOCAL", captured.getProvider());
            assertTrue(captured.isEnabled());
        }

        @Test
        @DisplayName("publishes USER_REGISTERED outbox event")
        void publishesOutboxEvent() {
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("enc");
            when(userRepository.save(any())).thenReturn(savedUser);
            when(jwtService.generateToken(any())).thenReturn("jwt");
            when(jwtService.generateRefreshToken(any())).thenReturn("refresh");

            authService.register(request);

            verify(outboxEventPublisher).publish(eq("1"), eq("USER_REGISTERED"), any());
        }

        @Test
        @DisplayName("revokes old tokens before saving new ones")
        void revokesOldTokens() {
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("enc");
            when(userRepository.save(any())).thenReturn(savedUser);
            when(jwtService.generateToken(any())).thenReturn("jwt");
            when(jwtService.generateRefreshToken(any())).thenReturn("refresh");

            authService.register(request);

            verify(tokenService).revokeAllUserTokens(1L);
            verify(tokenService).saveAccessToken(savedUser, "jwt");
            verify(tokenService).saveRefreshToken(savedUser, "refresh");
        }

        @Test
        @DisplayName("duplicate email → throws IllegalArgumentException")
        void duplicateEmail() {
            when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

            assertThrows(IllegalArgumentException.class, () -> authService.register(request));
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("authenticate")
    class Authenticate {

        private final AuthenticationRequest request = new AuthenticationRequest("john@example.com", "Pass123!");

        @Test
        @DisplayName("valid credentials → returns tokens")
        void validCredentials() {
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(savedUser));
            when(jwtService.generateToken(savedUser)).thenReturn("access-jwt");
            when(jwtService.generateRefreshToken(savedUser)).thenReturn("refresh-jwt");

            AuthenticationResponse response = authService.authenticate(request);

            assertEquals("access-jwt", response.accessToken());
            assertEquals("refresh-jwt", response.refreshToken());
        }

        @Test
        @DisplayName("delegates to AuthenticationManager")
        void delegatesToAuthManager() {
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(savedUser));
            when(jwtService.generateToken(any())).thenReturn("jwt");
            when(jwtService.generateRefreshToken(any())).thenReturn("refresh");

            authService.authenticate(request);

            verify(authenticationManager).authenticate(
                    argThat(auth -> auth instanceof UsernamePasswordAuthenticationToken
                                    && auth.getPrincipal().equals("john@example.com")
                                    && auth.getCredentials().equals("Pass123!"))
            );
        }

        @Test
        @DisplayName("bad credentials → AuthenticationManager throws")
        void badCredentials() {
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThrows(BadCredentialsException.class, () -> authService.authenticate(request));
            verify(tokenService, never()).saveAccessToken(any(), any());
        }

        @Test
        @DisplayName("user not found → throws UserNotFoundException")
        void userNotFound() {

            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class, () -> authService.authenticate(request));
        }
    }


    @Nested
    @DisplayName("refreshToken")
    class RefreshToken {

        @Test
        @DisplayName("valid refresh token → returns new token pair")
        void validRefreshToken() {
            RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-jwt");

            when(jwtService.extractUsername("valid-refresh-jwt")).thenReturn("john@example.com");
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(savedUser));
            when(jwtService.isTokenValid("valid-refresh-jwt", savedUser)).thenReturn(true);
            when(jwtService.isRefreshToken("valid-refresh-jwt")).thenReturn(true);
            when(tokenService.isTokenActiveInDb("valid-refresh-jwt")).thenReturn(true);
            when(jwtService.generateToken(savedUser)).thenReturn("new-access");
            when(jwtService.generateRefreshToken(savedUser)).thenReturn("new-refresh");

            AuthenticationResponse response = authService.refreshToken(request);

            assertEquals("new-access", response.accessToken());
            assertEquals("new-refresh", response.refreshToken());
        }

        @Test
        @DisplayName("null username in token → throws InvalidTokenException")
        void nullUsername() {
            when(jwtService.extractUsername("bad-jwt")).thenReturn(null);

            var request = new RefreshTokenRequest("bad-jwt");
            assertThrows(InvalidTokenException.class, () -> authService.refreshToken(request));
        }

        @Test
        @DisplayName("expired/invalid token → throws InvalidTokenException")
        void invalidToken() {
            when(jwtService.extractUsername("expired-jwt")).thenReturn("john@example.com");
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(savedUser));
            when(jwtService.isTokenValid("expired-jwt", savedUser)).thenReturn(false);

            var request = new RefreshTokenRequest("expired-jwt");
            assertThrows(InvalidTokenException.class, () -> authService.refreshToken(request));
        }

        @Test
        @DisplayName("ACCESS token instead of REFRESH → throws InvalidTokenException")
        void accessTokenInsteadOfRefresh() {
            when(jwtService.extractUsername("access-jwt")).thenReturn("john@example.com");
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(savedUser));
            when(jwtService.isTokenValid("access-jwt", savedUser)).thenReturn(true);
            when(jwtService.isRefreshToken("access-jwt")).thenReturn(false);

            var request = new RefreshTokenRequest("access-jwt");
            assertThrows(InvalidTokenException.class, () -> authService.refreshToken(request));
        }

        @Test
        @DisplayName("revoked token in DB → throws InvalidTokenException")
        void revokedInDb() {
            when(jwtService.extractUsername("revoked-jwt")).thenReturn("john@example.com");
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(savedUser));
            when(jwtService.isTokenValid("revoked-jwt", savedUser)).thenReturn(true);
            when(jwtService.isRefreshToken("revoked-jwt")).thenReturn(true);
            when(tokenService.isTokenActiveInDb("revoked-jwt")).thenReturn(false);

            var request = new RefreshTokenRequest("revoked-jwt");
            assertThrows(InvalidTokenException.class, () -> authService.refreshToken(request));
        }
    }

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("delegates to tokenService.revokeByRefreshToken")
        void delegatesToTokenService() {
            RefreshTokenRequest request = new RefreshTokenRequest("refresh-jwt");

            authService.logout(request);

            verify(tokenService).revokeByRefreshToken("refresh-jwt");
        }
    }

    @Nested
    @DisplayName("changePassword")
    class ChangePassword {

        @Test
        @DisplayName("correct current password → updates and revokes tokens")
        void successfulChange() {
            ChangePasswordRequest request = new ChangePasswordRequest("OldPass1!", "NewPass1!");

            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(savedUser));
            when(passwordEncoder.matches("OldPass1!", "encoded-password")).thenReturn(true);
            when(passwordEncoder.encode("NewPass1!")).thenReturn("new-encoded");

            authService.changePassword(request, "john@example.com");

            verify(userRepository).save(userCaptor.capture());
            assertEquals("new-encoded", userCaptor.getValue().getPassword());
            verify(tokenService).revokeAllUserTokens(1L);
        }

        @Test
        @DisplayName("wrong current password → throws InvalidPasswordException")
        void wrongCurrentPassword() {
            ChangePasswordRequest request = new ChangePasswordRequest("WrongPass!", "NewPass1!");

            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(savedUser));
            when(passwordEncoder.matches("WrongPass!", "encoded-password")).thenReturn(false);

            assertThrows(InvalidPasswordException.class,
                    () -> authService.changePassword(request, "john@example.com"));

            verify(userRepository, never()).save(any());
            verify(tokenService, never()).revokeAllUserTokens(anyLong());
        }

        @Test
        @DisplayName("user not found → throws UserNotFoundException")
        void userNotFound() {
            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            ChangePasswordRequest request = new ChangePasswordRequest("a", "b");

            assertThrows(UserNotFoundException.class,
                    () -> authService.changePassword(request, "unknown@example.com"));
        }
    }
}