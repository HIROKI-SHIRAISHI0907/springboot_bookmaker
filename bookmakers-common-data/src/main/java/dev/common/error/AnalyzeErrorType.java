package dev.common.error;

/**
 * 試合を登録・出力できなかった理由（analyze_error_match.error_type）。
 * 統計処理（application の各 BM）と CSV 作成（batch の B011）で共通に使う。
 *
 * <p>
 * エラー内容（error_message）は {@link #message(String)} で「ラベル: 補足」の形にそろえる。
 * 呼び出し側で文言を組み立てず、補足（対象の値・キーなど）だけを渡すこと。
 * </p>
 *
 * <p>2026-10 追加: ROUND_NOT_FOUND / CSV_CREATE_FAILED / CSV_DETAIL_REGISTER_FAILED（B011 用）、{@link #message(String)}</p>
 *
 * @author shiraishitoshio
 */
public enum AnalyzeErrorType {

	/** SeasonResolverIF の実装（CountryLeagueSeasonResolver）が Bean 登録されていない */
	SEASON_RESOLVER_MISSING("シーズン取得処理がありません"),

	/** country_league_season_master に該当する国・リーグ・期間のシーズンが無い */
	SEASON_NOT_FOUND("シーズンが見つかりません"),

	/** シーズン取得中に例外が起きた */
	SEASON_RESOLVE_FAILED("シーズン取得でエラー"),

	/** キー（「国: リーグ - ラウンドN」）から国・リーグが取れない */
	INVALID_CATEGORY("国・リーグを取得できません"),

	/** 【追加】キーに国・リーグはあるが、ラウンド番号（ラウンドN）が無い（B011: CSV のフォルダが決まらない） */
	ROUND_NOT_FOUND("ラウンド番号を取得できません"),

	/** 【追加】必須の項目が空（チーム名・スコアなど。どの項目かは error_field） */
	MISSING_VALUE("必須項目が空です"),

	/** 【追加】項目の値が読めない（スコアが数字でないなど。どの項目かは error_field、値は error_value） */
	INVALID_VALUE("値を読み取れません"),

	/** 【追加】CSV の作成中に例外が起きた（B011。どの処理かは detail の step） */
	CSV_CREATE_FAILED("CSV を作成できません"),

	/** 【追加】csv_detail_manage への登録に失敗した（B011） */
	CSV_DETAIL_REGISTER_FAILED("CSV 管理情報を登録できません"),

	/** 上記以外の予期しないエラー */
	UNEXPECTED("予期しないエラー");

	/** 画面表示用の名前 */
	private final String label;

	AnalyzeErrorType(String label) {
		this.label = label;
	}

	/**
	 * 画面表示用の名前。
	 *
	 * @return 名前
	 */
	public String getLabel() {
		return this.label;
	}

	/**
	 * エラー内容（error_message）を「ラベル: 補足」の形で作る。
	 *
	 * @param detail 補足（対象の値・キー・例外のメッセージなど。null・空ならラベルだけ）
	 * @return エラー内容
	 */
	public String message(String detail) {
		if (detail == null || detail.isBlank()) {
			return this.label;
		}
		return this.label + ": " + detail.trim();
	}
}