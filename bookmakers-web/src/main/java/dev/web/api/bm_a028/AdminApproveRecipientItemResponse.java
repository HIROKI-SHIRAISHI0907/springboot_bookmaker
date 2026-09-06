package dev.web.api.bm_a028;

import lombok.Builder;
import lombok.Data;

/**
 * 指令の宛先1名分の確認状況（管理者が指令の内訳を見るときに使用）。
 */
@Data
@Builder
public class AdminApproveRecipientItemResponse {

    private Long userId;

    private String userName;

    /** 未確認 / 確認済 */
    private String chkFlg;

    /** yyyy-MM-dd HH:mm:ss 形式。未確認の場合はnull */
    private String confirmedTime;
}
