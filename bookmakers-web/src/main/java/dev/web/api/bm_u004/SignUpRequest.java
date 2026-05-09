package dev.web.api.bm_u004;

import lombok.Data;

@Data
public class SignUpRequest {

	/** 氏名 */
    private String name; // optional

	/** E-mail */
    private String email;

    /** パスワード */
    private String password;

}