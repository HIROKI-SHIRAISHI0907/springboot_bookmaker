package dev.web.api.dashboard.bm_w006;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * トップ画面「本日の試合結果」（GET /v1/api/dashboard/results）
 * @author shiraishitoshio
 */
@Data
public class DashboardResultsResponse {

	/** 対象日（JST, yyyy-MM-dd） */
	private String date;

	/** 試合数 */
	private int count;

	/** 統計詳細データ（match_detail_*）がある試合数 */
	private int detailCount;

	private List<League> leagues = new ArrayList<>();

	@Data
	public static class League {
		/** 「国: リーグ」 */
		private String label;
		private String country;
		private String league;
		private List<Match> matches = new ArrayList<>();
	}

	@Data
	public static class Match {
		/** 試合詳細（/match-detail?seqKey=）を開くキー */
		private String seqKey;
		private String matchId;
		private String round;
		private String homeTeam;
		private String awayTeam;
		private Integer homeScore;
		private Integer awayScore;
		private Integer halftimeHomeScore;
		private Integer halftimeAwayScore;
		/** 最後に記録された時刻（JST, "yyyy-MM-dd HH:mm:ss"） */
		private String recordTime;
		/** 統計詳細データ（メンバー・選手スタッツなど）があるか */
		private boolean hasDetail;
	}
}
