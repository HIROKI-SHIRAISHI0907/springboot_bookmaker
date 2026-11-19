package dev.application.domain.repository.master.b011;

// 2026-11-20 bookmakers-batch（dev.batch.repository.master.MailInfoMasterBatchRepository）から移動（B011 を統計用の bookmakers-app で動かすため）

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
