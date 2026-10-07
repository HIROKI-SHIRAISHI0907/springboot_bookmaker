package dev.web.api.dashboard.matchDTO;

import lombok.Data;

/**
 * static_data の試合ごとの最新行（ライブ・今日終了した試合）
 * @author shiraishitoshio
 *
 */
@Data
public class DashboardMatchRow {
	/** 例: "0drkxQrA-12" */
	private String seqKey;
	private String matchId;
	private String dataCategory;
	private String times;
	private String homeTeamName;
	private String awayTeamName;
	private String homeRank;
	private String awayRank;
	private String homeScore;
	private String awayScore;
	private String homeExp;
	private String awayExp;
	private String homeShootIn;
	private String awayShootIn;
	private String homeDonation;
	private String awayDonation;
	private String recordTime;
	/** 前半終了時のスコア（ハーフタイムの行。無ければ null） */
	private String htHomeScore;
	private String htAwayScore;
}
