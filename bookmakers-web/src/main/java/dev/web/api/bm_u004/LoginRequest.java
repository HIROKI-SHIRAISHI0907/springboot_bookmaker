package dev.web.api.bm_u004;

import lombok.Data;

@Data
public class LoginRequest {

	/** E-mail */
    private String email;

    /** パスワード */
    private String password;

}