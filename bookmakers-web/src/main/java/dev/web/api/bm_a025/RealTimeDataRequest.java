package dev.web.api.bm_a025;

import lombok.Data;

/**
 * team_color_masterAPIリクエスト
 * @author shiraishitoshio
 *
 */
@Data
public class RealTimeDataRequest {

	/** ID */
	private String id;

	/** 国カテゴリ */
	private String dataCategory;

	/** ホーム */
	private String homeTeamName;

	/** アウェー */
	private String awayTeamName;

}
