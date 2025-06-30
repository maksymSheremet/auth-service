package my.code.config;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import my.code.auth.config.security.JwtProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Base64;

@TestConfiguration
public class TestingBeans {

    @Bean
    public JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        byte[] keyBytes = Keys.secretKeyFor(SignatureAlgorithm.HS256).getEncoded();
        String secret = Base64.getEncoder().encodeToString(keyBytes);
        properties.setSecret(secret);
        properties.setExpiration(3600000L);
        properties.setRefreshExpiration(604800000L);
        return properties;
    }
}
