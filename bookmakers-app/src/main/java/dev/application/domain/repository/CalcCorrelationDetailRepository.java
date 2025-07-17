package dev.application.domain.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import dev.application.analyze.bm_m025.CalcCorrelationRankingEntity;

@Mapper
public interface CalcCorrelationDetailRepository {

    @Insert({
        "INSERT INTO correlation_ranking_data (",
        "id, file, country, league, home, away, score, chk_body,",
        "1st_rank, 2nd_rank, 3rd_rank, 4th_rank, 5th_rank",
        "register_id, register_time, update_id, update_time",
        ") VALUES (",
        "#{id}, #{file}, #{country}, #{league}, #{home}, #{away}, #{score}, #{chkBody},",
        "#{rank1st}, #{rank2nd},#{rank3rd}, #{rank4th}, #{rank5th},",
        "#{registerId}, #{registerTime}, #{updateId}, #{updateTime}",
        ")"
    })
    void insert(CalcCorrelationRankingEntity entity);
}
