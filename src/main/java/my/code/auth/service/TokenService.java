package my.code.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.code.auth.database.entity.Token;
import my.code.auth.database.entity.TokenType;
import my.code.auth.database.entity.User;
import my.code.auth.database.repository.TokenRepository;
import my.code.auth.exception.InvalidTokenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;


@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final TokenRepository tokenRepository;
    private final JwtService jwtService;

    @Transactional
    public void saveAccessToken(User user, String jwt) {
        revokeAllUserTokens(user.getId());
        saveToken(user, jwt, TokenType.BEARER);
    }

    @Transactional
    public void saveRefreshToken(User user, String jwt) {
        saveToken(user, jwt, TokenType.REFRESH);
    }

    @Transactional
    public void revokeAllUserTokens(Long userId) {
        int revoked = tokenRepository.revokeAllTokensByUser(userId);
        if (revoked > 0) {
            log.debug("Revoked {} tokens for userId={}", revoked, userId);
        }
    }

    @Transactional
    public void revokeByRefreshToken(String refreshToken) {
        Token token = tokenRepository.findByToken(refreshToken)
                .filter(t -> t.getTokenType() == TokenType.REFRESH)
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        revokeAllUserTokens(token.getUser().getId());
        log.info("Revoked all tokens via refresh token for userId={}", token.getUser().getId());
    }

    @Transactional(readOnly = true)
    public boolean isTokenActiveInDb(String jwt) {
        return tokenRepository.findByToken(jwt)
                .map(token -> !token.isExpired() && !token.isRevoked())
                .orElse(false);
    }

    private void saveToken(User user, String jwt, TokenType tokenType) {
        Token token = Token.builder()
                .token(jwt)
                .tokenType(tokenType)
                .user(user)
                .createdAt(Instant.now())
                .expiresAt(jwtService.extractExpiration(jwt))
                .build();
        tokenRepository.save(token);
        log.debug("Saved {} token for userId={}", tokenType, user.getId());
    }
}