package dev.web.api.bm_a031;

import com.opencsv.bean.CsvBindByName;

import lombok.Data;

/**
 * リアルタイムデータ CSV の 1 行（static_data）
 *
 * <p>リアルタイムデータはスクレイピングで登録するため、CSV でできるのは「4:非表示」「5:再表示」だけ。</p>
 * <p>キー: ホームチーム + アウェーチーム + データカテゴリ。該当する行（その試合のデータすべて）をまとめて非表示・再表示する。</p>
 *
 * @author shiraishitoshio
 */
@Data
public class RealtimeCsvRow {

	/** 処理区分（4:非表示 5:再表示 のみ） */
	@CsvBindByName(column = "処理区分")
	private String operation;

	/** ホームチーム（static_data.home_team_name） */
	@CsvBindByName(column = "ホームチーム")
	private String homeTeamName;

	/** アウェーチーム（static_data.away_team_name） */
	@CsvBindByName(column = "アウェーチーム")
	private String awayTeamName;

	/** データカテゴリ（static_data.data_category。例: 日本: J1リーグ - ラウンド 30） */
	@CsvBindByName(column = "データカテゴリ")
	private String dataCategory;
}
