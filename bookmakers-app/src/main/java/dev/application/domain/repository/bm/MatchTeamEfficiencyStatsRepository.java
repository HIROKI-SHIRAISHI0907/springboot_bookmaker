package dev.application.domain.repository.bm;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import dev.application.analyze.bm_m036.MatchTeamEfficiencyStatsEntity;

/**
 * {@link MatchTeamEfficiencyStatsEntity} のRepositoryです。
 */
@Mapper
public interface MatchTeamEfficiencyStatsRepository {

	/**
	 * 攻撃効率統計を登録します。
	 *
	 * @param entity 登録対象
	 * @return 登録件数
	 */
	@Insert("""
			INSERT INTO match_team_efficiency_stats (
			    match_id,
			    season,
			    country,
			    league_id,
			    league_name,
			    team_id,
			    team_name,
			    opponent_team_id,
			    opponent_team_name,
			    goals_count,
			    shots_count,
			    shots_on_target_count,
			    shots_off_target_count,
			    box_shots_count,
			    non_box_shots_count,
			    box_touches_count,
			    set_piece_shots_count,
			    set_piece_goals_count,
			    on_target_rate,
			    off_target_rate,
			    box_shot_rate,
			    box_touch_to_shot_rate,
			    shot_to_goal_rate,
			    on_target_to_goal_rate,
			    set_piece_dependency_rate,
			    efficiency_note,
			    calculated_at,
			    register_id,
			    register_time,
			    update_id,
			    update_time
			) VALUES (
			    #{matchId},
			    #{season},
			    #{country},
			    #{leagueId},
			    #{leagueName},
			    #{teamId},
			    #{teamName},
			    #{opponentTeamId},
			    #{opponentTeamName},
			    #{goalsCount},
			    #{shotsCount},
			    #{shotsOnTargetCount},
			    #{shotsOffTargetCount},
			    #{boxShotsCount},
			    #{nonBoxShotsCount},
			    #{boxTouchesCount},
			    #{setPieceShotsCount},
			    #{setPieceGoalsCount},
			    #{onTargetRate},
			    #{offTargetRate},
			    #{boxShotRate},
			    #{boxTouchToShotRate},
			    #{shotToGoalRate},
			    #{onTargetToGoalRate},
			    #{setPieceDependencyRate},
			    #{efficiencyNote},
			    #{calculatedAt},
			    'SYSTEM',
			    CURRENT_TIMESTAMP,
			    'SYSTEM',
			    CURRENT_TIMESTAMP
			)
			""")
	int insert(MatchTeamEfficiencyStatsEntity entity);
}
