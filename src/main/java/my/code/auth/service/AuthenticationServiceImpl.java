package my.code.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.code.auth.database.entity.Role;
import my.code.auth.database.entity.User;
import my.code.auth.database.repository.UserRepository;
import my.code.auth.dto.AuthenticationRequest;
import my.code.auth.dto.AuthenticationResponse;
import my.code.auth.dto.ChangePasswordRequest;
import my.code.auth.dto.RefreshTokenRequest;
import my.code.auth.dto.RegisterRequest;
import my.code.auth.event.UserRegisteredEvent;
import my.code.auth.exception.InvalidPasswordException;
import my.code.auth.exception.InvalidTokenException;
import my.code.auth.exception.UserNotFoundException;
import my.code.auth.kafka.OutboxEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final OutboxEventPublisher outboxEventPublisher;

    @Override
    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = User.builder()
                .firstname(request.firstName())
                .lastname(request.lastName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .provider("LOCAL")
                .providerId("LOCAL_" + System.currentTimeMillis())
                .enabled(true)
                .build();
        User saved = userRepository.save(user);

        UserRegisteredEvent event = new UserRegisteredEvent(
                saved.getId(),
                saved.getEmail(),
                saved.getFirstname() + " " + saved.getLastname(),
                null,
                null,
                null,
                Instant.now()
        );
        outboxEventPublisher.publish(String.valueOf(saved.getId()), "USER_REGISTERED", event);

        return generateAndSaveTokens(saved);
    }

    @Override
    @Transactional
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = findUserByEmail(request.email());

        return generateAndSaveTokens(user);
    }

    @Override
    @Transactional
    public AuthenticationResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();
        String userEmail = jwtService.extractUsername(refreshToken);

        if (userEmail == null) {
            throw new InvalidTokenException("Invalid refresh token");
        }

        User user = findUserByEmail(userEmail);

        if (!jwtService.isTokenValid(refreshToken, user) || !jwtService.isRefreshToken(refreshToken)) {
            throw new InvalidTokenException("Refresh token is not valid");
        }

        if (!tokenService.isTokenActiveInDb(refreshToken)) {
            throw new InvalidTokenException("Refresh token has been revoked");
        }

        return generateAndSaveTokens(user);
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {
        tokenService.revokeByRefreshToken(request.refreshToken());
        log.info("User logged out successfully");
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request, String email) {
        User user = findUserByEmail(email);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new InvalidPasswordException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        tokenService.revokeAllUserTokens(user.getId());
        log.info("Password changed and tokens revoked for userId={}", user.getId());
    }

    private AuthenticationResponse generateAndSaveTokens(User user) {
        tokenService.revokeAllUserTokens(user.getId());

        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        tokenService.saveAccessToken(user, accessToken);
        tokenService.saveRefreshToken(user, refreshToken);

        return new AuthenticationResponse(accessToken, refreshToken);
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
    }
}
