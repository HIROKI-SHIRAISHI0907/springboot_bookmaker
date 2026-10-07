package dev.web.api.dashboard.futureDTO;

import lombok.Data;

/**
 * future_master の1行（これからの試合）
 * @author shiraishitoshio
 *
 */
@Data
public class DashboardFutureRow {
	private String gameTeamCategory;
	/** キックオフ（ISO-8601） */
	private String futureTime;
	private String homeRank;
	private String awayRank;
	private String homeTeamName;
	private String awayTeamName;
}
