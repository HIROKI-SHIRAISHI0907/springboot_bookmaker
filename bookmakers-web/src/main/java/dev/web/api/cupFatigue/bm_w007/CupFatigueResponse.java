package dev.web.api.cupFatigue.bm_w007;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 直近の大会（ACL など）出場（今後の試合予定・試合中の画面の「大会出場（疲労）」）
 * @author shiraishitoshio
 */
@Data
public class CupFatigueResponse {

	/** どちらかのチームが大会の出場チームに登録されているか */
	private boolean found;
	/** 基準の日時（JST, yyyy-MM-dd HH:mm。この試合の開始日時） */
	private String baseTime;
	/** 何日前・何日後まで見るか */
	private int days;
	private TeamFatigue home;
	private TeamFatigue away;

	/** チームごと */
	@Data
	public static class TeamFatigue {
		private String team;
		/** 大会の出場チームに登録されているか */
		private boolean cupTeam;
		/** 出場する大会名（登録されている大会。「、」区切り） */
		private String competitions;
		/** 基準の日時より前の大会の試合（新しい順） */
		private List<CupMatch> recent = new ArrayList<>();
		/** 基準の日時より後の大会の試合（近い順） */
		private List<CupMatch> upcoming = new ArrayList<>();
	}

	/** 大会の 1 試合 */
	@Data
	public static class CupMatch {
		private String mid;
		private String competitionName;
		private String round;
		/** 開始日時（JST, yyyy-MM-dd HH:mm） */
		private String kickoff;
		/** 基準の日時との差（時間。前なら正、後なら正の値で「何時間後」） */
		private long hours;
		/** 基準の日時との差（日。試合日どうしの差） */
		private long days;
		/** HOME / AWAY（このチームが大会の試合でどちらか） */
		private String side;
		private String opponent;
		private Integer teamScore;
		private Integer opponentScore;
		/** 出場選手が登録済みか（B016 で取得済み） */
		private boolean registered;
		/** スタメン人数 */
		private int starterCount;
		/** 出場人数（スタメン + 途中出場） */
		private int playedCount;
		/** 出場選手（スタメン → 途中出場の順） */
		private List<Player> players = new ArrayList<>();
	}

	/** 出場した選手 */
	@Data
	public static class Player {
		private String playerName;
		private String jerseyNumber;
		private String role;
		/** スタメン / ベンチ（途中出場） */
		private String lineupType;
		/** 出場時間（分。分からなければ null） */
		private Integer minutes;
		private String subInMinute;
		private String subOutMinute;
		private String rating;
	}
}
