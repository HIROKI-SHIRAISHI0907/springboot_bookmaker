package dev.web.api.bm_u004;

import lombok.Data;

@Data
public class SignUpRequest {

	/** E-mail */
    private String email;

    /** パスワード */
    private String password;

    /** 氏名 */
    private String name; // optional

    /** operatorId */
    private String operatorId; // optional (register_id/update_id用)

}