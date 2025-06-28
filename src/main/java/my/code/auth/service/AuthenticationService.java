package my.code.auth.service;

import my.code.auth.dto.AuthenticationRequest;
import my.code.auth.dto.AuthenticationResponse;
import my.code.auth.dto.ChangePasswordRequest;
import my.code.auth.dto.RefreshTokenRequest;
import my.code.auth.dto.RegisterRequest;

public interface AuthenticationService {

    AuthenticationResponse register(RegisterRequest request);

    AuthenticationResponse authenticate(AuthenticationRequest request);

    AuthenticationResponse refreshToken(RefreshTokenRequest request);

    void logout(RefreshTokenRequest request);

    void changePassword(ChangePasswordRequest request, String email);
}
