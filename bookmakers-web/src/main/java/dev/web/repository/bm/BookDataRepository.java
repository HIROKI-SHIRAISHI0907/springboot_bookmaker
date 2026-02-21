package dev.web.repository.bm;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.common.entity.DataEntity;
import dev.web.api.bm_w014.EachScoreLostDataResponseDTO;

/**
 * DataEntity 用リポジトリ（手動登録/更新向け）
 *
 * - NamedParameterJdbcTemplate で data テーブルを CRUD
 * - キーは seq を想定（必要なら gameId/matchId 等に変更してください）
 */
@Repository
public class BookDataRepository {

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	public BookDataRepository(
			@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
	}

	/** seq で1件取得 */
	public Optional<DataEntity> findBySeq(String seq) {

		String sql = """
				SELECT
				  seq,
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
				  goal_time,
				  goal_team_member,
				  judge,
				  home_team_style,
				  away_team_style,
				  probablity,
				  prediction_score_time
				FROM data
				WHERE seq = :seq
				""";

		List<DataEntity> list = bmJdbcTemplate.query(
				sql,
				Map.of("seq", seq),
				(rs, rowNum) -> mapDataEntity(rs));

		return list.stream().findFirst();
	}

	/** seq 複数で取得 */
	public List<DataEntity> findBySeqList(List<Integer> seqList) {
		if (seqList == null || seqList.isEmpty())
			return List.of();

		String sql = """
				SELECT DISTINCT
				  seq,
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
				  goal_time,
				  goal_team_member,
				  judge,
				  home_team_style,
				  away_team_style,
				  probablity,
				  prediction_score_time
				FROM data
				WHERE seq IN (:seqList)
				""";

		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("seqList", seqList);

		return bmJdbcTemplate.query(sql, params, (rs, rowNum) -> mapDataEntity(rs));
	}

	/** 新規登録（手動登録） */
	public int insert(DataEntity e) {

		String sql = """
				INSERT INTO data (
				  seq,
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
				  goal_time,
				  goal_team_member,
				  judge,
				  home_team_style,
				  away_team_style,
				  probablity,
				  prediction_score_time
				) VALUES (
				  :seq,
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
				  :recordTime,
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
				  :goalTime,
				  :goalTeamMember,
				  :judge,
				  :homeTeamStyle,
				  :awayTeamStyle,
				  :probablity,
				  :predictionScoreTime
				)
				""";

		return bmJdbcTemplate.update(sql, toParams(e));
	}

