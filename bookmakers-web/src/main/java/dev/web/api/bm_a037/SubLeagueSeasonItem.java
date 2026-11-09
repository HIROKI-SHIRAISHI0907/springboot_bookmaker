package dev.web.api.bm_a037;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * サブリーグ保有国リーグ登録設定: 保存する 国・リーグ 1 件
 * @author shiraishitoshio
 */
@Data
public class SubLeagueSeasonItem {

	private String country;

	private String league;

	/** true = サブリーグあり / false = なし（null はエラー） */
	private Boolean hasSubLeague;

	/** サブリーグ名（表示順。ありのときは 1 件以上。なしのときは無視） */
	private List<String> subLeagues = new ArrayList<>();
}
