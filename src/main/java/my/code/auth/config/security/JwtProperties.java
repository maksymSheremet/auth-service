package my.code.auth.config.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "jwt")
@Validated
public class JwtProperties {
    @NotBlank(message = "JWT secret must not be blank")
    private String secret;

    @Min(value = 60000, message = "JWT expiration must be at least 60 seconds")
    private long expiration;

    @Min(value = 60000, message = "Refresh token expiration must be at least 60 seconds")
    private long refreshExpiration;
}
