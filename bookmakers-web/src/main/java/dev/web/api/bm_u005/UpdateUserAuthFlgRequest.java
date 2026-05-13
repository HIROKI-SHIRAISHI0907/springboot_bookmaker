package dev.web.api.bm_u005;

import lombok.Data;

@Data
public class UpdateUserAuthFlgRequest {

	/** ユーザーID */
    private Long userId;

    /** 権限フラグ */
    private Integer authFlg; // 1=admin, 2=user

}
