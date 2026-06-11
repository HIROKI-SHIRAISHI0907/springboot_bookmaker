package dev.web.api.bm_a002;

import lombok.Data;

/**
 * CountryLeagueSeasonDTO
 * @author shiraishitoshio
 *
 */
@Data
public class CountryLeagueSeasonDTO {

	/** ID */
	private String id;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** シーズン開始 */
	private String startSeasonDate;

	/** シーズン終了 */
	private String endSeasonDate;

	/** シーズン年 */
	private String seasonYear;

	/** ラウンド数 */
	private String round;

	/** パス */
	private String path;

	/** アイコン */
	private String icon;

	/** 削除フラグ*/
	private String delFlg;
}
