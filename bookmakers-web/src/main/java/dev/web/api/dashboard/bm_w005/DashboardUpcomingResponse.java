package dev.web.api.dashboard.bm_w005;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * これからの試合のレスポンス（GET /v1/api/dashboard/upcoming）
 * @author shiraishitoshio
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardUpcomingResponse {

	private boolean loggedIn;

	private int count;

	private List<DashboardUpcomingMatchDTO> matches;
}