	/** 更新（seq 指定） */
	public int updateBySeq(DataEntity e) {

		String sql = """
				UPDATE data SET
				  condition_result_data_seq_id = :conditionResultDataSeqId,
				  data_category               = :dataCategory,
				  times                       = :times,
				  home_rank                   = :homeRank,
				  home_team_name              = :homeTeamName,
				  home_score                  = :homeScore,
				  away_rank                   = :awayRank,
				  away_team_name              = :awayTeamName,
				  away_score                  = :awayScore,
				  home_exp                    = :homeExp,
				  away_exp                    = :awayExp,
				  home_donation               = :homeDonation,
				  away_donation               = :awayDonation,
				  home_shoot_all              = :homeShootAll,
				  away_shoot_all              = :awayShootAll,
				  home_shoot_in               = :homeShootIn,
				  away_shoot_in               = :awayShootIn,
				  home_shoot_out              = :homeShootOut,
				  away_shoot_out              = :awayShootOut,
				  home_block_shoot            = :homeBlockShoot,
				  away_block_shoot            = :awayBlockShoot,
				  home_big_chance             = :homeBigChance,
				  away_big_chance             = :awayBigChance,
				  home_corner                 = :homeCorner,
				  away_corner                 = :awayCorner,
				  home_box_shoot_in           = :homeBoxShootIn,
				  away_box_shoot_in           = :awayBoxShootIn,
				  home_box_shoot_out          = :homeBoxShootOut,
				  away_box_shoot_out          = :awayBoxShootOut,
				  home_goal_post              = :homeGoalPost,
				  away_goal_post              = :awayGoalPost,
				  home_goal_head              = :homeGoalHead,
				  away_goal_head              = :awayGoalHead,
				  home_keeper_save            = :homeKeeperSave,
				  away_keeper_save            = :awayKeeperSave,
				  home_free_kick              = :homeFreeKick,
				  away_free_kick              = :awayFreeKick,
				  home_offside                = :homeOffside,
				  away_offside                = :awayOffside,
				  home_foul                   = :homeFoul,
				  away_foul                   = :awayFoul,
				  home_yellow_card            = :homeYellowCard,
				  away_yellow_card            = :awayYellowCard,
				  home_red_card               = :homeRedCard,
				  away_red_card               = :awayRedCard,
				  home_slow_in                = :homeSlowIn,
				  away_slow_in                = :awaySlowIn,
				  home_box_touch              = :homeBoxTouch,
				  away_box_touch              = :awayBoxTouch,
				  home_pass_count             = :homePassCount,
				  away_pass_count             = :awayPassCount,
				  home_long_pass_count        = :homeLongPassCount,
				  away_long_pass_count        = :awayLongPassCount,
				  home_final_third_pass_count = :homeFinalThirdPassCount,
				  away_final_third_pass_count = :awayFinalThirdPassCount,
				  home_cross_count            = :homeCrossCount,
				  away_cross_count            = :awayCrossCount,
				  home_tackle_count           = :homeTackleCount,
				  away_tackle_count           = :awayTackleCount,
				  home_clear_count            = :homeClearCount,
				  away_clear_count            = :awayClearCount,
				  home_duel_count             = :homeDuelCount,
				  away_duel_count             = :awayDuelCount,
				  home_intercept_count        = :homeInterceptCount,
				  away_intercept_count        = :awayInterceptCount,
				  record_time                 = :recordTime,
				  weather                     = :weather,
				  temparature                 = :temparature,
				  humid                       = :humid,
				  judge_member                = :judgeMember,
				  home_manager                = :homeManager,
				  away_manager                = :awayManager,
				  home_formation              = :homeFormation,
				  away_formation              = :awayFormation,
				  studium                     = :studium,
				  capacity                    = :capacity,
				  audience                    = :audience,
				  home_max_getting_scorer     = :homeMaxGettingScorer,
				  away_max_getting_scorer     = :awayMaxGettingScorer,
				  home_max_getting_scorer_game_situation = :homeMaxGettingScorerGameSituation,
				  away_max_getting_scorer_game_situation = :awayMaxGettingScorerGameSituation,
				  home_team_home_score        = :homeTeamHomeScore,
				  home_team_home_lost         = :homeTeamHomeLost,
				  away_team_home_score        = :awayTeamHomeScore,
				  away_team_home_lost         = :awayTeamHomeLost,
				  home_team_away_score        = :homeTeamAwayScore,
				  home_team_away_lost         = :homeTeamAwayLost,
				  away_team_away_score        = :awayTeamAwayScore,
				  away_team_away_lost         = :awayTeamAwayLost,
				  notice_flg                  = :noticeFlg,
				  goal_time                   = :goalTime,
				  goal_team_member            = :goalTeamMember,
				  judge                       = :judge,
				  home_team_style             = :homeTeamStyle,
				  away_team_style             = :awayTeamStyle,
				  probablity                  = :probablity,
				  prediction_score_time       = :predictionScoreTime
				WHERE seq = :seq
				""";

		return bmJdbcTemplate.update(sql, toParams(e));
	}

	/** 物理削除（必要なら） */
	public int deleteBySeq(String seq) {
		String sql = "DELETE FROM data WHERE seq = :seq";
		return bmJdbcTemplate.update(sql, Map.of("seq", seq));
	}

