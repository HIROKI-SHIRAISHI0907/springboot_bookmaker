package dev.application.domain.repository.bm;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import dev.application.analyze.bm_m034.MatchTeamSnapshotFactEntity;

/**
 * {@link MatchTeamSnapshotFactEntity} のRepositoryです。
 */
@Mapper
public interface MatchTeamSnapshotFactRepository {

	/**
	 * 試合中スナップショットFactを登録します。
	 *
	 * @param entity 登録対象
	 * @return 登録件数
	 */
	@Insert("""
			INSERT INTO match_team_snapshot_fact (
			    match_id,
			    season,
			    country,
			    league_id,
			    league_name,
			    team_id,
			    team_name,
			    opponent_team_id,
			    opponent_team_name,
			    home_flg,
			    as_of_seconds,
			    match_time_label,
			    team_score,
			    opponent_score,
			    score_diff,
			    possession_rate,
			    shots_count,
			    shots_on_target_count,
			    shots_off_target_count,
			    blocked_shots_count,
			    big_chances_count,
			    corners_count,
			    box_touches_count,
			    passes_count,
			    long_passes_count,
			    final_third_passes_count,
			    crosses_count,
			    tackles_count,
			    clearances_count,
			    duels_won_count,
			    interceptions_count,
			    yellow_cards_count,
			    red_cards_count,
			    snapshot_recorded_at,
			    source_count,
			    data_quality_flag,
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
			    #{homeFlg},
			    #{asOfSeconds},
			    #{matchTimeLabel},
			    #{teamScore},
			    #{opponentScore},
			    #{scoreDiff},
			    #{possessionRate},
			    #{shotsCount},
			    #{shotsOnTargetCount},
			    #{shotsOffTargetCount},
			    #{blockedShotsCount},
			    #{bigChancesCount},
			    #{cornersCount},
			    #{boxTouchesCount},
			    #{passesCount},
			    #{longPassesCount},
			    #{finalThirdPassesCount},
			    #{crossesCount},
			    #{tacklesCount},
			    #{clearancesCount},
			    #{duelsWonCount},
			    #{interceptionsCount},
			    #{yellowCardsCount},
			    #{redCardsCount},
			    #{snapshotRecordedAt},
			    #{sourceCount},
			    #{dataQualityFlag},
			    #{note},
			    'SYSTEM',
			    CURRENT_TIMESTAMP,
			    'SYSTEM',
			    CURRENT_TIMESTAMP
			)
			""")
	int insert(MatchTeamSnapshotFactEntity entity);
}
