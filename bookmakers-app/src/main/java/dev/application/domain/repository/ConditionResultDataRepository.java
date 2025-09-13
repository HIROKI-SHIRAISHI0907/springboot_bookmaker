package dev.application.domain.repository;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.application.analyze.bm_m002.ConditionResultDataEntity;

/**
 * condition_result_data テーブルに集計結果を登録するためのRepository
 */
@Mapper
public interface ConditionResultDataRepository {

	/**
	 * 集計結果を condition_result_data に登録する
	 */
	@Insert("""
			    INSERT INTO condition_result_data (
			    	data_seq,
			        mail_target_count,
			        mail_anonymous_target_count,
			        mail_target_success_count,
			        mail_target_fail_count,
			        ex_mail_target_to_no_result_count,
			        ex_no_fin_data_to_no_result_count,
			        goal_delete,
			        alter_target_mail_anonymous,
			        alter_target_mail_fail,
			        no_result_count,
			        err_data,
			        condition_data,
			        hash,
			        register_id,
			        register_time,
			        update_id,
			        update_time
			    ) VALUES (
			    	#{dataSeq},
			        #{mailTargetCount},
			        #{mailAnonymousTargetCount},
			        #{mailTargetSuccessCount},
			        #{mailTargetFailCount},
			        #{exMailTargetToNoResultCount},
			        #{exNoFinDataToNoResultCount},
			        #{goalDelete},
			        #{alterTargetMailAnonymous},
			        #{alterTargetMailFail},
			        #{noResultCount},
			        #{errData},
			        #{conditionData},
			        #{hash},
			        #{registerId},
			        #{registerTime},
			        #{updateId},
			        #{updateTime}
			    )
			""")
	int insert(ConditionResultDataEntity entity);

	/**
	 * condition_result_data から取得する
	 */
	@Select("""
			    SELECT data_seq, mail_target_count,
			        mail_anonymous_target_count,
			        mail_target_success_count,
			        mail_target_fail_count,
			        ex_mail_target_to_no_result_count,
			        ex_no_fin_data_to_no_result_count,
			        goal_delete,
			        alter_target_mail_anonymous,
			        alter_target_mail_fail,
			        no_result_count,
			        err_data,
			        hash
			    FROM condition_result_data
			    WHERE hash = #{hash};
			""")
	List<ConditionResultDataEntity> findByHash(String hash);

	/**
	 * 集計結果を condition_result_data に対して更新する
	 */
	@Update("""
			    UPDATE condition_result_data
			    SET
			        mail_target_count = #{mailTargetCount},
			        mail_anonymous_target_count = #{mailAnonymousTargetCount},
			        mail_target_success_count = #{mailTargetSuccessCount},
			        mail_target_fail_count = #{mailTargetFailCount},
			        ex_mail_target_to_no_result_count = #{mailTargetFailToNoResultCount},
			        ex_no_fin_data_to_no_result_count = #{mailFinDataToNoResultCount},
			        goal_delete = #{goalDelate},
			        alter_target_mail_anonymous = #{alterTargetMailAnonymous},
			        alter_target_mail_fail = #{alterTargetMailFail},
			        no_result_count = #{noResultCount},
			        err_data = #{errData},
			    WHERE hash = #{hash}
			""")
	int update(ConditionResultDataEntity entity);
}
