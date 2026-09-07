package dev.web.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.auth0.jwt.interfaces.DecodedJWT;

import dev.web.api.bm_a028.AdminApproveActionResponse;
import dev.web.api.bm_a028.AdminApproveListResponse;
import dev.web.api.bm_a028.AdminApproveService;
import dev.web.api.bm_a028.ApproveActionRequest;
import dev.web.api.bm_a028.CreateInstructionRequest;
import dev.web.api.bm_a028.CreateRequestRequest;
import dev.web.jwt.JwtService;
import dev.web.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 承認フロー（依頼/指令）コントローラー。
 *
 * 依頼: 担当者(ROLE_ADMIN_SUB) が起票し、管理者(ROLE_ADMIN) が承認/差し戻しする。
 * 指令: 管理者(ROLE_ADMIN) が起票し、担当者(ROLE_ADMIN_SUB)全員が確認する。
 *
 * ロール判定はAuthController#loginで発行したJWTの"roles"クレームを見て行う想定。
 * JwtServiceの実装に応じて、クレーム名・取得方法は調整してください。
 *
 * @author shiraishitoshio
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/approve")
@Slf4j
public class AdminApproveController {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AdminApproveService approveService;

    // ------------------------------------------------------------
    // 依頼（担当者 → 管理者）
    // ------------------------------------------------------------

    /** 担当者が依頼を起票する */
    @PostMapping("/requests")
    public ResponseEntity<AdminApproveActionResponse> createRequest(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody CreateRequestRequest req) {
        CurrentUser current = resolveCurrentUser(authorizationHeader);
        if (current == null) {
            return unauthorized();
        }
        if (!current.roles.contains("ROLE_ADMIN_SUB")) {
            return forbidden("担当者のみ依頼を起票できます。");
        }
        AdminApproveActionResponse res = approveService.createRequest(current.userId, req);
        return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
    }

    /**
     * 依頼の一覧を取得する。
     * 管理者: 全件（承認/差し戻しモーダルの元データ）。
     * 担当者: 自分が申請した依頼のみ。
     */
    @GetMapping("/requests")
    public ResponseEntity<AdminApproveListResponse> listRequests(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        log.info(
                "[AdminApproveController#listRequests] START. authorizationPresent={}",
                authorizationHeader != null);

        CurrentUser current = resolveCurrentUser(authorizationHeader);

        if (current == null) {
            log.warn(
                    "[AdminApproveController#listRequests] Unauthorized. resolveCurrentUser returned null");

            return unauthorizedList();
        }

        log.info(
                "[AdminApproveController#listRequests] CurrentUser. userId={}, email={}, roles={}",
                current.userId,
                current.email,
                current.roles);

        AdminApproveListResponse res;

        if (current.roles.contains("ROLE_ADMIN")) {

            log.info(
                    "[AdminApproveController#listRequests] Get requests for ADMIN. userId={}",
                    current.userId);

            res = approveService.getRequestsForAdmin();

        } else if (current.roles.contains("ROLE_ADMIN_SUB")) {

            log.info(
                    "[AdminApproveController#listRequests] Get requests for ADMIN_SUB. userId={}",
                    current.userId);

            res = approveService.getMyRequests(current.userId);

        } else {

            log.warn(
                    "[AdminApproveController#listRequests] Forbidden. userId={}, roles={}",
                    current.userId,
                    current.roles);

            return forbiddenList("管理者または担当者のみ依頼一覧を確認できます。");
        }

        log.info(
                "[AdminApproveController#listRequests] END. responseCode={}, itemCount={}",
                res.getResponseCode(),
                res.getItems() == null ? 0 : res.getItems().size());

        return ResponseEntity
                .status(parseStatus(res.getResponseCode()))
                .body(res);
    }

    /** 管理者が依頼を承認する */
    @PatchMapping("/requests/{approveId}/approve")
    public ResponseEntity<AdminApproveActionResponse> approveRequest(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String approveId) {
        CurrentUser current = resolveCurrentUser(authorizationHeader);
        if (current == null) {
            return unauthorized();
        }
        if (!current.roles.contains("ROLE_ADMIN")) {
            return forbidden("管理者のみ承認できます。");
        }
        AdminApproveActionResponse res = approveService.approveRequest(approveId, current.userId);
        return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
    }

