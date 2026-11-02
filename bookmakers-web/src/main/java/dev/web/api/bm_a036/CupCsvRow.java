package dev.web.api.bm_a036;

import com.opencsv.bean.CsvBindByName;

import lombok.Data;

/**
 * 大会一括アップロード CSV の 1 行（cup_competition_master）
 *
 * <p>キー: 大会URL。同じ URL の大会があれば大会名を更新し（日程を取る状態にする）、無ければ登録する。</p>
 *
 * @author shiraishitoshio
 */
@Data
public class CupCsvRow {

	/** 大会名（例: AFCチャンピオンズリーグエリート） */
	@CsvBindByName(column = "大会名")
	private String competitionName;

	/** 大会URL（Flashscore の大会ページ。例: https://www.flashscore.co.jp/soccer/asia/afc-champions-league/） */
	@CsvBindByName(column = "大会URL")
	private String competitionUrl;
}
