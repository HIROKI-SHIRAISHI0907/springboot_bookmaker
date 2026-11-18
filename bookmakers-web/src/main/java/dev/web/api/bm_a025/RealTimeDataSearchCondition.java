package dev.web.api.bm_a025;

import lombok.Data;

/**
 * RealTimeDataSearchCondition
 * @author shiraishitoshio
 *
 */
@Data
public class RealTimeDataSearchCondition {

	/** 国カテゴリ */
	private String dataCategory;

	/** ホーム */
	private String homeTeamName;

	/** アウェー */
	private String awayTeamName;

	/** チーム（ホーム・アウェーのどちらかに含む。2026-11-18 追加） */
	private String teamName;

}
