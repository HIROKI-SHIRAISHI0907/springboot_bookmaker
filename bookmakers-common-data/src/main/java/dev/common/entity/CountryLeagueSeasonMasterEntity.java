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

	/** file */
	private String file;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** シーズン開始 */
	private String startSeasonDate;

	/** シーズン終了 */
	private String endSeasonDate;

	/** 更新済みスタンプ */
	private String updStamp;

}
