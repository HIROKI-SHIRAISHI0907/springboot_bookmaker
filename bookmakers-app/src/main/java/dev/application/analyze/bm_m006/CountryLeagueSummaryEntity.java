package dev.application.analyze.bm_m006;

import lombok.Data;

/**
 * output_通番.xlsxブックから読み込んだデータをマッピングさせるためのDTOクラス
 * @author shiraishitoshio
 *
 */
@Data
public class CountryLeagueSummaryEntity {

	/** ID */
	private String id;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** データ件数 */
	private String dataCount;

	/** CSV件数 */
	private String csvCount;

	/** 登録ID */
	private String registerId;

	/** 登録時間 */
	private String registerTime;

	/** 更新ID */
	private String updateId;

	/** 更新時間 */
	private String updateTime;

}
