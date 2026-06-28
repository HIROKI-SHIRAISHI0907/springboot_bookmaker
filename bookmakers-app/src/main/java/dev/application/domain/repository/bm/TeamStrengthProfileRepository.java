package dev.application.domain.repository.bm;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import dev.application.analyze.bm_m041.TeamStrengthProfileEntity;

/**
 * {@link TeamStrengthProfileEntity} のRepositoryです。
 */
@Mapper
public interface TeamStrengthProfileRepository {

	/**
	 * チーム強度プロファイルを登録します。
	 *
	 * @param entity 登録対象
	 * @return 登録件数
	 */
	@Insert("""
			INSERT INTO team_strength_profile (
			    match_id,
			    season,
			    country,
			    league_id,
			    league_name,
			    team_id,
			    team_name,
			    opponent_team_id,
			    opponent_team_name,
			    snapshot_date,
			    last5_points,
			    last5_goal_diff,
			    home_strength_index,
			    away_strength_index,
			    vs_upper_performance,
			    vs_lower_drop_rate,
			    elo_like_rating,
			    form_index,
			    calculated_at,
			    note,
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
			    #{snapshotDate},
			    #{last5Points},
			    #{last5GoalDiff},
			    #{homeStrengthIndex},
			    #{awayStrengthIndex},
			    #{vsUpperPerformance},
			    #{vsLowerDropRate},
			    #{eloLikeRating},
			    #{formIndex},
			    #{calculatedAt},
			    #{note},
			    'SYSTEM',
			    CURRENT_TIMESTAMP,
			    'SYSTEM',
			    CURRENT_TIMESTAMP
			)
			""")
	int insert(TeamStrengthProfileEntity entity);
}
