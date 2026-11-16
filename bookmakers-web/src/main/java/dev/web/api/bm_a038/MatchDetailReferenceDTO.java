package dev.web.api.bm_a038;

import lombok.Data;

/**
 * 統計詳細データ参照: 1 試合分（match_detail_match の 1 行と、各テーブルの行数）
 * @author shiraishitoshio
 */
@Data
public class MatchDetailReferenceDTO {

	/** 試合ID（Flashscore の mid） */
	private String matchId;

	/** 試合日 "yyyy-MM-dd" */
	private String matchDate;

	/** 試合国及びカテゴリ */
	private String category;

	private String homeTeam;

	private String awayTeam;

	private String homeScore;

	private String awayScore;

	/** 状態（終了 など） */
	private String status;

	/** 開始日時（ページの表記のまま） */
	private String startTime;

	private String matchUrl;

	/** 取得日時（日本時間。スクレイパーの表記のまま） */
	private String scrapedAt;

	/** 取れなかったページ（カンマ区切り。全部取れたら空） */
	private String scrapeErrors;

	/** 登録日時（日本時間 "yyyy-MM-dd HH:mm:ss"） */
	private String registerTime;

	/** 各テーブルの行数 */
	private int momentumCount;

	private int incidentCount;

	private int summaryCount;

	private int goalCount;

	private int formationCount;

	private int lineupCount;

	private int missingCount;

	private int memberStatCount;
}
