package dev.application.domain.repository.bm;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import dev.application.analyze.bm_m037.MatchTeamDefenseStatsEntity;

/**
 * {@link MatchTeamDefenseStatsEntity} のRepositoryです。
 */
@Mapper
public interface MatchTeamDefenseStatsRepository {

	/**
	 * 守備力・被圧力統計を登録します。
	 *
	 * @param entity 登録対象
	 * @return 登録件数
	 */
	@Insert("""
			INSERT INTO match_team_defense_stats (
			    match_id,
			    season,
			    country,
			    league_id,
			    league_name,
			    team_id,
			    team_name,
			    opponent_team_id,
			    opponent_team_name,
			    shots_conceded_count,
			    shots_on_target_conceded_count,
			    box_touches_conceded_count,
			    keeper_saves_count,
			    save_rate,
			    blocked_shots_count,
			    block_rate,
			    clearances_count,
			    clearance_frequency,
			    tackles_count,
			    interceptions_count,
			    defensive_action_rate,
			    lead_state_shots_conceded_increase_rate,
			    post_conceded_10m_pressure,
			    post_red_card_10m_pressure,
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
			    #{shotsConcededCount},
			    #{shotsOnTargetConcededCount},
			    #{boxTouchesConcededCount},
			    #{keeperSavesCount},
			    #{saveRate},
			    #{blockedShotsCount},
			    #{blockRate},
			    #{clearancesCount},
			    #{clearanceFrequency},
			    #{tacklesCount},
			    #{interceptionsCount},
			    #{defensiveActionRate},
			    #{leadStateShotsConcededIncreaseRate},
			    #{postConceded10mPressure},
			    #{postRedCard10mPressure},
			    #{calculatedAt},
			    #{note},
			    'SYSTEM',
			    CURRENT_TIMESTAMP,
			    'SYSTEM',
			    CURRENT_TIMESTAMP
			)
			""")
	int insert(MatchTeamDefenseStatsEntity entity);
}
