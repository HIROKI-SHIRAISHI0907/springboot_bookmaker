package dev.web.api.bm_a039;

import lombok.Data;

/**
 * チーム昇格・降格確認設定の 1 行（team_league_move_history）
 * @author shiraishitoshio
 */
@Data
public class TeamLeagueMoveDTO {

	private Long id;

	private String country;

	private String team;

	private String link;

	/** country_league_master.id */
	private Integer teamId;

	private String fromLeague;

	private String toLeague;

	/** 検知したときの移動元リーグのレベル（1 = 最上位。未設定は null） */
	private Integer fromLevel;

	private Integer toLevel;

	/** 検知したときの候補 UP / DOWN / OTHER */
	private String suggestType;

	/** 確定した種類 UP / DOWN / OTHER / RENAME（未確定の間は候補と同じ） */
	private String moveType;

	/** UPDATE（行の付け替え・生き返り）/ INSERT（新しい行） */
	private String source;

	/** '0' 未確定 / '1' 確定 / '9' 取り消し */
	private String confirmFlg;

	/** 日本時間 "yyyy-MM-dd HH:mm:ss" */
	private String detectedTime;

	private String confirmTime;

	private String updateId;

	/** 移動先リーグがスクレイピング対象か（対象外の未確定は保留。確認・確定の対象にしない） */
	private boolean scrapeTarget;
}
