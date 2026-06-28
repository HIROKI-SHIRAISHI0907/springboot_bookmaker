package dev.application.domain.repository.bm;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import dev.application.analyze.bm_m039.MatchTeamMomentumStatsEntity;

/**
 * {@link MatchTeamMomentumStatsEntity} のRepositoryです。
 */
@Mapper
public interface MatchTeamMomentumStatsRepository {

	/**
	 * モメンタム統計を登録します。
	 *
	 * @param entity 登録対象
	 * @return 登録件数
	 */
	@Insert("""
			INSERT INTO match_team_momentum_stats (
			    match_id,
			    season,
			    country,
			    league_id,
			    league_name,
			    team_id,
			    team_name,
			    opponent_team_id,
			    opponent_team_name,
			    as_of_seconds,
			    window_minutes,
			    recent_shots_diff,
			    recent_shots_on_target_diff,
			    recent_box_touches_diff,
			    recent_corners_diff,
			    recent_progression_diff,
			    post_goal_attack_response,
			    post_conceded_attack_response,
			    momentum_index,
			    momentum_trend,
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
			    #{asOfSeconds},
			    #{windowMinutes},
			    #{recentShotsDiff},
			    #{recentShotsOnTargetDiff},
			    #{recentBoxTouchesDiff},
			    #{recentCornersDiff},
			    #{recentProgressionDiff},
			    #{postGoalAttackResponse},
			    #{postConcededAttackResponse},
			    #{momentumIndex},
			    #{momentumTrend},
			    #{calculatedAt},
			    'SYSTEM',
			    CURRENT_TIMESTAMP,
			    'SYSTEM',
			    CURRENT_TIMESTAMP
			)
			""")
	int insert(MatchTeamMomentumStatsEntity entity);
}
