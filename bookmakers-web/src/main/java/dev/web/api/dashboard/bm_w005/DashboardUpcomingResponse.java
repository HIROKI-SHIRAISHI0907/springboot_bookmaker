package dev.web.api.dashboard.bm_w005;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * これからの試合のレスポンス
 * <ul>
 *   <li>GET /v1/api/dashboard/upcoming（トップ画面: リーグごとに直近 perLeague 試合・最大 maxLeagues リーグ）</li>
 *   <li>GET /v1/api/upcoming-matches（全試合画面: 期間内の全試合）</li>
 * </ul>
 * @author shiraishitoshio
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardUpcomingResponse {

	private boolean loggedIn;

	/** 画面に返した試合数（matches の件数） */
	private int count;

	/** 期間内の全試合数（「全試合 →」の件数） */
	private int totalCount;

	/** 期間内の全リーグ数 */
	private int totalLeagueCount;

	/** 何時間後までか */
	private int hours;

	/** 返した試合（キックオフ順。leagues を平らにしたもの） */
	private List<DashboardUpcomingMatchDTO> matches;

	/** リーグごと（最初の試合のキックオフ順） */
	private List<DashboardUpcomingLeagueDTO> leagues;
}
