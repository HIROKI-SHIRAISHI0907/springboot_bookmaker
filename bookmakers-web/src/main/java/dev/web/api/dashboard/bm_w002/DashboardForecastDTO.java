package dev.web.api.dashboard.bm_w002;

import lombok.Data;

/**
 * 勝ち・分け・負けの見込みとゴール数の見込み
 *
 * <ul>
 *   <li>ライブは「このままなら」（今のスコア＋残り時間の得点見込み）、試合前は「予想」。</li>
 *   <li>未ログインは確率・ゴール数の数値を返さない（null）。バーの描画用に 10% 単位に丸めた bar* だけ返す。</li>
 * </ul>
 * @author shiraishitoshio
 *
 */
@Data
public class DashboardForecastDTO {

	/** ホーム勝ちの確率（%。未ログインは null） */
	private Integer probHome;

	/** 引き分けの確率（%。未ログインは null） */
	private Integer probDraw;

	/** アウェー勝ちの確率（%。未ログインは null） */
	private Integer probAway;

	/** バーの幅（%）。ログイン時は prob* と同じ、未ログインは 10% 単位 */
	private Integer barHome;

	private Integer barDraw;

	private Integer barAway;

	/** ゴール数の見込み HIGH=多い / MID=普通 / LOW=少ない */
	private String goalsLabel;

	/** 試合全体のゴール数の見込み（ライブは今のスコア込み。未ログインは null） */
	private Double expectedTotalGoals;

	/** ライブの残り時間のゴール数の見込み（試合前は null。未ログインは null） */
	private Double expectedRestGoals;
}
