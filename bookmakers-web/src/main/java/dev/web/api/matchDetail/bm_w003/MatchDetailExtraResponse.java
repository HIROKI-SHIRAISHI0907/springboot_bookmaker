package dev.web.api.matchDetail.bm_w003;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 試合詳細の統計詳細データ（GET /v1/api/match-detail/extra?matchId=）。
 * B016 が登録した match_detail_* テーブルの内容。値は文字列のまま返す（数値の解釈は画面側）。
 * side は "HOME" / "AWAY"。
 * @author shiraishitoshio
 */
@Data
public class MatchDetailExtraResponse {

	/** match_detail_match に試合があるか */
	private boolean found;

	private String matchId;
	private String category;
	private String homeTeam;
	private String awayTeam;
	private String homeScore;
	private String awayScore;
	/** スクレイピング時に取れなかったページ（空なら全部取れた） */
	private String scrapeErrors;

	/** ハーフごとのスコア（前半・後半…） */
	private List<Half> halves = new ArrayList<>();
	private List<Momentum> momentum = new ArrayList<>();
	private List<MomentumIncident> momentumIncidents = new ArrayList<>();
	private List<Event> events = new ArrayList<>();
	private List<Goal> goals = new ArrayList<>();
	private List<Formation> formations = new ArrayList<>();
	private List<Lineup> lineups = new ArrayList<>();
	private List<Missing> missing = new ArrayList<>();
	private List<PlayerStat> playerStats = new ArrayList<>();

	@Data
	public static class Half {
		private String half;
		private String score;
	}

	@Data
	public static class Momentum {
		private String half;
		private String minuteLabel;
		private String minute;
		private String addedMinute;
		private String side;
		private String value;
		private String signedValue;
	}

	@Data
	public static class MomentumIncident {
		private String type;
		private String minuteLabel;
		private String minute;
		private String side;
	}

	@Data
	public static class Event {
		private String half;
		private String timeLabel;
		private String minute;
		private String addedMinute;
		private String side;
		private String type;
		private String player;
		private String subOutPlayer;
		private String score;
		private String note;
		private String assist;
		private String description;
	}

	@Data
	public static class Goal {
		private String timeLabel;
		private String minute;
		private String addedMinute;
		private String half;
		private String side;
		private String scorer;
		private String scorerTeam;
		private boolean ownGoal;
		private boolean penalty;
		private String assist;
		private String score;
	}

	@Data
	public static class Formation {
		private String side;
		private String teamName;
		private String formation;
		private String averageRating;
		private String coach;
		private String coachNationality;
	}

	@Data
	public static class Lineup {
		private String side;
		/** スタメン / ベンチ */
		private String lineupType;
		private String playerId;
		private String number;
		private String name;
		private String nationality;
		private String role;
		private boolean played;
		private String subInMinute;
		private String subInFor;
		private String subOutMinute;
		private String subOutFor;
		private String rating;
		/** ピッチ上の行（1 = GK 側）。スタメンだけ */
		private String pitchRow;
		/** ピッチ上の列（左から） */
		private String pitchCol;
		private String goals;
		private String ownGoals;
		/** 出来事のアイコン（"/" 区切り。例: goal-soccer×2/yellow-card） */
		private String events;
	}

	@Data
	public static class Missing {
		private String side;
		private String name;
		private String nationality;
		private String reason;
	}

	@Data
	public static class PlayerStat {
		private String playerId;
		private String name;
		private String position;
		private String side;
		private String rating;
		private String minutes;
		private String goals;
		private String assists;
		private String xg;
		private String xa;
		private String shots;
		private String shotsOnTarget;
		private String bigChancesCreated;
		private String keyPasses;
		private String passesAccurate;
		private String passesFinalThird;
		private String longBalls;
		private String crosses;
		private String touches;
		private String touchesBox;
		private String dribblesWon;
		private String duelsWon;
		private String duelsTotal;
		private String aerialsWon;
		private String tacklesWon;
		private String interceptions;
		private String clearances;
		private String foulsCommitted;
		private String foulsSuffered;
		private String yellowCards;
		private String redCards;
		private String saves;
		private String goalsConceded;
		private String goalsPrevented;
	}
}
