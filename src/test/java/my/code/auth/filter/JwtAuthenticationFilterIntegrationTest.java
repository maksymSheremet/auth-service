package my.code.auth.filter;

import my.code.auth.BaseIntegrationTest;
import my.code.auth.database.entity.User;
import my.code.auth.database.repository.UserRepository;
import my.code.auth.dto.AuthenticationResponse;
import my.code.auth.dto.RegisterRequest;
import my.code.auth.service.JwtService;
import my.code.auth.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("integration")
class JwtAuthenticationFilterIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    private String validAccessToken;
    private User registeredUser;

    private static final String ME_URL = "/api/auth/me";
    private static final String REGISTER_URL = "/api/auth/register";

    @BeforeEach
    void setUp() {
        RegisterRequest request = new RegisterRequest(
                "Filter", "Test", "filter-" + System.nanoTime() + "@test.com", "Pass123!");

        ResponseEntity<AuthenticationResponse> response = restTemplate.postForEntity(
                REGISTER_URL, request, AuthenticationResponse.class);

        assertNotNull(response.getBody());
        validAccessToken = response.getBody().accessToken();
        String email = jwtService.extractUsername(validAccessToken);
        registeredUser = userRepository.findByEmail(email).orElseThrow();
    }

    @Test
    @DisplayName("valid Bearer token → 200, user authenticated")
    void validToken_requestPasses() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validAccessToken);

        var response = restTemplate.exchange(
                ME_URL, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(registeredUser.getEmail(), response.getBody().get("email"));
    }

    @Test
    @DisplayName("no Authorization header on protected endpoint → 401")
    void noToken_protectedEndpoint_returns401() {
        var response = restTemplate.getForEntity(ME_URL, Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("no Authorization header on public endpoint → request passes")
    void noToken_publicEndpoint_passes() {
        RegisterRequest request = new RegisterRequest(
                "Public", "Test",
                "public-" + System.nanoTime() + "@test.com", "Pass123!");

        ResponseEntity<AuthenticationResponse> response = restTemplate.postForEntity(
                REGISTER_URL, request, AuthenticationResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    @DisplayName("malformed token → 401")
    void malformedToken_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("not.a.valid.jwt");

        var response = restTemplate.exchange(ME_URL, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("Authorization header without Bearer prefix → 401 (filter skips)")
    void noBearerPrefix_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic dXNlcjpwYXNz");

        var response = restTemplate.exchange(ME_URL, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("revoked token in DB → 401 (filter rejects)")
    void revokedToken_returns401() {
        tokenService.revokeAllUserTokens(registeredUser.getId());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validAccessToken);

        var response = restTemplate.exchange(ME_URL, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("refresh token used as Bearer → not authenticated (wrong token type)")
    void refreshTokenAsBearer_returns401() {
        RegisterRequest request = new RegisterRequest(
                "Refresh", "Abuse",
                "refresh-abuse-" + System.nanoTime() + "@test.com", "Pass123!");

        ResponseEntity<AuthenticationResponse> regResponse = restTemplate.postForEntity(
                REGISTER_URL, request, AuthenticationResponse.class);

        assertNotNull(regResponse.getBody());
        String refreshToken = regResponse.getBody().refreshToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(refreshToken);

        var response = restTemplate.exchange(ME_URL, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}