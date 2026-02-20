package my.code.auth.controller;

import my.code.auth.BaseIntegrationTest;
import my.code.auth.dto.AuthenticationRequest;
import my.code.auth.dto.AuthenticationResponse;
import my.code.auth.dto.ChangePasswordRequest;
import my.code.auth.dto.RefreshTokenRequest;
import my.code.auth.dto.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private static final String BASE_URL = "/api/auth";

    @Test
    @Order(1)
    @DisplayName("POST /register — valid request → 201 + tokens")
    void register_validRequest_returns201WithTokens() {
        RegisterRequest request = new RegisterRequest("John", "Doe", "newuser@test.com", "Pass123!");

        ResponseEntity<AuthenticationResponse> response = restTemplate.postForEntity(
                BASE_URL + "/register", request, AuthenticationResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().accessToken());
        assertNotNull(response.getBody().refreshToken());
    }

    @Test
    @Order(2)
    @DisplayName("POST /register — duplicate email → 400")
    void register_duplicateEmail_returns400() {
        RegisterRequest request = new RegisterRequest(
                "John", "Doe", "newuser@test.com", "Pass123!");

        var response = restTemplate.postForEntity(BASE_URL + "/register", request, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Order(3)
    @ParameterizedTest(name = "POST /register — {2} → 400")
    @CsvSource({
            "not-an-email,   Pass123!,  invalid email format",
            "blank@test.com, '',        blank password",
            "'',             Pass123!,  blank email",
            "a@b.com,        short,     password too short"
    })
    void register_validation_returns400(String email, String password) {
        RegisterRequest request = new RegisterRequest("John", "Doe", email, password);

        var response = restTemplate.postForEntity(BASE_URL + "/register", request, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @Order(10)
    @DisplayName("POST /authenticate — valid credentials → 200 + tokens")
    void authenticate_validCredentials_returns200() {
        restTemplate.postForEntity(BASE_URL + "/register",
                new RegisterRequest("Auth", "User", "authuser@test.com", "Pass123!"),
                AuthenticationResponse.class);

        AuthenticationRequest request = new AuthenticationRequest("authuser@test.com", "Pass123!");

        ResponseEntity<AuthenticationResponse> response = restTemplate.postForEntity(
                BASE_URL + "/authenticate", request, AuthenticationResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().accessToken());
        assertNotNull(response.getBody().refreshToken());
    }

    @Test
    @Order(11)
    @DisplayName("POST /authenticate — wrong password → 401")
    void authenticate_wrongPassword_returns401() {
        AuthenticationRequest request = new AuthenticationRequest("authuser@test.com", "WrongPass!");

        var response = restTemplate.postForEntity(BASE_URL + "/authenticate", request, Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @Order(12)
    @DisplayName("POST /authenticate — non-existent user → 401")
    void authenticate_nonExistentUser_returns401() {
        AuthenticationRequest request = new AuthenticationRequest("ghost@test.com", "Pass123!");

        var response = restTemplate.postForEntity(BASE_URL + "/authenticate", request, Map.class);

        assertTrue(response.getStatusCode().is4xxClientError());
    }

    @Test
    @Order(20)
    @DisplayName("POST /refresh — valid refresh token → 200 + new tokens")
    void refresh_validToken_returns200() {
        ResponseEntity<AuthenticationResponse> regResponse = restTemplate.postForEntity(
                BASE_URL + "/register",
                new RegisterRequest("Ref", "User", "refresh@test.com", "Pass123!"),
                AuthenticationResponse.class);

        assertNotNull(regResponse.getBody());
        String currentRefresh = regResponse.getBody().refreshToken();

        RefreshTokenRequest request = new RefreshTokenRequest(currentRefresh);
        ResponseEntity<AuthenticationResponse> response = restTemplate.postForEntity(
                BASE_URL + "/refresh", request, AuthenticationResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().accessToken());
        assertNotNull(response.getBody().refreshToken());
    }

    @Test
    @Order(21)
    @DisplayName("POST /refresh — invalid token → 401")
    void refresh_invalidToken_returns401() {
        RefreshTokenRequest request = new RefreshTokenRequest("invalid.jwt.token");

        var response = restTemplate.postForEntity(BASE_URL + "/refresh", request, Map.class);

        assertTrue(response.getStatusCode().is4xxClientError());
    }

    @Test
    @Order(30)
    @DisplayName("POST /logout — valid refresh token → 204")
    void logout_validToken_returns204() {
        ResponseEntity<AuthenticationResponse> regResponse = restTemplate.postForEntity(
                BASE_URL + "/register",
                new RegisterRequest("Log", "Out", "logout@test.com", "Pass123!"),
                AuthenticationResponse.class);

        assertNotNull(regResponse.getBody());
        String currentRefresh = regResponse.getBody().refreshToken();

        HttpEntity<RefreshTokenRequest> entity = new HttpEntity<>(new RefreshTokenRequest(currentRefresh));
        ResponseEntity<Void> response = restTemplate.postForEntity(BASE_URL + "/logout", entity, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    @Order(40)
    @DisplayName("POST /change-password — authenticated + correct password → 204")
    void changePassword_authenticated_returns204() {
        ResponseEntity<AuthenticationResponse> regResponse = restTemplate.postForEntity(
                BASE_URL + "/register",
                new RegisterRequest("Pwd", "Change", "pwdchange@test.com", "Pass123!"),
                AuthenticationResponse.class);

        assertNotNull(regResponse.getBody());
        String token = regResponse.getBody().accessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ChangePasswordRequest request = new ChangePasswordRequest("Pass123!", "NewPass456!");
        HttpEntity<ChangePasswordRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Void> response = restTemplate.exchange(
                BASE_URL + "/change-password", HttpMethod.POST, entity, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    @Order(41)
    @DisplayName("POST /change-password — no auth header → 401")
    void changePassword_noAuth_returns401() {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPass123!", "NewPass456!");

        var response = restTemplate.postForEntity(BASE_URL + "/change-password", request, Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @Order(50)
    @DisplayName("GET /me — authenticated → 200 + user info")
    void me_authenticated_returns200() {
        ResponseEntity<AuthenticationResponse> regResponse = restTemplate.postForEntity(
                BASE_URL + "/register",
                new RegisterRequest("Me", "User", "meuser@test.com", "Pass123!"),
                AuthenticationResponse.class);

        assertNotNull(regResponse.getBody());
        String token = regResponse.getBody().accessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        var response = restTemplate.exchange(BASE_URL + "/me", HttpMethod.GET, entity, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("meuser@test.com", response.getBody().get("email"));
        assertEquals("USER", response.getBody().get("role"));
    }

    @Test
    @Order(51)
    @DisplayName("GET /me — no auth → 401")
    void me_noAuth_returns401() {
        var response = restTemplate.getForEntity(BASE_URL + "/me", Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}