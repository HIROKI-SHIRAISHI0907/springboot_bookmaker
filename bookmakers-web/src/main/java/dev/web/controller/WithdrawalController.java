package dev.web.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.auth0.jwt.interfaces.DecodedJWT;

import dev.web.api.bm_a029.AccountActionResponse;
import dev.web.api.bm_a029.AccountWithdrawalService;
import dev.web.jwt.JwtService;
import dev.web.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * アカウント自己操作（退会等）のコントローラー。
 *
 * JWTからのユーザー解決ロジック（{@link #resolveCurrentUser}）は
 * {@code AdminApproveController} と同一のパターンです。共通化されたベースクラスや
 * ユーティリティが既にある場合は、そちらに合わせて統合してください。
 *
 * フロント側は {@code POST /api/account/withdraw} を呼び出す想定です
 * （{@code Header.tsx} の "/v1/api/auth/logout" 呼び出しと同じ命名慣習に合わせるなら、
 * ゲートウェイ／コンテキストパス側で "/v1" が前置される構成を想定しています。
 * 実際のベースパスが異なる場合は、フロント側の fetch 先を調整してください）。
 *
 * @author shiraishitoshio
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/account")
@Slf4j
public class WithdrawalController {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AccountWithdrawalService withdrawalService;

    /**
     * 担当者本人が退会する。
     * 管理者はこのAPIを利用できない（要件どおり、担当者のみが退会できる）。
     */
    @PostMapping("/withdraw")
    public ResponseEntity<AccountActionResponse> withdraw(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        CurrentUser current = resolveCurrentUser(authorizationHeader);
        if (current == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    AccountActionResponse.builder().responseCode("401").message("認証情報が不正です。").build());
        }
        if (!current.roles.contains("ROLE_ADMIN_SUB")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    AccountActionResponse.builder().responseCode("403").message("担当者のみ退会できます。").build());
        }
        boolean withdrawn = withdrawalService.withdrawSelf(current.userId, String.valueOf(current.userId));
        if (!withdrawn) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    AccountActionResponse.builder().responseCode("404").message("対象のユーザーが見つかりません。").build());
        }
        return ResponseEntity.ok(
                AccountActionResponse.builder().responseCode("200").message("退会処理が完了しました。").build());
    }

    /** Authorizationヘッダーからログイン中ユーザー（userId・roles）を解決する。AdminApproveControllerと同様の実装。 */
    private CurrentUser resolveCurrentUser(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        try {
            DecodedJWT decoded = jwtService.verifyToken(token);
            String email = decoded.getSubject();
            List<String> roles = decoded.getClaim("roles").asList(String.class);
            Optional<Long> userId = userRepository.findUserIdByEmail(email);
            if (userId.isEmpty() || roles == null) {
                return null;
            }
            return new CurrentUser(email, userId.get(), roles);
        } catch (Exception e) {
            log.warn("トークン検証に失敗しました: {}", e.getMessage());
            return null;
        }
    }

    private static class CurrentUser {
        final String email;
        final Long userId;
        final List<String> roles;

        CurrentUser(String email, Long userId, List<String> roles) {
            this.email = email;
            this.userId = userId;
            this.roles = roles;
        }
    }
}
