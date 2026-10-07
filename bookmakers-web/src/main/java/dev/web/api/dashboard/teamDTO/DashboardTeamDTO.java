package dev.web.api.dashboard.teamDTO;

import lombok.Data;

/**
 * トップ画面のチーム表示（順位・得点しやすさ・失点しにくさ）
 * @author shiraishitoshio
 *
 */
@Data
public class DashboardTeamDTO {

	/** チーム名 */
	private String name;

	/** 順位（分からなければ null） */
	private Integer rank;

	/** 得点しやすさ A=◎ / B=○ / C=△（今季の成績が無ければ null） */
	private String attackGrade;

	/** 失点しにくさ A=◎ / B=○ / C=△ */
	private String defenseGrade;

	/** 今季の1試合平均得点（ホーム/アウェー別。未ログインは null） */
	private Double avgGoalsFor;

	/** 今季の1試合平均失点（未ログインは null） */
	private Double avgGoalsAgainst;
}
