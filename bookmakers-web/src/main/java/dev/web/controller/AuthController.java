package dev.web.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.auth0.jwt.interfaces.DecodedJWT;

import dev.web.api.bm_u004.AuthResponse;
import dev.web.api.bm_u004.AuthService;
import dev.web.api.bm_u004.LoginRequest;
import dev.web.api.bm_u004.SignUpRequest;
import dev.web.jwt.JwtService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@CrossOrigin(origins = "${app.cors.allowed-origins:*}")
public class AuthController {

    private final JwtService jwtService;
    private final AuthService authService;

    // signup: DB登録だけ（必要なら同時にtoken発行も可）
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signUp(@RequestBody SignUpRequest req) {
        AuthResponse res = authService.signUp(req);
        return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
    }

    // login: 認証OKならJWT発行して返す（ここに統合）
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest req) {
        AuthResponse res = authService.login(req);
        int status = parseStatus(res.getResponseCode());

        // 認証失敗ならそのまま返す
        if (status != 200) {
            return ResponseEntity.status(status).body(res);
        }

        // rolesは必要に応じてDBから取る（ひとまず固定）
        List<String> roles = List.of("ROLE_USER");

        // JWT生成（subjectに何を入れるかが重要）
        // username(email)を入れるのが一般的。userIdを入れたいなら generateToken の仕様を合わせる。
        String subject = req.getEmail(); // LoginRequestにemailがある想定
        String token = jwtService.generateToken(subject, roles);

        DecodedJWT decoded = jwtService.verifyToken(token);
        long iat = decoded.getIssuedAt().toInstant().getEpochSecond();
        long exp = decoded.getExpiresAt().toInstant().getEpochSecond();

        res.setAccessToken(token);
        res.setTokenType("Bearer");
        res.setIssuedAtEpochSecond(iat);
        res.setExpiresAtEpochSecond(exp);
        res.setRoles(roles);

        return ResponseEntity.ok(res);
    }

    // ★ tokenエンドポイントは重複元なので削除推奨
    // もし残すなら「refresh token」専用などに役割変更するのが吉。

    private static int parseStatus(String code) {
        try { return Integer.parseInt(code); } catch (Exception e) { return 500; }
    }
}
