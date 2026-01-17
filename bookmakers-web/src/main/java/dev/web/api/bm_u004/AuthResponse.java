package dev.web.api.bm_u004;

import java.util.List;

import lombok.Data;

/**
 * AuthResponse
 * @author shiraishitoshio
 *
 */
@Data
public class AuthResponse {

	/** レスポンスコード */
	private String responseCode; // "0"=成功, "9"=失敗 など運用に合わせて

	/** メッセージ */
    private String message;

    /** ユーザーID */
    private Long userId; // 成功時に返す

    /** アクセストークン（JWT） */
    private String accessToken;

    /** 例: "Bearer" */
    private String tokenType;

    /** 発行時刻（秒, epoch） */
    private long issuedAtEpochSecond;

    /** 失効時刻（秒, epoch） */
    private long expiresAtEpochSecond;

    /** 付与ロール（例: ["ROLE_USER", "ROLE_ADMIN"]） */
    private List<String> roles;

    public static AuthResponse of(String code, String msg) {
        AuthResponse r = new AuthResponse();
        r.setResponseCode(code);
        r.setMessage(msg);
        return r;
    }

    public static AuthResponse ok(String msg, Long userId) {
        AuthResponse r = of("200", msg);
        r.setUserId(userId);
        return r;
    }

}