// src/main/java/dev/web/jwt/AuthController.java
package dev.web.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.auth0.jwt.interfaces.DecodedJWT;

import dev.web.dto.AuthRequest;
import dev.web.dto.AuthResponse;
import dev.web.jwt.JwtService;


/**
 * JWT発行用コントローラー
 * 別ECS(React) から username / password で叩く想定
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "${app.cors.allowed-origins:*}") // Reactのオリジンを環境変数等で設定可能
public class AuthController {

	/** JWTサービス */
	@Autowired
    private JwtService jwtService;

    /**
     * 認証に成功したらJWTを返却
     * POST /api/auth/token
     */
    @PostMapping("/token")
    public ResponseEntity<AuthResponse> issueToken(@RequestBody AuthRequest req) {
        // ユーザー名/パスワードで認証（UserDetailsService 側の実装/インメモリ設定が利用されます）

        // JWT生成
        String token = jwtService.generateToken(req.getUsername(), List.of("ROLE_USER"));

        // 期限等をレスポンスに含めたい場合はデコードして拾う
        DecodedJWT decoded = jwtService.verifyToken(token);
        long iat = decoded.getIssuedAt().toInstant().getEpochSecond();
        long exp = decoded.getExpiresAt().toInstant().getEpochSecond();

        AuthResponse body = new AuthResponse();
        body.setAccessToken(token);
        body.setTokenType("Bearer");
        body.setIssuedAtEpochSecond(iat);
        body.setExpiresAtEpochSecond(exp);
        body.setRoles(List.of("ROLE_USER"));

        return ResponseEntity.ok(body);
    }
}
