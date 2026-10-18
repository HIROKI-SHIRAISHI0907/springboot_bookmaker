package dev.web.api.bm_a031;

import com.opencsv.bean.CsvBindByName;

import lombok.Data;

/**
 * シーズンデータ CSV の 1 行（country_league_season_master）
 *
 * <p>値はすべて文字列で受け取り、チェックはサービスで行う（型変換エラーで全体が止まらないように）。</p>
 * <p>キー: 国 + リーグ。削除・非表示・再表示はキーだけで決める（ID は使わない）。</p>
 *
 * @author shiraishitoshio
 */
@Data
public class SeasonCsvRow {

	/** 処理区分（1:登録 2:更新 3:削除 4:非表示 5:再表示） */
	@CsvBindByName(column = "処理区分")
	private String operation;

	/** ID（更新のときだけ使う。任意。国・リーグ名を変える更新で使う。登録・削除・非表示・再表示では空欄） */
	@CsvBindByName(column = "ID")
	private String id;

	/** 国 */
	@CsvBindByName(column = "国")
	private String country;

	/** リーグ */
	@CsvBindByName(column = "リーグ")
	private String league;

	/** シーズン年（例: 2026 / 2026-2027） */
	@CsvBindByName(column = "シーズン年")
	private String seasonYear;

	/** シーズン開始日（yyyy-MM-dd / yyyy/MM/dd、時刻付きも可） */
	@CsvBindByName(column = "シーズン開始日")
	private String startSeasonDate;

	/** シーズン終了日 */
	@CsvBindByName(column = "シーズン終了日")
	private String endSeasonDate;

	/** ラウンド数 */
	@CsvBindByName(column = "ラウンド数")
	private String round;

	/** パス（Flashscore のリーグのパス） */
	@CsvBindByName(column = "パス")
	private String path;

	/** アイコン */
	@CsvBindByName(column = "アイコン")
	private String icon;

	/** 有効フラグ（登録時に空なら '0'） */
	@CsvBindByName(column = "有効フラグ")
	private String validFlg;
}
