package dev.application.domain.repository;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.application.analyze.bm_m003.TeamMonthlyScoreSummaryEntity;

/**
 * チームの月別スコアデータを team_statistics_data テーブルに登録するためのRepository
 */
@Mapper
public interface TeamMonthlyScoreSummaryRepository {

	/**
	 * チームごとの月別スコア集計を登録する
	 * @param entity 月別スコア集計エンティティ
	 * @return 登録件数（通常は1）
	 */
	@Insert("""
			    INSERT INTO team_statistics_data (
			        country,
			        league,
			        team_name,
			        HA,
			        year,
			        jar_sum_score,
			        feb_sum_score,
			        mar_sum_score,
			        apr_sum_score,
			        may_sum_score,
			        jun_sum_score,
			        jul_sum_score,
			        aug_sum_score,
			        sep_sum_score,
			        oct_sum_score,
			        nov_sum_score,
			        dec_sum_score,
			        register_id,
			        register_time,
			        update_id,
			        update_time
			    ) VALUES (
			        #{country},
			        #{league},
			        #{teamName},
			        #{ha},
			        #{year},
			        #{januaryScoreSumCount},
			        #{februaryScoreSumCount},
			        #{marchScoreSumCount},
			        #{aprilScoreSumCount},
			        #{mayScoreSumCount},
			        #{juneScoreSumCount},
			        #{julyScoreSumCount},
			        #{augustScoreSumCount},
			        #{septemberScoreSumCount},
			        #{octoberScoreSumCount},
			        #{novemberScoreSumCount},
			        #{decemberScoreSumCount},
			        #{registerId},
			        #{registerTime},
			        #{updateId},
			        #{updateTime}
			    )
			""")
	int insertTeamMonthlyScore(TeamMonthlyScoreSummaryEntity entity);

	/**
	 * チームごとの月別スコア集計を取得する
	 * @param entity 月別スコア集計エンティティ
	 * @return 登録件数（通常は1）
	 */
	@Select("""
			    SELECT
			    	seq,
			        country,
			        league,
			        team_name,
			        ha,
			        year,
			        jar_sum_score,
			        feb_sum_score,
			        mar_sum_score,
			        apr_sum_score,
			        may_sum_score,
			        jun_sum_score,
			        jul_sum_score,
			        aug_sum_score,
			        sep_sum_score,
			        oct_sum_score,
			        nov_sum_score,
			        dec_sum_score
			    FROM
					team_statistics_data
				WHERE
			        country = #{country} and
			        league = #{league} and
			        team_name = #{teamName} and
			        ha = #{ha} and
			        year = #{year};
			""")
	List<TeamMonthlyScoreSummaryEntity> findByCount(TeamMonthlyScoreSummaryEntity entity);

	/**
	 * チームごとの月別スコア集計を取得する
	 * @param entity 月別スコア集計エンティティ
	 * @return 登録件数（通常は1）
	 */
	@Update("""
			    UPDATE team_statistics_data
			    SET
			        january_score_sum = #{januaryScoreSumCount},
					february_score_sum = #{februaryScoreSumCount},
					march_score_sum = #{marchScoreSumCount},
					april_score_sum = #{aprilScoreSumCount},
					may_score_sum = #{mayScoreSumCount},
					june_score_sum = #{juneScoreSumCount},
					july_score_sum = #{julyScoreSumCount},
					august_score_sum = #{augustScoreSumCount},
					september_score_sum = #{septemberScoreSumCount},
					october_score_sum = #{octoberScoreSumCount},
					november_score_sum = #{novemberScoreSumCount},
					december_score_sum = #{decemberScoreSumCount}
			    FROM
					team_statistics_data
				WHERE
			        seq = #{seq};
			""")
	int update(TeamMonthlyScoreSummaryEntity entity);
}
