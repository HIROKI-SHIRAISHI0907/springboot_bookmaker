package dev.web.repository.bm;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.common.entity.DataEntity;
import dev.web.api.bm_w020.SeqWithKey;

/**
 * CSV出力用のデータ取得リポジトリ.
 *
 * MyBatis(@Mapper)版を NamedParameterJdbcTemplate 版へ移植。
 *
 * @author shiraishitoshio
 */
@Repository
public class BookCsvDataRepository {

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	public BookCsvDataRepository(
			@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
	}

	/**
	 * CSV作成用検索データ
	 */
	public List<SeqWithKey> findAllSeqsWithKey() {

		String sql = """
				SELECT
				  t.dataCategory,
				  t.homeTeamName,
				  t.awayTeamName,
				  t.times,
				  t.seq
				FROM (
				  SELECT
				    d.home_team_name AS homeTeamName,
				    d.away_team_name AS awayTeamName,
				    d.times          AS times,

				    COALESCE(
				      MIN(CASE WHEN d.data_category LIKE '%ラウンド%' THEN d.seq END),
				      MIN(d.seq)
				    ) AS seq,

				    COALESCE(
				      MAX(CASE WHEN d.data_category LIKE '%ラウンド%' THEN d.data_category END),
				      MAX(d.data_category)
				    ) AS dataCategory

				  FROM data d
				  WHERE
				    EXISTS (
				      SELECT 1 FROM data x
				      WHERE x.home_team_name = d.home_team_name
				        AND x.away_team_name = d.away_team_name
				        AND x.times IN ('ハーフタイム', '第一ハーフ')
				    )
				    -- ★ここだけ変更：終了済/第二ハーフ が無くても 90分台なら対象にする
				    AND EXISTS (
				      SELECT 1 FROM data y
				      WHERE y.home_team_name = d.home_team_name
				        AND y.away_team_name = d.away_team_name
				        AND (
				          y.times IN ('終了済', '第二ハーフ')
				        )
				    )
				  GROUP BY
				    d.home_team_name, d.away_team_name, d.times
				) t
				ORDER BY t.homeTeamName, t.awayTeamName, t.seq ASC
				            """;

		return bmJdbcTemplate.query(
				sql,
				Map.of(),
				(rs, rowNum) -> {
					SeqWithKey dto = new SeqWithKey();
					dto.setDataCategory(rs.getString("dataCategory"));
					dto.setHomeTeamName(rs.getString("homeTeamName"));
					dto.setAwayTeamName(rs.getString("awayTeamName"));
					dto.setTimes(rs.getString("times"));
					dto.setSeq(rs.getInt("seq"));
					return dto;
				});
	}

	public List<DataEntity> findByData(List<Integer> seqList) {

		if (seqList == null || seqList.isEmpty()) {
			return List.of();
		}

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
				ORDER BY record_time ASC
				""";

		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("seqList", seqList);

		return bmJdbcTemplate.query(
				sql,
				params,
				(rs, rowNum) -> {
					DataEntity e = new DataEntity();

					// ※ DataEntity の型に合わせて必要なら getInt/getTimestamp へ変更してください
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

					// record_time は Timestamp/OffsetDateTime/LocalDateTime のどれかに合わせて調整
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
				});
	}
}
