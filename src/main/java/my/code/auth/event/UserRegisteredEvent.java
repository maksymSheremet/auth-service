package my.code.auth.event;

import java.time.Instant;

public record UserRegisteredEvent(
        Long userId,
        String email,
        String fullName,
        String avatarUrl,
        String timezone,
        String language,
        Instant createdAt
) {
}
