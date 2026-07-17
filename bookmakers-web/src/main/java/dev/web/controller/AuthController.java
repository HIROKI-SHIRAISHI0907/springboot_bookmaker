package dev.web.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.auth0.jwt.interfaces.DecodedJWT;

import dev.web.api.bm_u004.AuthResponse;
import dev.web.api.bm_u004.AuthService;
import dev.web.api.bm_u004.ForgotPasswordRequest;
import dev.web.api.bm_u004.LoginRequest;
import dev.web.api.bm_u004.SignUpRequest;
import dev.web.jwt.JwtService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

	private final JwtService jwtService;
	private final AuthService authService;

	@PostMapping("/signup")
	public ResponseEntity<AuthResponse> signUp(@RequestBody SignUpRequest req) {
		AuthResponse res = authService.signUp(req);
		return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
	}

	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest req) {
		AuthResponse res = authService.login(req);
		int status = parseStatus(res.getResponseCode());

		if (status != 200) {
			return ResponseEntity.status(status).body(res);
		}

		int authFlg = res.getAuthFlg() == null ? 2 : res.getAuthFlg();

		List<String> roles;
		switch (authFlg) {
		case 1:
			roles = List.of("ROLE_ADMIN", "ROLE_USER");
			break;
		case 2:
		default:
			roles = List.of("ROLE_USER");
			break;
		}

		String subject = normalizeEmail(req.getEmail());
		if (subject.isEmpty()) {
			res.setResponseCode("400");
			res.setMessage("メールアドレスが不正です。");
			return ResponseEntity.badRequest().body(res);
		}

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

	@PostMapping("/forgot-password")
	public ResponseEntity<AuthResponse> forgotPassword(@RequestBody ForgotPasswordRequest req) {
		AuthResponse res = authService.forgotPassword(req);
		return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
	}

	private static int parseStatus(String code) {
		try {
			int status = Integer.parseInt(code);
			return (status >= 100 && status <= 599) ? status : 500;
		} catch (Exception e) {
			return 500;
		}
	}

	private static String normalizeEmail(String email) {
		if (email == null) {
			return "";
		}
		return email.trim().toLowerCase();
	}
}
