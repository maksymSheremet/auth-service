//package my.code.auth.service;
//
//import io.jsonwebtoken.SignatureAlgorithm;
//import io.jsonwebtoken.security.Keys;
//import my.code.auth.config.security.JwtProperties;
//import my.code.auth.entity.Role;
//import my.code.auth.entity.TokenType;
//import my.code.auth.entity.User;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import java.util.Base64;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertNotNull;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.doNothing;
//import static org.mockito.Mockito.when;
//
//@ExtendWith(MockitoExtension.class)
//class JwtServiceTest {
//
//    @Mock
//    private JwtProperties jwtProperties;
//
//    @Mock
//    private TokenService tokenService;
//
//    @InjectMocks
//    private JwtService jwtService;
//
//    private User user;
//
//    @BeforeEach
//    void setUp() {
//        user = User.builder()
//                .id(1L)
//                .email("test@example.com")
//                .role(Role.USER)
//                .enabled(true)
//                .build();
//
//        // Налаштування JwtProperties для тестів
//        when(jwtProperties.getSecret()).thenReturn(Base64.getEncoder().encodeToString(
//                Keys.secretKeyFor(SignatureAlgorithm.HS256).getEncoded()));
//        when(jwtProperties.getExpiration()).thenReturn(3600000L);
//        when(jwtProperties.getRefreshExpiration()).thenReturn(604800000L);
//
//        // Ініціалізація JwtService
//        jwtService.init();
//    }
//
//    @Test
//    void testGenerateToken() {
//        doNothing().when(tokenService).saveUserToken(any(User.class), any(String.class), any(TokenType.class));
//
//        String token = jwtService.generateToken(user);
//        assertNotNull(token);
//        assertEquals("test@example.com", jwtService.extractUsername(token));
//        assertEquals("ROLE_USER", jwtService.extractUserRole(token));
//        assertEquals(1L, jwtService.extractUserId(token));
//        assertTrue(jwtService.isTokenValid(token, user));
//    }
//
//    @Test
//    void testGenerateRefreshToken() {
//        doNothing().when(tokenService).saveUserToken(any(User.class), any(String.class), any(TokenType.class));
//
//        String refreshToken = jwtService.generateRefreshToken(user);
//        assertNotNull(refreshToken);
//        assertEquals("test@example.com", jwtService.extractUsername(refreshToken));
//        assertTrue(jwtService.isTokenValid(refreshToken, user));
//    }
//}