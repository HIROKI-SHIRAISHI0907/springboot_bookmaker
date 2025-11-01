// src/main/java/dev/web/jwt/AuthResponse.java
package dev.web.dto;

import java.util.List;

import lombok.Data;

/**
 * 認証レスポンスDTO
 */
@Data
public class AuthResponse {

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

}
