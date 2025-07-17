package dev.application.analyze.bm_m019_bm_m020;

import lombok.Data;

/**
 * classify_result_data_detailデータEntity
 * @author shiraishitoshio
 *
 */
@Data
public class MatchClassificationResultCountEntity {

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** 分類モード */
	private String classifyMode;

	/** 該当数 */
	private String count;

	/** 備考 */
	private String remarks;

	/** 登録ID */
	private String registerId;

	/** 登録時間 */
	private String registerTime;

	/** 更新ID */
	private String updateId;

	/** 更新時間 */
	private String updateTime;
}
