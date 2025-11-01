package dev.web.jwt;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.auth0.jwt.interfaces.DecodedJWT;

/**
 * JwtService の単体テスト
 */
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();

        // @Value で注入されるフィールドをテスト用に手動セット
        ReflectionTestUtils.setField(jwtService, "secret", "test-secret-key");
        ReflectionTestUtils.setField(jwtService, "issuer", "bookmakers-web");
        ReflectionTestUtils.setField(jwtService, "expiresInSeconds", 3600L);
    }

    @Test
    void testGenerateAndVerifyToken() {
        // given
        String username = "testuser";
        List<String> roles = List.of("ROLE_USER", "ROLE_ADMIN");

        // when
        String token = jwtService.generateToken(username, roles);

        // then: トークンがnullでないこと
        assertNotNull(token);
        assertTrue(token.startsWith("ey"), "トークンの形式がJWTのようである");

        // when: 検証
        DecodedJWT decoded = jwtService.verifyToken(token);

        // then: 正しい値が取得できること
        assertEquals(username, decoded.getSubject());
        assertEquals("bookmakers-web", decoded.getIssuer());
        assertEquals(roles, decoded.getClaim("roles").asList(String.class));

        // 有効期限・発行日時も存在する
        assertNotNull(decoded.getIssuedAt());
        assertNotNull(decoded.getExpiresAt());
    }

    @Test
    void testVerifyTokenWithInvalidSignature() {
        // given: 正しいトークンを作って別のキーで検証
        String token = jwtService.generateToken("user", List.of("ROLE_USER"));

        JwtService otherService = new JwtService();
        ReflectionTestUtils.setField(otherService, "secret", "wrong-secret");
        ReflectionTestUtils.setField(otherService, "issuer", "bookmakers-web");

        // when & then
        assertThrows(Exception.class, () -> otherService.verifyToken(token),
                "署名不正なトークンは検証に失敗するべき");
    }
}
