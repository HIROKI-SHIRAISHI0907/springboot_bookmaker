package dev.web.api.bm_u005;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserItemResponse {

	/** ユーザーID */
    private Long userId;

    /** email */
    private String email;

    /** 氏名 */
    private String name;

    /** 権限フラグ */
    private Integer authFlg;

    /** 権限ラベル */
    private String authLabel;

    /** 登録時間 */
    private String registerTime;

    /** 更新時間 */
    private String updateTime;

}
