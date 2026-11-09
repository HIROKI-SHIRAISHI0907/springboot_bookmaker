package dev.web.api.bm_a037;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * サブリーグ保有国リーグ登録設定: 保存・設定しない の結果
 * @author shiraishitoshio
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubLeagueSeasonSaveResponse {

	/** 更新した 国・リーグ の数 */
	private int updatedLeagues;

	/** 保存後の一覧 */
	private List<SubLeagueSeasonDTO> items = new ArrayList<>();
}
