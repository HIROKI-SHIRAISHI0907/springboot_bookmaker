package dev.web.api.dashboard.teamDTO;

import java.util.List;

import dev.web.api.dashboard.bm_w005.DashboardUpcomingMatchDTO;
import lombok.Data;

/**
 * チームのこれからの1試合（チーム視点の情報つき）
 * @author shiraishitoshio
 */
@Data
public class TeamUpcomingItemDTO {

	/** 試合（トップ画面の「これからの試合」と同じ形。未ログインは数値 null） */
	private DashboardUpcomingMatchDTO match;

	/** H / A */
	private String homeAway;

	/** 対戦相手 */
	private String opponent;

	/** 対戦相手の直近の結果（左が新しい。W / D / L / null） */
	private List<String> opponentForm;

	/** このチームが勝つ確率（%。未ログインは null） */
	private Integer winProb;
}
