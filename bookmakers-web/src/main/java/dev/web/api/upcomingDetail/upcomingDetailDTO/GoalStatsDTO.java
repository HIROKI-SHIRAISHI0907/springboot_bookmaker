package dev.web.api.upcomingDetail.upcomingDetailDTO;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * ゴール数などの統計（全試合 / ホーム時 / アウェー時 のどれか 1 つ）
 * <p>率は画面で 試合数 を分母に計算する（ここでは件数だけ返す）。前半・後半は halfKnownGames の試合だけの合計。</p>
 * @author shiraishitoshio
 */
@Data
public class GoalStatsDTO {

	/** ALL: 全試合 / HOME: ホーム時 / AWAY: アウェー時 */
	private String scope;

	private int games;

	private int win;

	private int draw;

	private int lose;

	private int points;

	/** 得点・失点（PK 戦の得点は含めない） */
	private int goalsFor;

	private int goalsAgainst;

	/** 1 試合平均（試合が無ければ null。小数 2 桁） */
	private Double avgGoalsFor;

	private Double avgGoalsAgainst;

	/** 前半・後半の得点が分かる試合数 */
	private int halfKnownGames;

	private int goalsFor1st;

	private int goalsFor2nd;

	private int goalsAgainst1st;

	private int goalsAgainst2nd;

	/** 無失点 */
	private int cleanSheets;

	/** 無得点 */
	private int failedToScore;

	/** 両チーム得点 */
	private int bothScored;

	/** 合計 2 点以上 / 3 点以上 / 4 点以上 */
	private int over15;

	private int over25;

	private int over35;

	/** 先制した / 先制された */
	private int scoredFirst;

	private int concededFirst;

	/** 直近 5 試合の結果 W / D / L（左が新しい） */
	private List<String> form = new ArrayList<>();
}