	/** ====== row mapper ====== */
	private static DataEntity mapDataEntity(java.sql.ResultSet rs) throws java.sql.SQLException {
		DataEntity e = new DataEntity();

		e.setSeq(rs.getString("seq"));
		e.setConditionResultDataSeqId(rs.getString("condition_result_data_seq_id"));
		e.setDataCategory(rs.getString("data_category"));
		e.setTimes(rs.getString("times"));
		e.setHomeRank(rs.getString("home_rank"));
		e.setHomeTeamName(rs.getString("home_team_name"));
		e.setHomeScore(rs.getString("home_score"));
		e.setAwayRank(rs.getString("away_rank"));
		e.setAwayTeamName(rs.getString("away_team_name"));
		e.setAwayScore(rs.getString("away_score"));
		e.setHomeExp(rs.getString("home_exp"));
		e.setAwayExp(rs.getString("away_exp"));
		e.setHomeDonation(rs.getString("home_donation"));
		e.setAwayDonation(rs.getString("away_donation"));
		e.setHomeShootAll(rs.getString("home_shoot_all"));
		e.setAwayShootAll(rs.getString("away_shoot_all"));
		e.setHomeShootIn(rs.getString("home_shoot_in"));
		e.setAwayShootIn(rs.getString("away_shoot_in"));
		e.setHomeShootOut(rs.getString("home_shoot_out"));
		e.setAwayShootOut(rs.getString("away_shoot_out"));
		e.setHomeBlockShoot(rs.getString("home_block_shoot"));
		e.setAwayBlockShoot(rs.getString("away_block_shoot"));
		e.setHomeBigChance(rs.getString("home_big_chance"));
		e.setAwayBigChance(rs.getString("away_big_chance"));
		e.setHomeCorner(rs.getString("home_corner"));
		e.setAwayCorner(rs.getString("away_corner"));
		e.setHomeBoxShootIn(rs.getString("home_box_shoot_in"));
		e.setAwayBoxShootIn(rs.getString("away_box_shoot_in"));
		e.setHomeBoxShootOut(rs.getString("home_box_shoot_out"));
		e.setAwayBoxShootOut(rs.getString("away_box_shoot_out"));
		e.setHomeGoalPost(rs.getString("home_goal_post"));
		e.setAwayGoalPost(rs.getString("away_goal_post"));
		e.setHomeGoalHead(rs.getString("home_goal_head"));
		e.setAwayGoalHead(rs.getString("away_goal_head"));
		e.setHomeKeeperSave(rs.getString("home_keeper_save"));
		e.setAwayKeeperSave(rs.getString("away_keeper_save"));
		e.setHomeFreeKick(rs.getString("home_free_kick"));
		e.setAwayFreeKick(rs.getString("away_free_kick"));
		e.setHomeOffside(rs.getString("home_offside"));
		e.setAwayOffside(rs.getString("away_offside"));
		e.setHomeFoul(rs.getString("home_foul"));
		e.setAwayFoul(rs.getString("away_foul"));
		e.setHomeYellowCard(rs.getString("home_yellow_card"));
		e.setAwayYellowCard(rs.getString("away_yellow_card"));
		e.setHomeRedCard(rs.getString("home_red_card"));
		e.setAwayRedCard(rs.getString("away_red_card"));
		e.setHomeSlowIn(rs.getString("home_slow_in"));
		e.setAwaySlowIn(rs.getString("away_slow_in"));
		e.setHomeBoxTouch(rs.getString("home_box_touch"));
		e.setAwayBoxTouch(rs.getString("away_box_touch"));
		e.setHomePassCount(rs.getString("home_pass_count"));
		e.setAwayPassCount(rs.getString("away_pass_count"));
		e.setHomeLongPassCount(rs.getString("home_long_pass_count"));
		e.setAwayLongPassCount(rs.getString("away_long_pass_count"));
		e.setHomeFinalThirdPassCount(rs.getString("home_final_third_pass_count"));
		e.setAwayFinalThirdPassCount(rs.getString("away_final_third_pass_count"));
		e.setHomeCrossCount(rs.getString("home_cross_count"));
		e.setAwayCrossCount(rs.getString("away_cross_count"));
		e.setHomeTackleCount(rs.getString("home_tackle_count"));
		e.setAwayTackleCount(rs.getString("away_tackle_count"));
		e.setHomeClearCount(rs.getString("home_clear_count"));
		e.setAwayClearCount(rs.getString("away_clear_count"));
		e.setHomeDuelCount(rs.getString("home_duel_count"));
		e.setAwayDuelCount(rs.getString("away_duel_count"));
		e.setHomeInterceptCount(rs.getString("home_intercept_count"));
		e.setAwayInterceptCount(rs.getString("away_intercept_count"));

		e.setRecordTime(rs.getString("record_time"));
		e.setWeather(rs.getString("weather"));
		e.setTemparature(rs.getString("temparature"));
		e.setHumid(rs.getString("humid"));
		e.setJudgeMember(rs.getString("judge_member"));
		e.setHomeManager(rs.getString("home_manager"));
		e.setAwayManager(rs.getString("away_manager"));
		e.setHomeFormation(rs.getString("home_formation"));
		e.setAwayFormation(rs.getString("away_formation"));
		e.setStudium(rs.getString("studium"));
		e.setCapacity(rs.getString("capacity"));
		e.setAudience(rs.getString("audience"));
		e.setHomeMaxGettingScorer(rs.getString("home_max_getting_scorer"));
		e.setAwayMaxGettingScorer(rs.getString("away_max_getting_scorer"));
		e.setHomeMaxGettingScorerGameSituation(rs.getString("home_max_getting_scorer_game_situation"));
		e.setAwayMaxGettingScorerGameSituation(rs.getString("away_max_getting_scorer_game_situation"));
		e.setHomeTeamHomeScore(rs.getString("home_team_home_score"));
		e.setHomeTeamHomeLost(rs.getString("home_team_home_lost"));
		e.setAwayTeamHomeScore(rs.getString("away_team_home_score"));
		e.setAwayTeamHomeLost(rs.getString("away_team_home_lost"));
		e.setHomeTeamAwayScore(rs.getString("home_team_away_score"));
		e.setHomeTeamAwayLost(rs.getString("home_team_away_lost"));
		e.setAwayTeamAwayScore(rs.getString("away_team_away_score"));
		e.setAwayTeamAwayLost(rs.getString("away_team_away_lost"));
		e.setNoticeFlg(rs.getString("notice_flg"));
		e.setGoalTime(rs.getString("goal_time"));
		e.setGoalTeamMember(rs.getString("goal_team_member"));
		e.setJudge(rs.getString("judge"));
		e.setHomeTeamStyle(rs.getString("home_team_style"));
		e.setAwayTeamStyle(rs.getString("away_team_style"));
		e.setProbablity(rs.getString("probablity"));
		e.setPredictionScoreTime(rs.getString("prediction_score_time"));

		return e;
	}

