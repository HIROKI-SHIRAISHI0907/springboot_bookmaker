package dev.application.domain.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.application.analyze.bm_m002.ConditionResultDataEntity;

@Mapper
public interface ConditionResultDataRepository {

	// 1) 取得（hashで1件想定）
	@Select("""
			SELECT
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
			  hash
			FROM public.condition_result_data
			WHERE hash = #{hash}
			""")
	List<ConditionResultDataEntity> findByHash(@Param("hash") String hash);

	// 2) 新規登録（UPSERT: hash 衝突時は更新）
	@Insert("""
			INSERT INTO condition_result_data (
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
			  CASE WHEN #{errData} IS NULL THEN NULL ELSE convert_to(#{errData}, 'UTF8') END,
			  CASE WHEN #{conditionData} IS NULL THEN NULL ELSE convert_to(#{conditionData}, 'UTF8') END,
			  #{hash},
			  #{registerId}, CAST(#{registerTime} AS timestamptz), #{updateId}, CAST(#{updateTime}  AS timestamptz)
			)
			ON CONFLICT (hash) DO UPDATE SET
			  mail_target_count                = EXCLUDED.mail_target_count,
			  mail_anonymous_target_count      = EXCLUDED.mail_anonymous_target_count,
			  mail_target_success_count        = EXCLUDED.mail_target_success_count,
			  mail_target_fail_count           = EXCLUDED.mail_target_fail_count,
			  ex_mail_target_to_no_result_count= EXCLUDED.ex_mail_target_to_no_result_count,
			  ex_no_fin_data_to_no_result_count= EXCLUDED.ex_no_fin_data_to_no_result_count,
			  goal_delete                      = EXCLUDED.goal_delete,
			  alter_target_mail_anonymous      = EXCLUDED.alter_target_mail_anonymous,
			  alter_target_mail_fail           = EXCLUDED.alter_target_mail_fail,
			  no_result_count                  = EXCLUDED.no_result_count,
			  err_data                         = EXCLUDED.err_data,
			  condition_data                   = EXCLUDED.condition_data
			""")
	int insert(ConditionResultDataEntity e);

	// 3) 明示 UPDATE（必要なら）
	@Update("""
			UPDATE condition_result_data
			SET
			  mail_target_count = #{mailTargetCount},
			  mail_anonymous_target_count = #{mailAnonymousTargetCount},
			  mail_target_success_count = #{mailTargetSuccessCount},
			  mail_target_fail_count = #{mailTargetFailCount},
			  ex_mail_target_to_no_result_count = #{exMailTargetToNoResultCount},
			  ex_no_fin_data_to_no_result_count = #{exNoFinDataToNoResultCount},
			  goal_delete = #{goalDelete},
			  alter_target_mail_anonymous = #{alterTargetMailAnonymous},
			  alter_target_mail_fail = #{alterTargetMailFail},
			  no_result_count = #{noResultCount},
			  err_data = #{errData},
			  condition_data = convert_to(#{conditionData}, 'UTF8')
			WHERE hash = #{hash}
			""")
	int update(ConditionResultDataEntity e);
}
