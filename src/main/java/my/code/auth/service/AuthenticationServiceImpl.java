package my.code.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.code.auth.dto.AuthenticationRequest;
import my.code.auth.dto.AuthenticationResponse;
import my.code.auth.dto.ChangePasswordRequest;
import my.code.auth.dto.RefreshTokenRequest;
import my.code.auth.dto.RegisterRequest;
import my.code.auth.event.UserRegisteredEvent;
import my.code.auth.database.entity.Role;
import my.code.auth.database.entity.User;
import my.code.auth.exception.InvalidPasswordException;
import my.code.auth.exception.InvalidTokenException;
import my.code.auth.exception.UserNotFoundException;
import my.code.auth.database.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserRegistrationProducer producer;


    @Override
    public AuthenticationResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }
        var user = User.builder()
                .firstname(request.getFirstName())
                .lastname(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .provider("LOCAL")
                .providerId("LOCAL_" + System.currentTimeMillis())
                .enabled(true)
                .build();
        var saved = userRepository.save(user);

        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(saved.getId())
                .email(saved.getEmail())
                .fullName(saved.getFirstname() + " " + saved.getLastname())
//                .avatarUrl(request.getAvatarUrl())
//                .timezone(request.getTimezone())
//                .language(request.getLanguage())
//                .createdAt(Instant.now())
                .build();

        producer.send(event);

        var accessToken = jwtService.generateToken(user);
        var refreshToken = jwtService.generateRefreshToken(user);

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
            var user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new UserNotFoundException("User not found with email: " + request.getEmail()));
            var accessToken = jwtService.generateToken(user);
            var refreshToken = jwtService.generateRefreshToken(user);
            tokenService.revokeAllUserTokens(user);

            return AuthenticationResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .build();
        } catch (BadCredentialsException e) {
            log.error("Authentication failed for email: {}", request.getEmail());
            throw new BadCredentialsException("Invalid credentials");
        }
    }

    @Override
    public AuthenticationResponse refreshToken(RefreshTokenRequest request) {
        final String refreshToken = request.getRefreshToken();
        final String userEmail = jwtService.extractUsername(refreshToken);

        if (userEmail == null) {
            log.error("Invalid refresh token: null username extracted");
            throw new InvalidTokenException("Invalid refresh token");
        }

        var user = findUserByEmail(userEmail);

        if (!jwtService.isTokenValid(refreshToken, user)) {
            log.error("Refresh token not valid for user: {}", userEmail);
            throw new InvalidTokenException("Refresh token not valid");
        }

        var newAccessToken = jwtService.generateToken(user);
        var newRefreshToken = jwtService.generateRefreshToken(user);
        tokenService.revokeAllUserTokens(user);

        return AuthenticationResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    @Override
    public void logout(RefreshTokenRequest request) {
        tokenService.revokeRefreshToken(request.getRefreshToken());
    }

    @Override
    public void changePassword(ChangePasswordRequest request, String email) {
        log.debug("Attempting to change password for user: {}", email);
        User user = findUserByEmail(email);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            log.error("Invalid current password for user: {}", email);
            throw new InvalidPasswordException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed successfully for user: {}", email);
    }

    private User findUserByEmail(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> {
                    log.error("User not found with email: {}", userEmail);
                    return new UserNotFoundException("User not found with email: " + userEmail);
                });
    }

}