	/** ====== params builder ====== */
	private static MapSqlParameterSource toParams(DataEntity e) {
		MapSqlParameterSource p = new MapSqlParameterSource();

		p.addValue("seq", e.getSeq());
		p.addValue("conditionResultDataSeqId", e.getConditionResultDataSeqId());
		p.addValue("dataCategory", e.getDataCategory());
		p.addValue("times", e.getTimes());
		p.addValue("homeRank", e.getHomeRank());
		p.addValue("homeTeamName", e.getHomeTeamName());
		p.addValue("homeScore", e.getHomeScore());
		p.addValue("awayRank", e.getAwayRank());
		p.addValue("awayTeamName", e.getAwayTeamName());
		p.addValue("awayScore", e.getAwayScore());
		p.addValue("homeExp", e.getHomeExp());
		p.addValue("awayExp", e.getAwayExp());
		p.addValue("homeDonation", e.getHomeDonation());
		p.addValue("awayDonation", e.getAwayDonation());
		p.addValue("homeShootAll", e.getHomeShootAll());
		p.addValue("awayShootAll", e.getAwayShootAll());
		p.addValue("homeShootIn", e.getHomeShootIn());
		p.addValue("awayShootIn", e.getAwayShootIn());
		p.addValue("homeShootOut", e.getHomeShootOut());
		p.addValue("awayShootOut", e.getAwayShootOut());
		p.addValue("homeBlockShoot", e.getHomeBlockShoot());
		p.addValue("awayBlockShoot", e.getAwayBlockShoot());
		p.addValue("homeBigChance", e.getHomeBigChance());
		p.addValue("awayBigChance", e.getAwayBigChance());
		p.addValue("homeCorner", e.getHomeCorner());
		p.addValue("awayCorner", e.getAwayCorner());
		p.addValue("homeBoxShootIn", e.getHomeBoxShootIn());
		p.addValue("awayBoxShootIn", e.getAwayBoxShootIn());
		p.addValue("homeBoxShootOut", e.getHomeBoxShootOut());
		p.addValue("awayBoxShootOut", e.getAwayBoxShootOut());
		p.addValue("homeGoalPost", e.getHomeGoalPost());
		p.addValue("awayGoalPost", e.getAwayGoalPost());
		p.addValue("homeGoalHead", e.getHomeGoalHead());
		p.addValue("awayGoalHead", e.getAwayGoalHead());
		p.addValue("homeKeeperSave", e.getHomeKeeperSave());
		p.addValue("awayKeeperSave", e.getAwayKeeperSave());
		p.addValue("homeFreeKick", e.getHomeFreeKick());
		p.addValue("awayFreeKick", e.getAwayFreeKick());
		p.addValue("homeOffside", e.getHomeOffside());
		p.addValue("awayOffside", e.getAwayOffside());
		p.addValue("homeFoul", e.getHomeFoul());
		p.addValue("awayFoul", e.getAwayFoul());
		p.addValue("homeYellowCard", e.getHomeYellowCard());
		p.addValue("awayYellowCard", e.getAwayYellowCard());
		p.addValue("homeRedCard", e.getHomeRedCard());
		p.addValue("awayRedCard", e.getAwayRedCard());
		p.addValue("homeSlowIn", e.getHomeSlowIn());
		p.addValue("awaySlowIn", e.getAwaySlowIn());
		p.addValue("homeBoxTouch", e.getHomeBoxTouch());
		p.addValue("awayBoxTouch", e.getAwayBoxTouch());
		p.addValue("homePassCount", e.getHomePassCount());
		p.addValue("awayPassCount", e.getAwayPassCount());
		p.addValue("homeLongPassCount", e.getHomeLongPassCount());
		p.addValue("awayLongPassCount", e.getAwayLongPassCount());
		p.addValue("homeFinalThirdPassCount", e.getHomeFinalThirdPassCount());
		p.addValue("awayFinalThirdPassCount", e.getAwayFinalThirdPassCount());
		p.addValue("homeCrossCount", e.getHomeCrossCount());
		p.addValue("awayCrossCount", e.getAwayCrossCount());
		p.addValue("homeTackleCount", e.getHomeTackleCount());
		p.addValue("awayTackleCount", e.getAwayTackleCount());
		p.addValue("homeClearCount", e.getHomeClearCount());
		p.addValue("awayClearCount", e.getAwayClearCount());
		p.addValue("homeDuelCount", e.getHomeDuelCount());
		p.addValue("awayDuelCount", e.getAwayDuelCount());
		p.addValue("homeInterceptCount", e.getHomeInterceptCount());
		p.addValue("awayInterceptCount", e.getAwayInterceptCount());
		p.addValue("recordTime", e.getRecordTime());
		p.addValue("weather", e.getWeather());
		p.addValue("temparature", e.getTemparature());
		p.addValue("humid", e.getHumid());
		p.addValue("judgeMember", e.getJudgeMember());
		p.addValue("homeManager", e.getHomeManager());
		p.addValue("awayManager", e.getAwayManager());
		p.addValue("homeFormation", e.getHomeFormation());
		p.addValue("awayFormation", e.getAwayFormation());
		p.addValue("studium", e.getStudium());
		p.addValue("capacity", e.getCapacity());
		p.addValue("audience", e.getAudience());
		p.addValue("homeMaxGettingScorer", e.getHomeMaxGettingScorer());
		p.addValue("awayMaxGettingScorer", e.getAwayMaxGettingScorer());
		p.addValue("homeMaxGettingScorerGameSituation", e.getHomeMaxGettingScorerGameSituation());
		p.addValue("awayMaxGettingScorerGameSituation", e.getAwayMaxGettingScorerGameSituation());
		p.addValue("homeTeamHomeScore", e.getHomeTeamHomeScore());
		p.addValue("homeTeamHomeLost", e.getHomeTeamHomeLost());
		p.addValue("awayTeamHomeScore", e.getAwayTeamHomeScore());
		p.addValue("awayTeamHomeLost", e.getAwayTeamHomeLost());
		p.addValue("homeTeamAwayScore", e.getHomeTeamAwayScore());
		p.addValue("homeTeamAwayLost", e.getHomeTeamAwayLost());
		p.addValue("awayTeamAwayScore", e.getAwayTeamAwayScore());
		p.addValue("awayTeamAwayLost", e.getAwayTeamAwayLost());
		p.addValue("noticeFlg", e.getNoticeFlg());
		p.addValue("goalTime", e.getGoalTime());
		p.addValue("goalTeamMember", e.getGoalTeamMember());
		p.addValue("judge", e.getJudge());
		p.addValue("homeTeamStyle", e.getHomeTeamStyle());
		p.addValue("awayTeamStyle", e.getAwayTeamStyle());
		p.addValue("probablity", e.getProbablity());
		p.addValue("predictionScoreTime", e.getPredictionScoreTime());

		return p;
	}