    /** 管理者が依頼を差し戻す */
    @PatchMapping("/requests/{approveId}/reject")
    public ResponseEntity<AdminApproveActionResponse> rejectRequest(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String approveId,
            @RequestBody ApproveActionRequest req) {
        CurrentUser current = resolveCurrentUser(authorizationHeader);
        if (current == null) {
            return unauthorized();
        }
        if (!current.roles.contains("ROLE_ADMIN")) {
            return forbidden("管理者のみ差し戻しできます。");
        }
        AdminApproveActionResponse res = approveService.rejectRequest(approveId, current.userId, req.getComment());
        return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
    }

    /** 担当者が自分の依頼を取り消す */
    @PatchMapping("/requests/{approveId}/cancel")
    public ResponseEntity<AdminApproveActionResponse> cancelRequest(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String approveId,
            @RequestBody ApproveActionRequest req) {
        CurrentUser current = resolveCurrentUser(authorizationHeader);
        if (current == null) {
            return unauthorized();
        }
        if (!current.roles.contains("ROLE_ADMIN_SUB")) {
            return forbidden("担当者のみ依頼を取り消せます。");
        }
        AdminApproveActionResponse res = approveService.cancelRequest(approveId, current.userId, req.getComment());
        return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
    }

    // ------------------------------------------------------------
    // 指令（管理者 → 担当者全員）
    // ------------------------------------------------------------

    /** 管理者が指令を発行する（その時点の担当者全員へ一斉送信） */
    @PostMapping("/instructions")
    public ResponseEntity<AdminApproveActionResponse> createInstruction(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody CreateInstructionRequest req) {
        CurrentUser current = resolveCurrentUser(authorizationHeader);
        if (current == null) {
            return unauthorized();
        }
        if (!current.roles.contains("ROLE_ADMIN")) {
            return forbidden("管理者のみ指令を発行できます。");
        }
        AdminApproveActionResponse res = approveService.createInstruction(current.userId, req);
        return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
    }

    /**
     * 指令の一覧を取得する。
     * 管理者: 自分が発行した指令＋宛先ごとの確認状況の内訳を返す。
     * 担当者: 自分が宛先に含まれる指令＋自分の確認状況を返す。
     */
    @GetMapping("/instructions")
    public ResponseEntity<AdminApproveListResponse> listInstructions(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        CurrentUser current = resolveCurrentUser(authorizationHeader);
        if (current == null) {
            return unauthorizedList();
        }
        AdminApproveListResponse res;
        if (current.roles.contains("ROLE_ADMIN")) {
            res = approveService.getInstructionsForAdmin(current.userId);
        } else if (current.roles.contains("ROLE_ADMIN_SUB")) {
            res = approveService.getInstructionsForRecipient(current.userId);
        } else {
            return forbiddenList("管理者または担当者のみ指令一覧を確認できます。");
        }
        return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
    }

    /** 担当者が指令を確認する */
    @PatchMapping("/instructions/{approveId}/confirm")
    public ResponseEntity<AdminApproveActionResponse> confirmInstruction(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String approveId) {
        CurrentUser current = resolveCurrentUser(authorizationHeader);
        if (current == null) {
            return unauthorized();
        }
        if (!current.roles.contains("ROLE_ADMIN_SUB")) {
            return forbidden("担当者のみ指令を確認できます。");
        }
        AdminApproveActionResponse res = approveService.confirmInstruction(approveId, current.userId);
        return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
    }

    /** 管理者が自分の出した指令を差し戻す */
    @PatchMapping("/instructions/{approveId}/reject")
    public ResponseEntity<AdminApproveActionResponse> rejectInstruction(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String approveId,
            @RequestBody ApproveActionRequest req) {
        CurrentUser current = resolveCurrentUser(authorizationHeader);
        if (current == null) {
            return unauthorized();
        }
        if (!current.roles.contains("ROLE_ADMIN")) {
            return forbidden("管理者のみ指令を差し戻せます。");
        }
        AdminApproveActionResponse res = approveService.rejectInstruction(approveId, current.userId, req.getComment());
        return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
    }

    /** 管理者が自分の出した指令を取り消す */
    @PatchMapping("/instructions/{approveId}/cancel")
    public ResponseEntity<AdminApproveActionResponse> cancelInstruction(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String approveId,
            @RequestBody ApproveActionRequest req) {
        CurrentUser current = resolveCurrentUser(authorizationHeader);
        if (current == null) {
            return unauthorized();
        }
        if (!current.roles.contains("ROLE_ADMIN")) {
            return forbidden("管理者のみ指令を取り消せます。");
        }
        AdminApproveActionResponse res = approveService.cancelInstruction(approveId, current.userId, req.getComment());
        return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
    }

