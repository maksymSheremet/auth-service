package my.code.auth.dto;

public record AuthenticationResponse(
        String accessToken,
        String refreshToken
) {
}