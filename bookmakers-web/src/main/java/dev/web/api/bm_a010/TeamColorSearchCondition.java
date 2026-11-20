package dev.web.api.bm_a010;

import lombok.Data;

/**
 * CountryLeagueSeasonSearchCondition
 * @author shiraishitoshio
 *
 */
@Data
public class TeamColorSearchCondition {

	/** ID */
	private String id;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** チーム */
	private String team;

	/** メインカラーコード */
	private String teamColorMainHex;

	/** サブカラーコード */
	private String teamColorSubHex;


	/** 削除フラグ（'0' = 有効 / '1' = 無効。2026-11-20 追加。チームデータ取得情報の del_flg と同じ扱い） */
	private String delFlg;
}
