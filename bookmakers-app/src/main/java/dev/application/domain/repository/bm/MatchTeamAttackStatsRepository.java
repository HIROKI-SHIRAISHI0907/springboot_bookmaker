package dev.application.domain.repository.bm;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import dev.application.analyze.bm_m035.MatchTeamAttackStatsEntity;

/**
 * {@link MatchTeamAttackStatsEntity} のRepositoryです。
 */
@Mapper
public interface MatchTeamAttackStatsRepository {

	/**
	 * 攻撃生成力統計を登録します。
	 *
	 * @param entity 登録対象
	 * @return 登録件数
	 */
	@Insert("""
			INSERT INTO match_team_attack_stats (
			    match_id,
			    season,
			    country,
			    league_id,
			    league_name,
			    team_id,
			    team_name,
			    opponent_team_id,
			    opponent_team_name,
			    actual_minutes,
			    shots_count,
			    shots_per90,
			    shots_on_target_count,
			    shots_on_target_per90,
			    box_touches_count,
			    box_touches_per90,
			    corners_count,
			    corners_per90,
			    final_third_passes_count,
			    final_third_passes_per90,
			    crosses_count,
			    crosses_per90,
			    attack_volume_index,
			    calculated_at,
			    source_count,
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
			    #{actualMinutes},
			    #{shotsCount},
			    #{shotsPer90},
			    #{shotsOnTargetCount},
			    #{shotsOnTargetPer90},
			    #{boxTouchesCount},
			    #{boxTouchesPer90},
			    #{cornersCount},
			    #{cornersPer90},
			    #{finalThirdPassesCount},
			    #{finalThirdPassesPer90},
			    #{crossesCount},
			    #{crossesPer90},
			    #{attackVolumeIndex},
			    #{calculatedAt},
			    #{sourceCount},
			    #{note},
			    'SYSTEM',
			    CURRENT_TIMESTAMP,
			    'SYSTEM',
			    CURRENT_TIMESTAMP
			)
			""")
	int insert(MatchTeamAttackStatsEntity entity);
}
