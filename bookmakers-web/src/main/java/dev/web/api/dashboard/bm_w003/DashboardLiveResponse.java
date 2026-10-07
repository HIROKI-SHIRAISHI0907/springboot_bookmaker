package dev.web.api.dashboard.bm_w003;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ライブのレスポンス（GET /v1/api/dashboard/live）
 * @author shiraishitoshio
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardLiveResponse {

	/** ログイン中か（false なら数値は null） */
	private boolean loggedIn;

	/** 作成時刻（ISO-8601） */
	private String updatedAt;

	/** ライブの試合数 */
	private int count;

	/** 注目の試合（ライブが無ければ null） */
	private DashboardLiveMatchDTO featured;

	private List<DashboardLiveLeagueDTO> leagues;
}
