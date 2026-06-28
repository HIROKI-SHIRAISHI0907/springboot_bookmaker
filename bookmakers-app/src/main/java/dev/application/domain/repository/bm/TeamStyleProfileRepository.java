package dev.application.domain.repository.bm;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import dev.application.analyze.bm_m040.TeamStyleProfileEntity;

/**
 * {@link TeamStyleProfileEntity} のRepositoryです。
 */
@Mapper
public interface TeamStyleProfileRepository {

	/**
	 * プレースタイルプロファイルを登録します。
	 *
	 * @param entity 登録対象
	 * @return 登録件数
	 */
	@Insert("""
			INSERT INTO team_style_profile (
			    match_id,
			    season,
			    country,
			    league_id,
			    league_name,
			    team_id,
			    team_name,
			    opponent_team_id,
			    opponent_team_name,
			    from_date,
			    to_date,
			    possession_rate,
			    passes_per90,
			    long_pass_rate,
			    final_third_pass_rate,
			    cross_rate,
			    shots_per_box_touch,
			    defensive_action_intensity,
			    clearance_rate,
			    duel_intensity,
			    style_cluster_id,
			    style_label,
			    style_confidence,
			    sample_match_count,
			    calculated_at,
			    style_note,
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
			    #{fromDate},
			    #{toDate},
			    #{possessionRate},
			    #{passesPer90},
			    #{longPassRate},
			    #{finalThirdPassRate},
			    #{crossRate},
			    #{shotsPerBoxTouch},
			    #{defensiveActionIntensity},
			    #{clearanceRate},
			    #{duelIntensity},
			    #{styleClusterId},
			    #{styleLabel},
			    #{styleConfidence},
			    #{sampleMatchCount},
			    #{calculatedAt},
			    #{styleNote},
			    'SYSTEM',
			    CURRENT_TIMESTAMP,
			    'SYSTEM',
			    CURRENT_TIMESTAMP
			)
			""")
	int insert(TeamStyleProfileEntity entity);
}
