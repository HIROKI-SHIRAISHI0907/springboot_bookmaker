package dev.application.analyze.bm_m097;

import lombok.Data;

/**
 * upd_csv_infoテーブルentity
 * @author shiraishitoshio
 *
 */
@Data
public class UpdCsvInfoEntity {

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** テーブルID */
	private String tableId;

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
