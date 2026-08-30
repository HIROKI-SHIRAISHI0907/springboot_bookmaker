package dev.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.auth0.jwt.interfaces.DecodedJWT;

import dev.web.api.bm_u004.AuthResponse;
import dev.web.api.bm_u004.AuthService;
import dev.web.api.bm_u004.ForgotPasswordRequest;
import dev.web.api.bm_u004.LoginRequest;
import dev.web.api.bm_u004.PasswordResetService;
import dev.web.api.bm_u004.ResetPasswordConfirmRequest;
import dev.web.api.bm_u004.SignUpRequest;
import dev.web.jwt.JwtService;
import dev.web.mail.MailSendResponse;
import dev.web.mail.MailSendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {

	/** パスワード再設定用メールのmail_info_master.mail_id */
	private static final String PASSWORD_RESET_MAIL_ID = "bm-mail-001";

	private final JwtService jwtService;
	private final AuthService authService;
	private final MailSendService service;
	private final PasswordResetService passwordResetService;

	/**
	 * 新規登録
	 * @param req
	 * @return
	 */
	@PostMapping("/signup")
	public ResponseEntity<AuthResponse> signUp(@RequestBody SignUpRequest req) {
		AuthResponse res = authService.signUp(req);
		return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
	}

	/**
	 * ログインチェック
	 * @param req
	 * @return
	 */
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

	/**
	 * ログアウト処理
	 * @param authorizationHeader
	 * @return
	 */
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
			@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

		String subject = extractSubjectSafely(authorizationHeader);
		if (subject != null) {
			log.info("logout: user={}", subject);
		} else {
			log.info("logout: subject不明のトークンでログアウト要求を受け付け");
		}
		return ResponseEntity.ok().build();
	}

	/**
	 * パスワード再設定を行うメール情報をDBに登録する。
	 *
	 * PATCH /api/auth/passwd/reset/view
	 */
	@PatchMapping("/passwd/reset/view")
	public ResponseEntity<MailSendResponse> patchView(
			@RequestBody ForgotPasswordRequest req) {

		String email = req.getEmail();
		MailSendResponse res = service.send(PASSWORD_RESET_MAIL_ID, email);

		HttpStatus status = switch (res.getResponseCode()) {
		case "200" -> HttpStatus.OK;
		case "400" -> HttpStatus.BAD_REQUEST;
		case "404" -> HttpStatus.NOT_FOUND;
		case "409" -> HttpStatus.CONFLICT;
		default -> HttpStatus.INTERNAL_SERVER_ERROR;
		};

		return ResponseEntity.status(status).body(res);
	}

	/**
	 * リンクの有効性をチェック
	 * @param key
	 * @return
	 */
	@GetMapping("/passwd/reset/validate")
	public ResponseEntity<AuthResponse> getPasswdResetValidate(@RequestParam("key") String key) {
	    AuthResponse res = passwordResetService.validate(key);
	    return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
	}

	/**
	 * パスワード入力後の再設定
	 * @param req
	 * @return
	 */
	@PatchMapping("/passwd/reset/confirm")
	public ResponseEntity<AuthResponse> patchPasswdResetConfirm(
	        @RequestBody ResetPasswordConfirmRequest req) {
	    AuthResponse res = passwordResetService.confirm(req.getKey(), req.getNewPassword());
	    return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
	}

	/**
	 * authorizationHeaderの検証
	 * @param authorizationHeader
	 * @return
	 */
	private String extractSubjectSafely(String authorizationHeader) {
		if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
			return null;
		}
		String token = authorizationHeader.substring("Bearer ".length()).trim();
		try {
			DecodedJWT decoded = jwtService.verifyToken(token);
			return decoded.getSubject();
		} catch (Exception e) {
			// 期限切れ/改ざん済みのトークンでも、ログアウト自体は成功として扱う
			return null;
		}
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
