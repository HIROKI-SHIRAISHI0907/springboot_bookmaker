package dev.web.api.bm_a036;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 大会出場チーム登録設定（bm_a036）の表示用データ
 * @author shiraishitoshio
 */
public class CupCompetitionResponse {

	private CupCompetitionResponse() {
	}

	/** 大会（cup_competition_master） */
	@Data
	public static class Competition {
		private Integer id;
		/** 大会名（例: AFCチャンピオンズリーグエリート） */
		private String competitionName;
		/** 地域（例: アジア） */
		private String region;
		/** Flashscore の大会ページ（例: https://www.flashscore.co.jp/soccer/asia/afc-champions-league/） */
		private String competitionUrl;
		/** シーズン（例: 2026/2027。任意） */
		private String seasonYear;
		private int dispOrder;
		/** "1" = 日程を取る / "0" = 止める */
		private String scrapeFlg;
		private int teamCount;
	}

	/** 大会の出場チーム（cup_competition_team_master） */
	@Data
	public static class Team {
		private Integer id;
		private Integer competitionId;
		private String country;
		private String league;
		private String team;
		private String link;
	}

	/** 候補の国（スクレイピング対象のリーグがある国） */
	@Data
	public static class CandidateCountry {
		private String country;
		private List<CandidateLeague> leagues = new ArrayList<>();
	}

	/** 候補のリーグ */
	@Data
	public static class CandidateLeague {
		private String league;
		private List<CandidateTeam> teams = new ArrayList<>();
	}

	/** 候補のチーム（country_league_master） */
	@Data
	public static class CandidateTeam {
		private String country;
		private String league;
		private String team;
		private String link;
	}

	/** 大会の日程（S3 の b026_cup_schedule.json） */
	@Data
	public static class Schedule {
		/** 日程 JSON があるか */
		private boolean exists;
		/** 日程を取った日時（スクレイパーが入れる） */
		private String generatedAt;
		private List<ScheduleMatch> matches = new ArrayList<>();
	}

	/** 日程の 1 試合 */
	@Data
	public static class ScheduleMatch {
		/** 試合ID（URL の ?mid=） */
		private String mid;
		private String url;
		/** 試合日（JST, yyyy-MM-dd） */
		private String date;
		/** 開始日時（JST, yyyy-MM-dd HH:mm） */
		private String kickoff;
		private String round;
		private String home;
		private String away;
		/** 登録した出場チームのうち、この試合に出るチーム（"ヴィッセル神戸" など。複数は「、」区切り） */
		private String targetTeams;
		/**
		 * 状態
		 * <ul>
		 *   <li>SCHEDULED : 試合前（試合日の翌日になったら出場選手を取る）</li>
		 *   <li>WAITING   : 取る日になった（次の Lambda 実行で取得を依頼する）</li>
		 *   <li>REQUESTED : 取得を依頼した（B016 のスクレイピング → 登録待ち）</li>
		 *   <li>REGISTERED: 出場選手を登録済み（match_detail_match にある）</li>
		 *   <li>EXPIRED   : 依頼しないまま取る期間を過ぎた（再取得で依頼できる）</li>
		 * </ul>
		 */
		private String status;
		/** 取得を依頼した日時（JST） */
		private String requestedAt;
	}

	/** 登録・更新・削除の結果 */
	@Data
	public static class Result {
		/** "0" = 成功 / "9" = エラー */
		private String responseCode;
		private String message;
		private int count;
		private Integer id;

		public static Result ok(String message, int count) {
			Result r = new Result();
			r.setResponseCode("0");
			r.setMessage(message);
			r.setCount(count);
			return r;
		}

		public static Result ng(String message) {
			Result r = new Result();
			r.setResponseCode("9");
			r.setMessage(message);
			return r;
		}
	}
}
