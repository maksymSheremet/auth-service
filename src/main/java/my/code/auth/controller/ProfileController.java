package my.code.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import my.code.auth.dto.UserProfileResponse;
import my.code.auth.util.AuthUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
public class ProfileController {

    private final AuthUtils authUtils;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfileResponse> getProfile(HttpServletRequest request) {
        return ResponseEntity.ok(
                new UserProfileResponse(
                        authUtils.getCurrentUserId(request),
                        authUtils.getCurrentUserEmail(request),
                        authUtils.getCurrentUserRole(request)
                )
        );
    }
}