package dev.web.api.bm_a040;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * リアルタイムデータ CSV の列（スクレイパー BmData_aws_S3.py の HEADER と同じ順。ReadOrigin がこの順で読む）。
 * 2026-11-28 追加。
 * @author shiraishitoshio
 */
public final class RealtimeCsvColumns {

	private RealtimeCsvColumns() {
	}

	public static final String HOME_TEAM = "ホームチーム";
	public static final String AWAY_TEAM = "アウェーチーム";
	public static final String CATEGORY = "試合国及びカテゴリ";
	public static final String TIMES = "試合時間";
	public static final String HOME_SCORE = "ホームスコア";
	public static final String AWAY_SCORE = "アウェースコア";
	public static final String RECORD_TIME = "スコア時間";
	public static final String GAME_LINK = "試合リンク文字列";
	public static final String MATCH_ID = "試合ID";
	public static final String SEQ = "通番";
	public static final String SORT_SECONDS = "ソート用秒";

	/** 全列（104 列。順番は ReadOrigin の列番号と同じ） */
	public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
			"ホーム順位", "試合国及びカテゴリ", "試合時間", "ホームチーム", "ホームスコア", "アウェー順位", "アウェーチーム",
			"アウェースコア", "ホーム期待値", "アウェー期待値", "ホーム枠内ゴール期待値", "アウェー枠内ゴール期待値",
			"ホームボール支配率", "アウェーボール支配率", "ホームシュート数", "アウェーシュート数",
			"ホーム枠内シュート数", "アウェー枠内シュート数", "ホーム枠外シュート数", "アウェー枠外シュート数",
			"ホームブロックシュート", "アウェーブロックシュート", "ホームビッグチャンス", "アウェービッグチャンス",
			"ホームコーナーキック", "アウェーコーナーキック", "ホームボックス内シュート", "アウェーボックス内シュート",
			"ホームボックス外シュート", "アウェーボックス外シュート", "ホームゴールポスト", "アウェーゴールポスト",
			"ホームヘディングゴール", "アウェーヘディングゴール",
			"ホームキーパーセーブ", "アウェーキーパーセーブ", "ホームフリーキック", "アウェーフリーキック",
			"ホームオフサイド", "アウェーオフサイド", "ホームファウル", "アウェーファウル",
			"ホームイエローカード", "アウェーイエローカード", "ホームレッドカード", "アウェーレッドカード",
			"ホームスローイン", "アウェースローイン",
			"ホーム相手ボックスタッチ", "アウェー相手ボックスタッチ", "ホームパス", "アウェーパス", "ホームロングパス",
			"アウェーロングパス", "ホームファイナルサードパス", "アウェーファイナルサードパス",
			"ホームクロス", "アウェークロス", "ホームタックル", "アウェータックル", "ホームクリア", "アウェークリア",
			"ホームデュエル勝利数", "アウェーデュエル勝利数",
			"ホームインターセプト", "アウェーインターセプト",
			"スコア時間", "天気", "気温", "湿度", "審判名", "ホーム監督名", "アウェー監督名", "ホームフォーメーション",
			"アウェーフォーメーション",
			"スタジアム", "収容人数", "観客数", "場所", "ホームチーム最大得点取得者", "アウェーチーム最大得点取得者",
			"ホームチーム最大得点取得者出場状況", "アウェーチーム最大得点取得者出場状況",
			"ホームチームホーム得点", "ホームチームホーム失点", "アウェーチームホーム得点", "アウェーチームホーム失点",
			"ホームチームアウェー得点", "ホームチームアウェー失点",
			"アウェーチームアウェー得点", "アウェーチームアウェー失点", "通知フラグ", "試合リンク文字列", "ゴール時間", "選手名",
			"判定結果", "ホームチームスタイル", "アウェイチームスタイル",
			"ゴール確率", "得点予想時間", "試合ID", "通番", "ソート用秒", "当時の試合時間"));

	/** 必須の列（空ならエラー） */
	public static final List<String> REQUIRED = Collections.unmodifiableList(Arrays.asList(
			HOME_TEAM, AWAY_TEAM, CATEGORY, TIMES, RECORD_TIME));
}
