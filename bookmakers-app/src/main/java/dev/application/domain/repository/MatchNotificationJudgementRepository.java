package dev.application.domain.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * BM_M002:通知情報の正誤判定をスクレイピングレコードに更新するためのRepository
 * @author shiraishitoshio
 *
 */
@Mapper
public interface MatchNotificationJudgementRepository {

	/**
     * 通知情報の正誤判定を更新する
     * @param entity 判定対象の通知エンティティ（通知ID、判定結果などを含む）
     * @return 更新件数
     */
	@Update("""
	        UPDATE data
	        SET judge = #{judge},
	            update_time = #{updateTime}
	        WHERE condition_result_data_seq_id = #{conditionResultDataSeqId}
	    """)
    int updateJudgement(MatchNotificationJudgementEntity entity);

}
