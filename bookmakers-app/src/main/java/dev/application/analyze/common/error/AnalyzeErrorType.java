package dev.application.analyze.common.error;

/**
 * 統計処理で試合を登録できなかった理由（analyze_error_match.error_type）。
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
}
