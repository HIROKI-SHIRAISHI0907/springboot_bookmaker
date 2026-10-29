package dev.web.api.bm_a034;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * リーグ所属チーム付け替え設定（GET /v1/api/admin/league-team?country=）
 * @author shiraishitoshio
 */
@Data
public class LeagueTeamResponse {

	private String country;

	/** リーグ（名前順）。各リーグの所属チームつき */
	private List<League> leagues = new ArrayList<>();

	/**
	 * 重複しているチーム（バリデーションエラー）。
	 * 同じ国の有効なチームで、リンクの「/team/xxx/yyy/」の部分が同じものが2行以上ある。
	 * 1件でもあれば付け替えはできない（リーグから外すことはできる）。
	 */
	private List<Duplicate> duplicates = new ArrayList<>();

	@Data
	public static class Country {
		private String country;
		private int leagueCount;
	}

	@Data
	public static class League {
		private String league;
		/** シーズンのマスタ（country_league_season_master）にあるか */
		private boolean inSeason;
		private String seasonYear;
		private List<Team> teams = new ArrayList<>();
	}

	@Data
	public static class Team {
		private Integer id;
		private String league;
		private String team;
		private String link;
	}

	@Data
	public static class Duplicate {
		/** 比較に使ったリンク（/team/xxx/yyy/） */
		private String link;
		private String team;
		/** 重複している行のリーグ（行の数だけ。同じリーグに複数あれば同じ名前が並ぶ） */
		private List<String> leagues = new ArrayList<>();
		/** 重複している行の id */
		private List<Integer> ids = new ArrayList<>();
		/** 「XXX チームが J1 リーグと J2 リーグに重複しています。」 */
		private String message;
	}
}