	// ========= data =========
	public List<DataIngestRow> findDataByRegisterTime(OffsetDateTime from, OffsetDateTime to) {
		String sql = """
				    SELECT
				      seq,
				      data_category,
				      times,
				      home_team_name,
				      away_team_name,
				      record_time,
				      register_time,
				      update_time
				    FROM data
				    WHERE register_time >= :from
				      AND register_time <  :to
				    ORDER BY register_time DESC
				""";

		var params = new MapSqlParameterSource()
				.addValue("from", from)
				.addValue("to", to);

		return bmJdbcTemplate.query(sql, params, (rs, rowNum) -> {
			DataIngestRow r = new DataIngestRow();
			r.seq = rs.getString("seq");
			r.dataCategory = rs.getString("data_category");
			r.times = rs.getString("times");
			r.homeTeamName = rs.getString("home_team_name");
			r.awayTeamName = rs.getString("away_team_name");
			r.recordTime = rs.getString("record_time");

			Timestamp rt = rs.getTimestamp("register_time");
			r.registerTime = (rt == null) ? null : rt.toInstant().atOffset(ZoneOffset.UTC);

			Timestamp ut = rs.getTimestamp("update_time");
			r.updateTime = (ut == null) ? null : ut.toInstant().atOffset(ZoneOffset.UTC);

			return r;
		});
	}

