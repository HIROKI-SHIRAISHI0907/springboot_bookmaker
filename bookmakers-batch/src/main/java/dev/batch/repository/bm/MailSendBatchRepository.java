package dev.batch.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.common.entity.MailSendManagementEntity;

/**
 * メール送信管理取得・メール送信repository
 *
 */
@Mapper
public interface MailSendBatchRepository {

	/**
	 * メール送信管理の中でステータスが通知前: '0'のものかつ送信失敗が3回以上でないものを取得する
	 */
	@Select("""
			SELECT
				mail_send_key AS mailSendKey,
				message_id AS messageId,
				to_address AS toAddress,
				mail_id AS mailId,
				envelope_from AS envelopeFrom,
				notify_status AS notifyStatus,
				fail_send_count AS failSendCount,
				bikou
			FROM mail_send_manage
			WHERE
				notify_status = '0' AND
				fail_send_count < 3
			         """)
	List<MailSendManagementEntity> findPendingNoticeStatus();

	/**
	 * メール送信管理の中でステータスが通知前: '0'のものかつ送信失敗が3回以上でないものを取得する
	 */
	@Select("""
			SELECT
				mail_send_key AS mailSendKey,
				message_id AS messageId,
				to_address AS toAddress,
				mail_id AS mailId,
				envelope_from AS envelopeFrom,
				notify_status AS notifyStatus,
				fail_send_count AS failSendCount,
				bikou
			FROM mail_send_manage
			WHERE
				mail_send_key = #{mailSendKey}
			         """)
	List<MailSendManagementEntity> findByMailSendKey(
			@Param("mailSendKey") String mailSendKey);

	/**
	 * 通知ステータスを更新する。
	 *
	 * @param mailSendKey メール送信キー
	 * @param notifyStatus 通知ステータス
	 * @return 更新件数
	 */
	@Update("""
			    UPDATE mail_send_manage
			       SET notify_status = #{notifyStatus},
			           update_time = CURRENT_TIMESTAMP
			     WHERE mail_send_key = #{mailSendKey}
			""")
	int updateFromPendingToSendedStatus(
			@Param("mailSendKey") String mailSendKey,
			@Param("notifyStatus") String notifyStatus);

	/**
	 * 送信失敗数を更新する。
	 *
	 * @param mailSendKey メール送信キー
	 * @param failSendCount 送信失敗数
	 * @return 更新件数
	 */
	@Update("""
			    UPDATE mail_send_manage
			       SET fail_send_count = #{failSendCount},
			           update_time = CURRENT_TIMESTAMP
			     WHERE mail_send_key = #{mailSendKey}
			""")
	int updateFailSendCount(
			@Param("mailSendKey") String mailSendKey,
			@Param("failSendCount") Integer failSendCount);

	/**
	 * メール送信管理に登録する
	 *
	 * @param mailSendKey メール送信キー
	 * @param notifyStatus 通知ステータス
	 * @return 更新件数
	 */
	@Insert("""
			    INSERT INTO mail_send_manage (
			      mail_send_key,
			      message_id,
			      to_address,
			      mail_id,
			      envelope_from,
			      notify_status,
			      fail_send_count,
			      bikou,
			      register_id,
			  	  register_time,
			  	  update_id,
			  	  update_time
			    ) VALUES (
			      #{mailSendKey},
			      #{messageId},
			      #{toAddress},
			      #{mailId},
			      #{envelopeFrom},
			      #{notifyStatus},
			      #{failSendCount},
			      #{bikou},
			      'SYSTEM',
			  	  CURRENT_TIMESTAMP,
			  	  'SYSTEM',
			  	  CURRENT_TIMESTAMP
			    )
			""")
	int insert(MailSendManagementEntity entity);

}
