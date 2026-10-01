package dev.application.analyze.common.error;

import java.util.ArrayList;
import java.util.List;

/**
 * 登録できなかった試合の情報（{@link AnalyzeErrorRecorder} に渡す）。record は使わない。
 *
 * <p>使い方: {@code AnalyzeErrorInfo.match(category, home, away).matchId(id).field("homeScore", "-")}</p>
 *
 * <h2>どの項目でエラーになったか（field）</h2>
 * <p>
 * 【追加】{@link #field(String, Object)} で、エラーの原因になった項目名と、そのときの値を持たせる。
 * 複数の項目が原因のときは続けて呼ぶ（例: {@code .field("country", c).field("league", l)}）。
 * analyze_error_match の error_field（項目名をカンマ区切り。一意キーの一部）と error_value（「項目名=値」を "; " 区切り）に入る。
 * 項目名は BookDataEntity のフィールド名（homeScore など）か、キーなら dataCategory、国・リーグなら country / league にそろえる。
 * </p>
 *
 * @author shiraishitoshio
 */
public final class AnalyzeErrorInfo {

	/** 値の最大文字数（1項目あたり） */
	private static final int MAX_VALUE = 200;

	private String country;
	private String league;
	private String dataCategory;
	private String homeTeamName;
	private String awayTeamName;
	private String matchId;
	private String detail;

	/** エラーの原因になった項目名（追加順） */
	private final List<String> fieldNames = new ArrayList<>();

	/** 項目のそのときの値（fieldNames と同じ順） */
	private final List<String> fieldValues = new ArrayList<>();

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

	/**
	 * エラーの原因になった項目と、そのときの値（同じ項目名は1回だけ。値が null なら "(null)"、空なら "(空)"）。
	 *
	 * @param fieldName 項目名（例: homeScore）
	 * @param value そのときの値
	 * @return this
	 */
	public AnalyzeErrorInfo field(String fieldName, Object value) {
		if (fieldName == null || fieldName.isBlank() || this.fieldNames.contains(fieldName.trim())) {
			return this;
		}
		this.fieldNames.add(fieldName.trim());
		this.fieldValues.add(display(value));
		return this;
	}

	/** 原因の項目が設定されているか */
	public boolean hasField() {
		return !this.fieldNames.isEmpty();
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

	/** 原因の項目名（カンマ区切り。無ければ空文字） */
	public String getErrorField() {
		return String.join(",", this.fieldNames);
	}

	/** 原因の項目の値（「項目名=値」を "; " 区切り。無ければ null） */
	public String getErrorValue() {
		if (this.fieldNames.isEmpty()) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (int k = 0; k < this.fieldNames.size(); k++) {
			if (k > 0) {
				sb.append("; ");
			}
			sb.append(this.fieldNames.get(k)).append('=').append(this.fieldValues.get(k));
		}
		return sb.toString();
	}

	private static String display(Object value) {
		if (value == null) {
			return "(null)";
		}
		String s = String.valueOf(value);
		if (s.isBlank()) {
			return "(空)";
		}
		return s.length() <= MAX_VALUE ? s : s.substring(0, MAX_VALUE) + "…";
	}

	@Override
	public String toString() {
		return "country=" + this.country + ", league=" + this.league + ", category=" + this.dataCategory
				+ ", home=" + this.homeTeamName + ", away=" + this.awayTeamName + ", matchId=" + this.matchId
				+ (this.fieldNames.isEmpty() ? "" : ", field=[" + getErrorValue() + "]")
				+ (this.detail == null ? "" : ", " + this.detail);
	}
}
