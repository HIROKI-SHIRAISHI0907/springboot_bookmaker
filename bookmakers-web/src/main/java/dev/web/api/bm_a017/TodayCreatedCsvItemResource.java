package dev.web.api.bm_a017;

import lombok.Data;

@Data
public class TodayCreatedCsvItemResource {

	/** csv_id */
	private String csvId;

	/** データカテゴリ */
	private String dataCategory;

	/** シーズン */
	private String season;

	/** ホーム */
	private String homeTeamName;

	/** アウェー */
	private String awayTeamName;

	/** 統計反映済みフラグ */
	private String checkFinFlg;

	/** 登録日時 */
	private String registerTime;

}
