// src/main/java/dev/web/jwt/AuthRequest.java
package dev.web.dto;

import lombok.Data;

/**
 * 認証リクエストDTO
 */
@Data
public class AuthRequest {

	/** ユーザー名 */
    private String username;

    /** パスワード*/
    private String password;
}
