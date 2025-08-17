package dev.application.domain.repository;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Lang;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;

import dev.application.analyze.bm_m003.TeamMonthlyScoreSummaryEntity;

/**
 * チームの月別スコアデータを team_statistics_data テーブルに登録するためのRepository
 */
@Mapper
public interface TeamMonthlyScoreSummaryRepository {

    /**
     * 登録（列名は jar/feb/..._sum_score に統一）
     */
    @Lang(XMLLanguageDriver.class)
    @Insert("""
        <script>
        INSERT INTO team_statistics_data (
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
        </script>
        """)
    int insertTeamMonthlyScore(TeamMonthlyScoreSummaryEntity entity);

    /**
     * 取得（エンティティのプロパティに AS で明示マッピング）
     */
    @Lang(XMLLanguageDriver.class)
    @Select("""
        <script>
        SELECT
          seq,
          country,
          league,
          team_name AS teamName,
          ha,
          year,
          jar_sum_score AS januaryScoreSumCount,
          feb_sum_score AS februaryScoreSumCount,
          mar_sum_score AS marchScoreSumCount,
          apr_sum_score AS aprilScoreSumCount,
          may_sum_score AS mayScoreSumCount,
          jun_sum_score AS juneScoreSumCount,
          jul_sum_score AS julyScoreSumCount,
          aug_sum_score AS augustScoreSumCount,
          sep_sum_score AS septemberScoreSumCount,
          oct_sum_score AS octoberScoreSumCount,
          nov_sum_score AS novemberScoreSumCount,
          dec_sum_score AS decemberScoreSumCount
        FROM team_statistics_data
        WHERE
          country   = #{country}
          AND league    = #{league}
          AND team_name = #{teamName}
          AND ha        = #{ha}
          AND year      = #{year}
        </script>
        """)
    List<TeamMonthlyScoreSummaryEntity> findByCount(TeamMonthlyScoreSummaryEntity entity);

    /**
     * 更新（列名は insert と同じ jar/feb/..._sum_score を使用）
     */
    @Lang(XMLLanguageDriver.class)
    @Update("""
        <script>
        UPDATE team_statistics_data
        SET
          jar_sum_score = #{januaryScoreSumCount},
          feb_sum_score = #{februaryScoreSumCount},
          mar_sum_score = #{marchScoreSumCount},
          apr_sum_score = #{aprilScoreSumCount},
          may_sum_score = #{mayScoreSumCount},
          jun_sum_score = #{juneScoreSumCount},
          jul_sum_score = #{julyScoreSumCount},
          aug_sum_score = #{augustScoreSumCount},
          sep_sum_score = #{septemberScoreSumCount},
          oct_sum_score = #{octoberScoreSumCount},
          nov_sum_score = #{novemberScoreSumCount},
          dec_sum_score = #{decemberScoreSumCount},
          update_id     = #{updateId},
          update_time   = #{updateTime}
        WHERE seq = #{seq}
        </script>
        """)
    int update(TeamMonthlyScoreSummaryEntity entity);
}
