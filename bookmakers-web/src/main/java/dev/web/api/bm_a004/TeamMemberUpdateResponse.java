package dev.web.api.bm_a004;

import lombok.Data;

/**
 * team_member_masterAPIレスポンス
 *
 * @author shiraishitoshio
 */
@Data
public class TeamMemberUpdateResponse {

	/** レスポンスコード */
	private String responseCode; // "0"=成功, "9"=失敗 など運用に合わせて

	/** メッセージ */
    private String message;

}