	public static class DataIngestRow {
		public String seq;
		public String dataCategory;
		public String times;
		public String homeTeamName;
		public String awayTeamName;
		public String recordTime;
		public OffsetDateTime registerTime;
		public OffsetDateTime updateTime;
	}

	public Optional<EachScoreLostDataResponseDTO> findEachScoreLoseMatchFinishedByRoundAndTeams(
	        String country,
	        String league,
	        String homeTeamName,
	        String awayTeamName,
	        int roundNo
	) {
	    String likeCond = country + ": " + league + "%";

	    String sql = """
	        SELECT DISTINCT ON (d.game_link)
	          d.seq,
	          d.data_category,
	          d.home_team_name,
	          d.away_team_name,
	          d.home_score,
	          d.away_score,
	          NULLIF(TRIM(d.game_link), '') AS link,
	          d.record_time,
	          CASE
	            WHEN regexp_match(d.data_category, '(ラウンド|Round)\\s*([0-9]+)') IS NULL THEN NULL
	            ELSE CAST((regexp_match(d.data_category, '(ラウンド|Round)\\s*([0-9]+)'))[2] AS INT)
	          END AS round_no
	        FROM public.data d
	        WHERE d.times = '終了済'
	          AND d.data_category LIKE :likeCond
	          AND d.home_team_name = :homeTeam
	          AND d.away_team_name = :awayTeam
	          AND (
	            CASE
	              WHEN regexp_match(d.data_category, '(ラウンド|Round)\\s*([0-9]+)') IS NULL THEN NULL
	              ELSE CAST((regexp_match(d.data_category, '(ラウンド|Round)\\s*([0-9]+)'))[2] AS INT)
	            END
	          ) = :roundNo
	          AND d.game_link IS NOT NULL
	        ORDER BY d.game_link, d.record_time DESC
	        LIMIT 1
	    """;

	    var params = new MapSqlParameterSource()
	            .addValue("likeCond", likeCond)
	            .addValue("homeTeam", homeTeamName)
	            .addValue("awayTeam", awayTeamName)
	            .addValue("roundNo", roundNo);

	    List<EachScoreLostDataResponseDTO> list = bmJdbcTemplate.query(sql, params, (rs, rowNum) -> {
	        var dto = new EachScoreLostDataResponseDTO();
	        dto.setSeq(rs.getLong("seq"));
	        dto.setDataCategory(rs.getString("data_category"));

	        String r = rs.getString("round_no");
	        dto.setRoundNo(rs.wasNull() ? null : r);

	        Timestamp rt = rs.getTimestamp("record_time");
	        dto.setRecordTime(rt == null ? null : rt.toInstant().atOffset(ZoneOffset.UTC).toString());

	        dto.setHomeTeamName(rs.getString("home_team_name"));
	        dto.setAwayTeamName(rs.getString("away_team_name"));

	        String hs = rs.getString("home_score");
	        String as = rs.getString("away_score");
	        dto.setHomeScore(hs == null || hs.isBlank() ? null : Integer.valueOf(hs.trim()));
	        dto.setAwayScore(as == null || as.isBlank() ? null : Integer.valueOf(as.trim()));

	        dto.setLink(rs.getString("link"));
	        dto.setStatus("FINISHED");
	        return dto;
	    });

	    return list.stream().findFirst();
	}

	/** dataを全件 DataEntity で取得（重いので注意） */
	public List<DataEntity> findAll() {
	    String sql = """
	        SELECT
	          seq,
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
	          goal_time,
	          goal_team_member,
	          judge,
	          home_team_style,
	          away_team_style,
	          probablity,
	          prediction_score_time
	        FROM data
	        ORDER BY seq DESC
	        """;

	    return bmJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, rowNum) -> mapDataEntity(rs));
	}

}
