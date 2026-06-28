package dev.application.domain.repository.bm;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import dev.application.analyze.bm_m038.MatchTeamTimebandStatsEntity;

/**
 * {@link MatchTeamTimebandStatsEntity} のRepositoryです。
 */
@Mapper
public interface MatchTeamTimebandStatsRepository {

	/**
	 * 時間帯別統計を登録します。
	 *
	 * @param entity 登録対象
	 * @return 登録件数
	 */
	@Insert("""
			INSERT INTO match_team_timeband_stats (
			    match_id,
			    season,
			    country,
			    league_id,
			    league_name,
			    team_id,
			    team_name,
			    opponent_team_id,
			    opponent_team_name,
			    time_band,
			    goals_count,
			    goals_conceded_count,
			    shots_count,
			    shots_on_target_count,
			    box_touches_count,
			    corners_count,
			    cards_count,
			    scoring_rate,
			    conceding_rate,
			    first_conceded_time_seconds,
			    equal_state_scoring_time_seconds,
			    lead_state_conceded_time_seconds,
			    late_conceded_time_seconds,
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
			    #{timeBand},
			    #{goalsCount},
			    #{goalsConcededCount},
			    #{shotsCount},
			    #{shotsOnTargetCount},
			    #{boxTouchesCount},
			    #{cornersCount},
			    #{cardsCount},
			    #{scoringRate},
			    #{concedingRate},
			    #{firstConcededTimeSeconds},
			    #{equalStateScoringTimeSeconds},
			    #{leadStateConcededTimeSeconds},
			    #{lateConcededTimeSeconds},
			    #{calculatedAt},
			    'SYSTEM',
			    CURRENT_TIMESTAMP,
			    'SYSTEM',
			    CURRENT_TIMESTAMP
			)
			""")
	int insert(MatchTeamTimebandStatsEntity entity);
}
