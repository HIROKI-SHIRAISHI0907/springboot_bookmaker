package dev.web.api.bm_a031;

import com.opencsv.bean.CsvBindByName;

import lombok.Data;

/**
 * 試合予定データ CSV の 1 行（future_master）
 *
 * <p>列名はスクレイピング CSV と同じ日本語名にそろえている。</p>
 * <p>キー: ホームチーム + アウェーチーム + 試合国及びカテゴリ。削除・非表示・再表示はキーだけで決める（ID は使わない）。</p>
 *
 * @author shiraishitoshio
 */
@Data
public class FutureCsvRow {

	/** 処理区分（1:登録 2:更新 3:削除 4:非表示 5:再表示） */
	@CsvBindByName(column = "処理区分")
	private String operation;

	/** ID（future_master.seq。更新のときだけ使う。任意。チーム名・カテゴリを変える更新で使う。登録・削除・非表示・再表示では空欄） */
	@CsvBindByName(column = "ID")
	private String id;

	/** 試合国及びカテゴリ（「国: リーグ - ラウンド N」。区切りは半角のみ。例: 日本: J1リーグ - ラウンド 30） */
	@CsvBindByName(column = "試合国及びカテゴリ")
	private String gameTeamCategory;

	/** 試合予定時間（日本時間。yyyy-MM-dd HH:mm[:ss] / yyyy/MM/dd HH:mm[:ss]） */
	@CsvBindByName(column = "試合予定時間")
	private String futureTime;

	@CsvBindByName(column = "ホーム順位")
	private String homeRank;

	@CsvBindByName(column = "アウェー順位")
	private String awayRank;

	@CsvBindByName(column = "ホームチーム")
	private String homeTeamName;

	@CsvBindByName(column = "アウェーチーム")
	private String awayTeamName;

	@CsvBindByName(column = "ホームチーム最大得点取得者")
	private String homeMaxGettingScorer;

	@CsvBindByName(column = "アウェーチーム最大得点取得者")
	private String awayMaxGettingScorer;

	@CsvBindByName(column = "ホームチームホーム得点")
	private String homeTeamHomeScore;

	@CsvBindByName(column = "ホームチームホーム失点")
	private String homeTeamHomeLost;

	@CsvBindByName(column = "アウェーチームホーム得点")
	private String awayTeamHomeScore;

	@CsvBindByName(column = "アウェーチームホーム失点")
	private String awayTeamHomeLost;

	@CsvBindByName(column = "ホームチームアウェー得点")
	private String homeTeamAwayScore;

	@CsvBindByName(column = "ホームチームアウェー失点")
	private String homeTeamAwayLost;

	@CsvBindByName(column = "アウェーチームアウェー得点")
	private String awayTeamAwayScore;

	@CsvBindByName(column = "アウェーチームアウェー失点")
	private String awayTeamAwayLost;

	/** 試合リンク文字列 */
	@CsvBindByName(column = "試合リンク文字列")
	private String gameLink;

	/** データ取得時間（日本時間。登録で空欄なら現在日時） */
	@CsvBindByName(column = "データ取得時間")
	private String dataTime;
}
