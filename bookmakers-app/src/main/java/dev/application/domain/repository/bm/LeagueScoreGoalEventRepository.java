package dev.application.domain.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import dev.application.analyze.bm_m017.LeagueScoreGoalEventEntity;

/**
 * league_score_goal_event Mapper。
 *
 * <h2>何をするクラスか</h2>
 * <ul>
 *   <li>{@link #upsert}: 1ゴールを UPSERT する。一意キー（シーズン・国・リーグ・ホーム・アウェー・何点目）が既にあれば上書き。</li>
 *   <li>{@link #deleteGoalsAfter}: 試合のゴール数より後ろの行を消す（ゴール取り消しで点数が減った場合の後始末）。</li>
 *   <li>{@link #findSeqByMatchKey}: 試合の既存行の seq を取得する（再処理で番号を消費しないため）。</li>
 * </ul>
 * <p>
 * 旧 LeagueScoreTimeBandStatsRepository / LeagueScoreTimeBandStatsSplitScoreRepository を置き換える。
 * 件数・割合はビュー（league_score_time_band_stats / league_score_time_band_stats_split_score）から読むこと。
 * </p>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>PostgreSQL 専用構文</b>（ON CONFLICT）。</li>
 *   <li><b>メタデータは NOT NULL</b>。Entity に値がなければ ID='SYSTEM'、日時=NOW()。更新時は register_* を変えない。</li>
 *   <li><b>同時実行で同じ試合を2つの処理が新規として採番した場合</b>: 後の INSERT は更新になり、後の番号は欠番になる。</li>
 * </ul>
 */
@Mapper
public interface LeagueScoreGoalEventRepository {

	/**
	 * 1試合分の既存行の何点目（goal_no）と seq を取得する。
	 *
	 * @return 既存行（goalNo と seq のみ設定。なければ空）
	 */
	@Select({
			"SELECT goal_no, seq FROM league_score_goal_event ",
			"WHERE season = #{season} AND country = #{country} AND league = #{league} ",
			"AND home_team_name = #{homeTeamName} AND away_team_name = #{awayTeamName}"
	})
	@Results(id = "goalNoSeq", value = {
			@Result(column = "goal_no", property = "goalNo"),
			@Result(column = "seq", property = "seq")
	})
	List<LeagueScoreGoalEventEntity> findSeqByMatchKey(
			@Param("season") String season,
			@Param("country") String country,
			@Param("league") String league,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName);

	/**
	 * 1試合のうち、goal_no が maxGoalNo より大きい行を削除する。
	 *
	 * @param maxGoalNo 今回のゴール数（0 なら全行削除）
	 * @return 削除件数
	 */
	@Delete({
			"DELETE FROM league_score_goal_event ",
			"WHERE season = #{season} AND country = #{country} AND league = #{league} ",
			"AND home_team_name = #{homeTeamName} AND away_team_name = #{awayTeamName} ",
			"AND goal_no > #{maxGoalNo}"
	})
	int deleteGoalsAfter(
			@Param("season") String season,
			@Param("country") String country,
			@Param("league") String league,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName,
			@Param("maxGoalNo") int maxGoalNo);

	/**
	 * 1ゴールを UPSERT する。
	 *
	 * @param entity 登録対象
	 * @return 処理件数（INSERT・UPDATE とも 1）
	 */
	@Insert({
			"INSERT INTO league_score_goal_event (",
			"seq, season, country, league, home_team_name, away_team_name, match_id, ",
			"goal_no, scored_side, home_score_value, away_score_value, ",
			"time_range_area, time_band_order, goal_times, ",
			"register_id, register_time, update_id, update_time",
			") VALUES (",
			"#{seq}, #{season}, #{country}, #{league}, #{homeTeamName}, #{awayTeamName}, #{matchId}, ",
			"#{goalNo}, #{scoredSide}, #{homeScoreValue}, #{awayScoreValue}, ",
			"#{timeRangeArea}, #{timeBandOrder}, #{goalTimes}, ",
			"COALESCE(#{registerId}, 'SYSTEM'), COALESCE(CAST(#{registerTime} AS timestamptz), NOW()), ",
			"COALESCE(#{updateId}, 'SYSTEM'), COALESCE(CAST(#{updateTime} AS timestamptz), NOW())",
			") ON CONFLICT (season, country, league, home_team_name, away_team_name, goal_no) DO UPDATE SET ",
			// seq・一意キー・register_* は変えない
			"match_id = EXCLUDED.match_id, ",
			"scored_side = EXCLUDED.scored_side, ",
			"home_score_value = EXCLUDED.home_score_value, ",
			"away_score_value = EXCLUDED.away_score_value, ",
			"time_range_area = EXCLUDED.time_range_area, ",
			"time_band_order = EXCLUDED.time_band_order, ",
			"goal_times = EXCLUDED.goal_times, ",
			"update_id = EXCLUDED.update_id, update_time = EXCLUDED.update_time"
	})
	int upsert(LeagueScoreGoalEventEntity entity);
}