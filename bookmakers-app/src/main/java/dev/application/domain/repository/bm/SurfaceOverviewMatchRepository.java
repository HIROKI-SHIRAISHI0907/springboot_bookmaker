package dev.application.domain.repository.bm;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import dev.application.analyze.bm_m031.SurfaceOverviewMatchEntity;

/**
 * surface_overview_match Mapper（BM_M031 チーム × 1試合の明細）。
 *
 * <h2>何をするクラスか</h2>
 * <ul>
 *   <li>{@link #findSeq}: 既存行の seq を取得する（再処理で番号を消費しないため）。</li>
 *   <li>{@link #upsert}: 1行を UPSERT する。一意キー (season, country, league, team, opponent, ha) が既にあれば上書き。</li>
 * </ul>
 * <p>
 * 月別の成績・連続記録・順位の読み取りはビュー（surface_overview / surface_overview_season / surface_overview_standing /
 * surface_overview_match_state）から行うこと。旧 SurfaceOverviewRepository を置き換える。
 * </p>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>PostgreSQL 専用構文</b>（ON CONFLICT）。</li>
 *   <li><b>メタデータは NOT NULL</b>。Entity に値がなければ ID='SYSTEM'、日時=NOW()。更新時は seq・register_* を変えない。</li>
 *   <li><b>同時実行で同じ試合を2つの処理が新規として採番した場合</b>: 後の INSERT は更新になり、後の番号は欠番になる。</li>
 * </ul>
 */
@Mapper
public interface SurfaceOverviewMatchRepository {

	/**
	 * 既存行の seq（なければ null）。
	 */
	@Select({
			"SELECT seq FROM surface_overview_match ",
			"WHERE season = #{season} AND country = #{country} AND league = #{league} ",
			"AND team = #{team} AND opponent = #{opponent} AND ha = #{ha}"
	})
	String findSeq(
			@Param("season") String season,
			@Param("country") String country,
			@Param("league") String league,
			@Param("team") String team,
			@Param("opponent") String opponent,
			@Param("ha") String ha);

	/**
	 * 1行を UPSERT する。
	 *
	 * @param entity 登録対象（seq・season 設定済み）
	 * @return 処理件数（INSERT・UPDATE とも 1）
	 */
	@Insert({
			"INSERT INTO surface_overview_match (",
			"seq, season, country, league, team, ",
			"opponent, ha, match_id, round_no, total_rounds, ",
			"phase, match_time, game_year, game_month, result, ",
			"pk_flg, points, goals_for, goals_against, goals_for_1st, ",
			"goals_for_2nd, goals_against_1st, goals_against_2nd, first_goal, flow_known, ",
			"ever_led, ever_trailed, led_1_0, led_2_0, trailed_0_1, ",
			"trailed_0_2, ",
			"register_id, register_time, update_id, update_time",
			") VALUES (",
			"#{seq}, #{season}, #{country}, #{league}, #{team}, ",
			"#{opponent}, #{ha}, #{matchId}, #{roundNo}, #{totalRounds}, ",
			"#{phase}, #{matchTime}, #{gameYear}, #{gameMonth}, #{result}, ",
			"#{pkFlg}, #{points}, #{goalsFor}, #{goalsAgainst}, #{goalsFor1st}, ",
			"#{goalsFor2nd}, #{goalsAgainst1st}, #{goalsAgainst2nd}, #{firstGoal}, #{flowKnown}, ",
			"#{everLed}, #{everTrailed}, #{led10}, #{led20}, #{trailed01}, ",
			"#{trailed02}, ",
			"COALESCE(#{registerId}, 'SYSTEM'), COALESCE(CAST(#{registerTime} AS timestamptz), NOW()), ",
			"COALESCE(#{updateId}, 'SYSTEM'), COALESCE(CAST(#{updateTime} AS timestamptz), NOW())",
			") ON CONFLICT (season, country, league, team, opponent, ha) DO UPDATE SET ",
			// seq・一意キー・register_* は変えない
			"match_id = EXCLUDED.match_id, round_no = EXCLUDED.round_no, total_rounds = EXCLUDED.total_rounds, ",
			"phase = EXCLUDED.phase, match_time = EXCLUDED.match_time, game_year = EXCLUDED.game_year, ",
			"game_month = EXCLUDED.game_month, result = EXCLUDED.result, pk_flg = EXCLUDED.pk_flg, ",
			"points = EXCLUDED.points, goals_for = EXCLUDED.goals_for, goals_against = EXCLUDED.goals_against, ",
			"goals_for_1st = EXCLUDED.goals_for_1st, goals_for_2nd = EXCLUDED.goals_for_2nd, goals_against_1st = EXCLUDED.goals_against_1st, ",
			"goals_against_2nd = EXCLUDED.goals_against_2nd, first_goal = EXCLUDED.first_goal, flow_known = EXCLUDED.flow_known, ",
			"ever_led = EXCLUDED.ever_led, ever_trailed = EXCLUDED.ever_trailed, led_1_0 = EXCLUDED.led_1_0, ",
			"led_2_0 = EXCLUDED.led_2_0, trailed_0_1 = EXCLUDED.trailed_0_1, trailed_0_2 = EXCLUDED.trailed_0_2, ",
			"update_id = EXCLUDED.update_id, update_time = EXCLUDED.update_time"
	})
	int upsert(SurfaceOverviewMatchEntity entity);
}