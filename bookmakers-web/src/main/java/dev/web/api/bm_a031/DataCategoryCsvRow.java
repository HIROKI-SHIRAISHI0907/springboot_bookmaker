package dev.web.api.bm_a031;

import com.opencsv.bean.CsvBindByName;

import lombok.Data;

/**
 * データカテゴリ統一 CSV の 1 行（static_data.data_category・future_master.game_team_category の書き換え）
 *
 * <p>キー: ホームチーム + アウェーチーム + 現在のデータカテゴリ（データカテゴリ統一管理画面の 1 行と同じ単位）。</p>
 * <ul>
 *   <li>処理区分が「2（更新）」の行だけ反映する。空欄の行は何もしない（ダウンロードした一覧をそのまま残しておける）。</li>
 *   <li>反映すると、キーに該当する static_data の行すべてと、同じホーム・アウェー・同じカテゴリの future_master の行の
 *       カテゴリを「新しいデータカテゴリ」にする。</li>
 *   <li>現在のデータカテゴリが空の行は「カテゴリが入っていない行」が対象。</li>
 * </ul>
 * <p>フォーマットには、この後ろに参考の列「件数（参考）など」（件数・国リーグ判定・候補・形式を 1 列にまとめたもの）が付く。
 *    参考の列は読まない（消しても、そのままでもよい）。</p>
 *
 * @author shiraishitoshio
 */
@Data
public class DataCategoryCsvRow {

	/** 処理区分（2:更新 のみ。空欄の行は反映しない） */
	@CsvBindByName(column = "処理区分")
	private String operation;

	/** ホームチーム（static_data.home_team_name） */
	@CsvBindByName(column = "ホームチーム")
	private String homeTeamName;

	/** アウェーチーム（static_data.away_team_name） */
	@CsvBindByName(column = "アウェーチーム")
	private String awayTeamName;

	/** 現在のデータカテゴリ（キー。空欄はカテゴリが入っていない行） */
	@CsvBindByName(column = "現在のデータカテゴリ")
	private String currentDataCategory;

	/** 新しいデータカテゴリ（「国: リーグ - ラウンド N」。処理区分 2 の行は必須） */
	@CsvBindByName(column = "新しいデータカテゴリ")
	private String newDataCategory;
}
