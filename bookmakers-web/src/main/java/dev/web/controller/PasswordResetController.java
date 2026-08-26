package dev.web.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_u004.ForgotPasswordRequest;
import dev.web.mail.MailSendResponse;
import dev.web.mail.MailSendService;
import lombok.RequiredArgsConstructor;

/**
 * パスワード再設定用コントローラー
 * @author shiraishitoshio
 *
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class PasswordResetController {

    private final MailSendService service;

    /**
     * パスワード再設定を行うメール情報をDBに登録する。
     *
     * PATCH /api/passwd/reset/view
     */
    @PatchMapping("/passwd/reset/view")
    public ResponseEntity<MailSendResponse> patchView(
            @RequestBody ForgotPasswordRequest req) {

    	String email = req.getEmail();
    	MailSendResponse res = service.send(email);

        HttpStatus status = switch (res.getResponseCode()) {
            case "200" -> HttpStatus.OK;                    // SUCCESS
            case "400" -> HttpStatus.BAD_REQUEST;           // 必須不足
            case "404" -> HttpStatus.NOT_FOUND;             // NOT_FOUND
            case "409" -> HttpStatus.CONFLICT;              // LINK_ALREADY_USED
            default -> HttpStatus.INTERNAL_SERVER_ERROR;    // ERROR
        };

        return ResponseEntity.status(status).body(res);
    }

}
