package my.code.auth.config.security;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "bcrypt")
@Validated
public class BcryptProperties {
    @Min(value = 10, message = "BCrypt strength must be at least 10")
    @Max(value = 31, message = "BCrypt strength must not exceed 31")
    private int strength;
}
