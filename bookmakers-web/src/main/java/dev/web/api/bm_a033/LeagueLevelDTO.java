package dev.web.api.bm_a033;

import lombok.Data;

/**
 * リーグレベル情報反映設定: 国・リーグ 1 件（country_league_master を 国・リーグ でまとめたもの）
 * @author shiraishitoshio
 */
@Data
public class LeagueLevelDTO {

	private String country;

	private String league;

	/** リーグレベル（同じ国で 1 = 最上位、大きいほど下位）。未設定・行ごとにばらついているときは null */
	private Integer leagueLevel;

	/** 同じ 国・リーグ の行で値がばらついている（保存すると全行がそろう） */
	private boolean mixed;

	/** 有効なチーム数（del_flg = '0'） */
	private int activeTeams;

	/** 全行数（削除・非表示の行を含む） */
	private int totalRows;

	/** 最終更新（日本時間 "yyyy-MM-dd HH:mm:ss"） */
	private String updateTime;

	/** 最終更新者 */
	private String updateId;
}
