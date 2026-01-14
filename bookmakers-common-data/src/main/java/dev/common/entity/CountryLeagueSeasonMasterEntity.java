package dev.common.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 国,リーグシーズン情報マスタ
 * @author shiraishitoshio
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class CountryLeagueSeasonMasterEntity extends MetaEntity {

	/** ID */
	private String id;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** シーズン年 */
	private String seasonYear;

	/** シーズン開始 */
	private String startSeasonDate;

	/** シーズン終了 */
	private String endSeasonDate;

	/** ラウンド数 */
	private String round;

	/** パス */
	private String path;

	/** アイコン */
	private String icon;

	/** 有効フラグ*/
	private String validFlg;

	/** 削除フラグ*/
	private String delFlg;

}
