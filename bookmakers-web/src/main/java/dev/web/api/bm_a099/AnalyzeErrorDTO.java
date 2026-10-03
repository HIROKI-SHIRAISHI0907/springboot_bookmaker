package dev.web.api.bm_a099;

import lombok.Data;

/**
 * 登録できなかった試合 1件（analyze_error_match の1行）
 * <p>日時は ISO-8601（UTC）の文字列。画面で JST に変換して表示する。stackTrace は詳細取得のときだけ入る。</p>
 * @author shiraishitoshio
 *
 */
@Data
public class AnalyzeErrorDTO {

	/** seq（&lt;発生年&gt;-&lt;6桁枝番&gt;） */
	private String seq;

	/** BM 番号（例: BM_M004） */
	private String bmNumber;

	/** エラー種別（例: SEASON_NOT_FOUND） */
	private String errorType;

	/** エラー種別の表示名（例: シーズンが見つかりません） */
	private String errorTypeLabel;

	/** エラー内容 */
	private String errorMessage;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** 元のキー（「国: リーグ - ラウンドN」） */
	private String dataCategory;

	/** ホームチーム（チーム単位の BM はそのチーム） */
	private String homeTeamName;

	/** アウェーチーム */
	private String awayTeamName;

	/** 原因の項目名（カンマ区切り。例: homeScore / country,league） */
	private String errorField;

	/** 原因の項目のそのときの値（「項目名=値」を "; " 区切り） */
	private String errorValue;

	/** マッチID */
	private String matchId;

	/** シーズン（取得できていれば） */
	private String season;

	/** 補足 */
	private String detail;

	/** 例外クラス名 */
	private String exceptionClass;

	/** スタックトレース（詳細のみ） */
	private String stackTrace;

	/** 発生回数 */
	private Integer occurredCount;

	/** 最初の発生日時 */
	private String firstOccurredAt;

	/** 最後の発生日時 */
	private String lastOccurredAt;

	/** 対応済みか */
	private Boolean resolvedFlg;

	/** 対応日時 */
	private String resolvedAt;

	/** 対応者（自動解決は AUTO） */
	private String resolvedBy;

	/** メモ */
	private String note;
}
