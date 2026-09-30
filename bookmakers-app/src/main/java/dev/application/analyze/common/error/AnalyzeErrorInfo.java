package dev.application.analyze.common.error;

/**
 * 登録できなかった試合の情報（{@link AnalyzeErrorRecorder} に渡す）。record は使わない。
 *
 * <p>使い方: {@code AnalyzeErrorInfo.match(category, home, away).matchId(id)}</p>
 *
 * @author shiraishitoshio
 */
public final class AnalyzeErrorInfo {

	private String country;
	private String league;
	private String dataCategory;
	private String homeTeamName;
	private String awayTeamName;
	private String matchId;
	private String detail;

	private AnalyzeErrorInfo() {
	}

	/** 空の情報 */
	public static AnalyzeErrorInfo empty() {
		return new AnalyzeErrorInfo();
	}

	/** 試合（キー・ホーム・アウェー） */
	public static AnalyzeErrorInfo match(String dataCategory, String homeTeamName, String awayTeamName) {
		AnalyzeErrorInfo i = new AnalyzeErrorInfo();
		i.dataCategory = dataCategory;
		i.homeTeamName = homeTeamName;
		i.awayTeamName = awayTeamName;
		return i;
	}

	/** 国・リーグ */
	public AnalyzeErrorInfo countryLeague(String country, String league) {
		this.country = country;
		this.league = league;
		return this;
	}

	/** 元のキー（「国: リーグ - ラウンドN」） */
	public AnalyzeErrorInfo dataCategory(String dataCategory) {
		this.dataCategory = dataCategory;
		return this;
	}

	/** チーム（チーム単位の BM。ホームチーム欄に入れる） */
	public AnalyzeErrorInfo team(String team) {
		this.homeTeamName = team;
		return this;
	}

	/** マッチID */
	public AnalyzeErrorInfo matchId(String matchId) {
		this.matchId = matchId;
		return this;
	}

	/** 補足（H/A・年など） */
	public AnalyzeErrorInfo detail(String detail) {
		this.detail = detail;
		return this;
	}

	public String getCountry() {
		return this.country;
	}

	public String getLeague() {
		return this.league;
	}

	public String getDataCategory() {
		return this.dataCategory;
	}

	public String getHomeTeamName() {
		return this.homeTeamName;
	}

	public String getAwayTeamName() {
		return this.awayTeamName;
	}

	public String getMatchId() {
		return this.matchId;
	}

	public String getDetail() {
		return this.detail;
	}

	@Override
	public String toString() {
		return "country=" + this.country + ", league=" + this.league + ", category=" + this.dataCategory
				+ ", home=" + this.homeTeamName + ", away=" + this.awayTeamName + ", matchId=" + this.matchId
				+ (this.detail == null ? "" : ", " + this.detail);
	}
}
