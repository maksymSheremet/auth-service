package my.code.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import my.code.auth.config.security.JwtProperties;
import my.code.auth.database.entity.User;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.function.Function;


@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String TOKEN_TYPE_ACCESS = "ACCESS";
    private static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private final JwtProperties jwtProperties;
    private SecretKey signingKey;

    @PostConstruct
    void initKey() {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecret()));
    }

    public String generateToken(User user) {
        return buildToken(user, jwtProperties.getExpiration(), TOKEN_TYPE_ACCESS);
    }

    public String generateRefreshToken(User user) {
        return buildToken(user, jwtProperties.getRefreshExpiration(), TOKEN_TYPE_REFRESH);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_USER_ID, Long.class));
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_ROLE, String.class));
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    public Instant extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration).toInstant();
    }

    public boolean isTokenValid(String token, User user) {
        try {
            String username = extractUsername(token);
            return username.equals(user.getEmail()) && !isTokenExpired(token);
        } catch (ExpiredJwtException _) {
            return false;
        }
    }

    public boolean isRefreshToken(String token) {
        return TOKEN_TYPE_REFRESH.equals(extractTokenType(token));
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).isBefore(Instant.now());
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(jwtProperties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claimsResolver.apply(claims);
    }

    private String buildToken(User user, long expirationMs, String tokenType) {
        Instant now = Instant.now();

        return Jwts.builder()
                .id(java.util.UUID.randomUUID().toString())
                .subject(user.getEmail())
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_ROLE, user.getRole().getAuthority())
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .issuer(jwtProperties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey)
                .compact();
    }
}
