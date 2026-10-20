package dev.web.api.bm_a033;

import lombok.Data;

/**
 * リーグレベル情報反映設定: 保存する 1 件
 * @author shiraishitoshio
 */
@Data
public class LeagueLevelItem {

	private String country;

	private String league;

	/** 1〜99。null は未設定に戻す */
	private Integer leagueLevel;
}
