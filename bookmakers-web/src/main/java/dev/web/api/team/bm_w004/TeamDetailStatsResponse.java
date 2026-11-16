package dev.web.api.team.bm_w004;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * チームの統計詳細データ（GET /v1/api/team-detail-stats。2026-11 追加）
 * <p>B016 が登録した試合詳細（match_detail_*）を、チームの今季の試合で集計したもの。</p>
 * @author shiraishitoshio
 */
@Data
public class TeamDetailStatsResponse {

	private String country;

	private String league;

	private String team;

	private Summary summary = new Summary();

	/** フォーメーションの使用回数（多い順） */
	private List<Formation> formations = new ArrayList<>();

	/** 選手ごとの集計（出場数の多い順） */
	private List<Player> players = new ArrayList<>();

	@Data
	public static class Summary {
		/** 統計詳細データがある試合の数 */
		private int matchCount;
		/** その中で一番新しい試合日 "yyyy-MM-dd" */
		private String latestMatchDate;
		/** チームの平均評価点（試合ごとの平均評価点の平均） */
		private Double averageRating;
		/** 直近の試合の監督 */
		private String coach;
	}

	@Data
	public static class Formation {
		private String formation;
		private int count;
	}

	@Data
	public static class Player {
		private String name;
		/** 直近の試合のポジション */
		private String position;
		/** 出場数（選手スタッツがある試合の数） */
		private int apps;
		private Integer minutes;
		/** 平均評価点 */
		private Double rating;
		private Integer goals;
		private Integer assists;
		private Double xg;
		private Double xa;
		private Integer shots;
		private Integer shotsOnTarget;
		private Integer keyPasses;
		private Integer yellowCards;
		private Integer redCards;
		/** セーブ（GK） */
		private Integer saves;
	}
}
