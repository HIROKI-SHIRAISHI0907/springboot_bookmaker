package dev.application.domain.repository.bm;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import dev.application.analyze.bm_m042.PreMatchSummaryProfileEntity;

/**
 * {@link PreMatchSummaryProfileEntity} のRepositoryです。
 */
@Mapper
public interface PreMatchSummaryProfileRepository {

	/**
	 * 試合前要約プロファイルを登録します。
	 *
	 * @param entity 登録対象
	 * @return 登録件数
	 */
	@Insert("""
			INSERT INTO pre_match_summary_profile (
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
			    last5_result_string,
			    last5_avg_goals,
			    last5_avg_goals_conceded,
			    first_goal_rate,
			    win_after_scoring_first_rate,
			    comeback_rate,
			    late_scoring_rate,
			    late_conceding_rate,
			    set_piece_goal_involvement_rate,
			    clean_sheet_rate,
			    both_teams_to_score_rate,
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
			    #{last5ResultString},
			    #{last5AvgGoals},
			    #{last5AvgGoalsConceded},
			    #{firstGoalRate},
			    #{winAfterScoringFirstRate},
			    #{comebackRate},
			    #{lateScoringRate},
			    #{lateConcedingRate},
			    #{setPieceGoalInvolvementRate},
			    #{cleanSheetRate},
			    #{bothTeamsToScoreRate},
			    #{calculatedAt},
			    #{note},
			    'SYSTEM',
			    CURRENT_TIMESTAMP,
			    'SYSTEM',
			    CURRENT_TIMESTAMP
			)
			""")
	int insert(PreMatchSummaryProfileEntity entity);
}
