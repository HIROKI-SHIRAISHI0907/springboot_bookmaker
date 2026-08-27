package dev.batch.repository.master;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import dev.common.entity.MailInfoMasterEntity;

/**
 * メール情報マスタ取得repository
 *
 */
@Mapper
public interface MailInfoMasterBatchRepository {

	/**
	 * メール情報マスタをmailIdから検索
	 */
	@Select("""
            SELECT
				mail_id AS mailId,
				mail_subject AS mailSubject,
				mail_body AS mailBody,
				from_address AS fromAddress
			FROM mail_info_master
			WHERE
				mail_id = #{mailId}
			LIMIT 1
            """)
    MailInfoMasterEntity findMailByMailIdInfo(
    		@Param("mailId") String mailId);

}
