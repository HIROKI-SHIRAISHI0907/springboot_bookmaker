package dev.web.repository.bm;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import dev.common.entity.DataEntity;

/**
 * リアルタイムデータ CSV アップロード（static_data に直接登録。2026-11-28 追加）
 * <p>登録の列・値の入れ方は B008（bookmakers-batch OriginBookDataRepository#insert）と同じ。
 * register_id / update_id はアップロードしたユーザー。</p>
 * @author shiraishitoshio
 */
@Repository
public class RealtimeCsvInsertRepository {

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	/** bm DB 用のトランザクション */
	private final TransactionTemplate transactionTemplate;

	public RealtimeCsvInsertRepository(@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
		this.transactionTemplate = new TransactionTemplate(
				new DataSourceTransactionManager(bmJdbcTemplate.getJdbcTemplate().getDataSource()));
	}

	/** 1 つのトランザクションで実行する（例外が出たら全部戻す） */
	public void inTransaction(Runnable work) {
		transactionTemplate.executeWithoutResult(status -> work.run());
	}

	/** 同じ行（データカテゴリ・試合時間・チーム・試合ID が同じ）がすでにあるか（B008 と同じ判定） */
	public boolean exists(DataEntity e) {
		String sql = """
				SELECT COUNT(*) FROM static_data
				WHERE data_category = :dataCategory
				  AND times = :times
				  AND home_team_name = :homeTeamName
				  AND away_team_name = :awayTeamName
				  AND match_id = :matchId
				""";
		Integer n = bmJdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
				.addValue("dataCategory", e.getDataCategory())
				.addValue("times", e.getTimes())
				.addValue("homeTeamName", e.getHomeTeamName())
				.addValue("awayTeamName", e.getAwayTeamName())
				.addValue("matchId", e.getMatchId()), Integer.class);
		return n != null && n > 0;
	}

	/** この試合ID の最後の seq_key（無ければ null。連番の数値の大きい順） */
	public String findLastSeqKey(String matchId) {
		String sql = """
				SELECT seq_key FROM static_data
				WHERE match_id = :matchId OR seq_key LIKE :prefix
				ORDER BY NULLIF(SUBSTRING(seq_key FROM '([0-9]+)$'), '')::BIGINT DESC NULLS LAST
				LIMIT 1
				""";
		List<String> l = bmJdbcTemplate.queryForList(sql, new MapSqlParameterSource()
				.addValue("matchId", matchId).addValue("prefix", matchId + "-%"), String.class);
		return l.isEmpty() ? null : l.get(0);
	}

	/** 登録（1 行） */
	public int insert(DataEntity e, String operatorId) {
		String sql = """
				INSERT INTO static_data (
				seq_key,
				condition_result_data_seq_id,
				data_category,
				times,
				home_rank,
				home_team_name,
				home_score,
				away_rank,
				away_team_name,
				away_score,
				home_exp,
				away_exp,
				home_in_goal_exp,
				away_in_goal_exp,
				home_donation,
				away_donation,
				home_shoot_all,
				away_shoot_all,
				home_shoot_in,
				away_shoot_in,
				home_shoot_out,
				away_shoot_out,
				home_block_shoot,
				away_block_shoot,
				home_big_chance,
				away_big_chance,
				home_corner,
				away_corner,
				home_box_shoot_in,
				away_box_shoot_in,
				home_box_shoot_out,
				away_box_shoot_out,
				home_goal_post,
				away_goal_post,
				home_goal_head,
				away_goal_head,
				home_keeper_save,
				away_keeper_save,
				home_free_kick,
				away_free_kick,
				home_offside,
				away_offside,
				home_foul,
				away_foul,
				home_yellow_card,
				away_yellow_card,
				home_red_card,
				away_red_card,
				home_slow_in,
				away_slow_in,
				home_box_touch,
				away_box_touch,
				home_pass_count,
				away_pass_count,
				home_long_pass_count,
				away_long_pass_count,
				home_final_third_pass_count,
				away_final_third_pass_count,
				home_cross_count,
				away_cross_count,
				home_tackle_count,
				away_tackle_count,
				home_clear_count,
				away_clear_count,
				home_duel_count,
				away_duel_count,
				home_intercept_count,
				away_intercept_count,
				record_time,
				weather,
				temparature,
				humid,
				judge_member,
				home_manager,
				away_manager,
				home_formation,
				away_formation,
				studium,
				capacity,
				audience,
				location,
				home_max_getting_scorer,
				away_max_getting_scorer,
				home_max_getting_scorer_game_situation,
				away_max_getting_scorer_game_situation,
				home_team_home_score,
				home_team_home_lost,
				away_team_home_score,
				away_team_home_lost,
				home_team_away_score,
				home_team_away_lost,
				away_team_away_score,
				away_team_away_lost,
				notice_flg,
				game_link,
				goal_time,
				goal_team_member,
				judge,
				home_team_style,
				away_team_style,
				probablity,
				prediction_score_time,
				game_id,
				match_id,
				time_sort_seconds,
				add_manual_flg,
				register_id,
				register_time,
				update_id,
				update_time
				) VALUES (
				:seqKey,
				:conditionResultDataSeqId,
				:dataCategory,
				:times,
				:homeRank,
				:homeTeamName,
				:homeScore,
				:awayRank,
				:awayTeamName,
				:awayScore,
				:homeExp,
				:awayExp,
				:homeInGoalExp,
				:awayInGoalExp,
				:homeDonation,
				:awayDonation,
				:homeShootAll,
				:awayShootAll,
				:homeShootIn,
				:awayShootIn,
				:homeShootOut,
				:awayShootOut,
				:homeBlockShoot,
				:awayBlockShoot,
				:homeBigChance,
				:awayBigChance,
				:homeCorner,
				:awayCorner,
				:homeBoxShootIn,
				:awayBoxShootIn,
				:homeBoxShootOut,
				:awayBoxShootOut,
				:homeGoalPost,
				:awayGoalPost,
				:homeGoalHead,
				:awayGoalHead,
				:homeKeeperSave,
				:awayKeeperSave,
				:homeFreeKick,
				:awayFreeKick,
				:homeOffside,
				:awayOffside,
				:homeFoul,
				:awayFoul,
				:homeYellowCard,
				:awayYellowCard,
				:homeRedCard,
				:awayRedCard,
				:homeSlowIn,
				:awaySlowIn,
				:homeBoxTouch,
				:awayBoxTouch,
				:homePassCount,
				:awayPassCount,
				:homeLongPassCount,
				:awayLongPassCount,
				:homeFinalThirdPassCount,
				:awayFinalThirdPassCount,
				:homeCrossCount,
				:awayCrossCount,
				:homeTackleCount,
				:awayTackleCount,
				:homeClearCount,
				:awayClearCount,
				:homeDuelCount,
				:awayDuelCount,
				:homeInterceptCount,
				:awayInterceptCount,
				CAST(NULLIF(:recordTime, '') AS timestamp),
				:weather,
				:temparature,
				:humid,
				:judgeMember,
				:homeManager,
				:awayManager,
				:homeFormation,
				:awayFormation,
				:studium,
				:capacity,
				:audience,
				:location,
				:homeMaxGettingScorer,
				:awayMaxGettingScorer,
				:homeMaxGettingScorerGameSituation,
				:awayMaxGettingScorerGameSituation,
				:homeTeamHomeScore,
				:homeTeamHomeLost,
				:awayTeamHomeScore,
				:awayTeamHomeLost,
				:homeTeamAwayScore,
				:homeTeamAwayLost,
				:awayTeamAwayScore,
				:awayTeamAwayLost,
				:noticeFlg,
				:gameLink,
				:goalTime,
				:goalTeamMember,
				:judge,
				:homeTeamStyle,
				:awayTeamStyle,
				:probablity,
				:predictionScoreTime,
				:gameId,
				:matchId,
				:timeSortSeconds,
				:addManualFlg,
				:operatorId,
				CURRENT_TIMESTAMP,
				:operatorId,
				CURRENT_TIMESTAMP
				)
				""";
		BeanPropertySqlParameterSource bean = new BeanPropertySqlParameterSource(e);
		MapSqlParameterSource p = new MapSqlParameterSource();
		for (String name : bean.getReadablePropertyNames()) {
			if (!"class".equals(name)) {
				p.addValue(name, bean.getValue(name));
			}
		}
		p.addValue("operatorId", operatorId);
		return bmJdbcTemplate.update(sql, p);
	}
}
