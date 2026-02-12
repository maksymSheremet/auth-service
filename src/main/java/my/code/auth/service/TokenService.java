package my.code.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.code.auth.database.entity.Token;
import my.code.auth.database.entity.TokenType;
import my.code.auth.database.entity.User;
import my.code.auth.exception.InvalidTokenException;
import my.code.auth.database.repository.TokenRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final TokenRepository tokenRepository;

    public void saveUserToken(User user, String jwtToken, TokenType tokenType) {
        log.debug("Saving token for user: {}, type: {}", user.getEmail(), tokenType);
        var token = Token.builder()
                .user(user)
                .token(jwtToken)
                .tokenType(tokenType)
                .expired(false)
                .revoked(false)
                .build();
        tokenRepository.save(token);
    }

    public Optional<Token> findByToken(String token) {
        return tokenRepository.findByToken(token);
    }

    public void revokeAllUserTokens(User user) {
        List<Token> validTokens = tokenRepository.findAllValidTokensByUser(user.getId());
        if (validTokens.isEmpty()) {
            log.debug("No valid tokens found for user: {}", user.getEmail());
            return;
        }

        validTokens.forEach(token -> {
            token.setExpired(true);
            token.setRevoked(true);
        });
        tokenRepository.saveAll(validTokens);
        log.info("Revoked {} tokens for user: {}", validTokens.size(), user.getEmail());
    }

    public void revokeRefreshToken(String refreshToken) {
        tokenRepository.findByToken(refreshToken)
                .filter(token -> token.getTokenType() == TokenType.REFRESH)
                .ifPresentOrElse(token -> {
                    User user = token.getUser();
                    revokeAllUserTokens(user);
                    log.info("Revoked refresh token for user: {}", user.getEmail());
                }, () -> {
                    log.error("Refresh token not found: {}", refreshToken);
                    throw new InvalidTokenException("Refresh token not found");
                });
    }
}