    // ------------------------------------------------------------
    // 共通処理
    // ------------------------------------------------------------

    /**
     * Authorizationヘッダーからログイン中ユーザー（userId・roles）を解決する
     */
    private CurrentUser resolveCurrentUser(String authorizationHeader) {

        // Authorizationヘッダー自体が存在しない
        if (authorizationHeader == null) {
            log.warn("[AdminApproveController#resolveCurrentUser] Authorization header is null");
            return null;
        }

        // Bearer形式ではない
        if (!authorizationHeader.startsWith("Bearer ")) {
            log.warn(
                    "[AdminApproveController#resolveCurrentUser] Authorization header does not start with Bearer. prefix={}",
                    authorizationHeader.length() >= 10
                            ? authorizationHeader.substring(0, 10)
                            : authorizationHeader);
            return null;
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();

        // トークン自体はログに出さない
        log.info(
                "[AdminApproveController#resolveCurrentUser] Authorization received. tokenLength={}",
                token.length());

        if (token.isEmpty()) {
            log.warn("[AdminApproveController#resolveCurrentUser] Bearer token is empty");
            return null;
        }

        try {
            // JWT検証
            DecodedJWT decoded = jwtService.verifyToken(token);

            log.info(
                    "[AdminApproveController#resolveCurrentUser] JWT verification succeeded. subject={}",
                    decoded.getSubject());

            String email = decoded.getSubject();

            // roles取得
            java.util.List<String> roles =
                    decoded.getClaim("roles").asList(String.class);

            log.info(
                    "[AdminApproveController#resolveCurrentUser] JWT claims. subject={}, roles={}",
                    email,
                    roles);

            if (email == null || email.isBlank()) {
                log.warn(
                        "[AdminApproveController#resolveCurrentUser] JWT subject is null or blank");
                return null;
            }

            if (roles == null) {
                log.warn(
                        "[AdminApproveController#resolveCurrentUser] JWT roles claim is null. subject={}",
                        email);
                return null;
            }

            // email → userId
            java.util.Optional<Long> userId =
                    userRepository.findUserIdByEmail(email);

            log.info(
                    "[AdminApproveController#resolveCurrentUser] User lookup. email={}, userIdPresent={}",
                    email,
                    userId.isPresent());

            if (userId.isEmpty()) {
                log.warn(
                        "[AdminApproveController#resolveCurrentUser] User not found by email. email={}",
                        email);
                return null;
            }

            CurrentUser currentUser =
                    new CurrentUser(email, userId.get(), roles);

            log.info(
                    "[AdminApproveController#resolveCurrentUser] CurrentUser resolved. userId={}, roles={}",
                    currentUser.userId,
                    currentUser.roles);

            return currentUser;

        } catch (Exception e) {
            log.warn(
                    "[AdminApproveController#resolveCurrentUser] Token verification/resolution failed. exceptionType={}, message={}",
                    e.getClass().getName(),
                    e.getMessage(),
                    e);

            return null;
        }
    }

    private static class CurrentUser {
        final String email;
        final Long userId;
        final java.util.List<String> roles;

        CurrentUser(String email, Long userId, java.util.List<String> roles) {
            this.email = email;
            this.userId = userId;
            this.roles = roles;
        }
    }

    private ResponseEntity<AdminApproveActionResponse> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                AdminApproveActionResponse.builder().responseCode("401").message("認証情報が不正です。").build());
    }

    private ResponseEntity<AdminApproveActionResponse> forbidden(String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                AdminApproveActionResponse.builder().responseCode("403").message(message).build());
    }

    private ResponseEntity<AdminApproveListResponse> unauthorizedList() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                AdminApproveListResponse.builder().responseCode("401").message("認証情報が不正です。").build());
    }

    private ResponseEntity<AdminApproveListResponse> forbiddenList(String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                AdminApproveListResponse.builder().responseCode("403").message(message).build());
    }

    private static int parseStatus(String code) {
        try {
            int status = Integer.parseInt(code);
            return (status >= 100 && status <= 599) ? status : 500;
        } catch (Exception e) {
            return 500;
        }
    }
}
