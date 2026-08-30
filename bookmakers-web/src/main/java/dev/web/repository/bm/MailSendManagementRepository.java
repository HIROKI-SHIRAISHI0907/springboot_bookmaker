package dev.web.repository.bm;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.common.entity.MailSendManagementEntity;
import dev.common.enums.MailNoticeEnum;

/**
 * MailSendManagementRepositoryクラス
 * メール送信管理（mail_send_management）へのアクセスを担当する。
 *
 * 想定テーブル定義
 *   mail_send_key : メール送信キー（主キー）
 *   message_id    : メッセージID（SMTP送信時にMTAが払い出すID）
 *   to_address    : 送信先メールアドレス
 *   mail_id       : メールID（mail_info_masterへのFK）
 *   envelope_from : エンベロープフロム
 *   notify_status : 通知ステータス（例: PENDING / SENT / FAILED / BOUNCED）
 *
 * ※テーブル名・カラム名・@Qualifierの値は実際のプロジェクトに合わせて置き換えてください。
 *   このサンプルではCorrelationsRepositoryを参考に、マスタ系とは別スキーマを
 *   想定した構成（コンストラクタでJdbcTemplateを1つだけ受け取る形）にしています。
 *   送信ログ用に別データソースがある場合は@Qualifierの値を変更してください。
 *
 * @author shiraishitoshio
 */
@Repository
public class MailSendManagementRepository {

	private final NamedParameterJdbcTemplate jdbcTemplate;

	private static final RowMapper<MailSendManagementEntity> ROW_MAPPER = (rs, n) -> {
		MailSendManagementEntity dto = new MailSendManagementEntity();
		dto.setMailSendKey(rs.getString("mail_send_key"));
		dto.setMessageId(rs.getString("message_id"));
		dto.setToAddress(rs.getString("to_address"));
		dto.setMailId(rs.getString("mail_id"));
		dto.setEnvelopeFrom(rs.getString("envelope_from"));
		dto.setNotifyStatus(rs.getString("notify_status"));
		dto.setRegisterTime(rs.getTimestamp("register_time"));
		return dto;
	};

	public MailSendManagementRepository(
			@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	// --------------------------------------------------------
	// 全件取得
	// --------------------------------------------------------
	public List<MailSendManagementEntity> findAll() {
		String sql = """
				    SELECT
				      mail_send_key,
				      message_id,
				      to_address,
				      mail_id,
				      envelope_from,
				      notify_status
				    FROM mail_send_management
				    ORDER BY mail_send_key
				""";
		return jdbcTemplate.query(sql, new MapSqlParameterSource(), ROW_MAPPER);
	}

	// --------------------------------------------------------
	// 1件取得（メール送信キー指定）
	// --------------------------------------------------------
	public Optional<MailSendManagementEntity> findByMailSendKey(String mailSendKey) {
		String sql = """
				    SELECT
				      mail_send_key,
				      message_id,
				      to_address,
				      mail_id,
				      envelope_from,
				      notify_status,
				      register_time
				    FROM mail_send_manage
				    WHERE
				      mail_send_key = :mailSendKey
				""";
		List<MailSendManagementEntity> list = jdbcTemplate.query(
				sql,
				new MapSqlParameterSource().addValue("mailSendKey", mailSendKey),
				ROW_MAPPER);
		return list.stream().findFirst();
	}

	// --------------------------------------------------------
	// 1件取得（メッセージID指定／バウンス・開封通知の突合用）
	// --------------------------------------------------------
	public Optional<MailSendManagementEntity> findByMessageId(String messageId) {
		String sql = """
				    SELECT
				      mail_send_key,
				      message_id,
				      to_address,
				      mail_id,
				      envelope_from,
				      notify_status
				    FROM mail_send_manage
				    WHERE
				      message_id = :messageId
				""";
		List<MailSendManagementEntity> list = jdbcTemplate.query(
				sql,
				new MapSqlParameterSource().addValue("messageId", messageId),
				ROW_MAPPER);
		return list.stream().findFirst();
	}

	// --------------------------------------------------------
	// 通知ステータス別取得（例: 未通知分の再送処理などに利用）
	// --------------------------------------------------------
	public List<MailSendManagementEntity> findByNotifyStatus(String notifyStatus) {
		String sql = """
				    SELECT
				      mail_send_key,
				      message_id,
				      to_address,
				      mail_id,
				      envelope_from,
				      notify_status
				    FROM mail_send_manage
				    WHERE
				      notify_status = :notifyStatus
				""";
		return jdbcTemplate.query(
				sql,
				new MapSqlParameterSource().addValue("notifyStatus", notifyStatus),
				ROW_MAPPER);
	}

	// --------------------------------------------------------
	// 登録（送信実行時に1行INSERT）
	// --------------------------------------------------------
	public int insert(MailSendManagementEntity dto) {
		String sql = """
				    INSERT INTO mail_send_manage (
				      mail_send_key,
				      message_id,
				      to_address,
				      mail_id,
				      envelope_from,
				      notify_status,
				      fail_send_count,
				      register_id,
				  	  register_time,
				  	  update_id,
				  	  update_time
				    ) VALUES (
				      :mailSendKey,
				      :messageId,
				      :toAddress,
				      :mailId,
				      :envelopeFrom,
				      :notifyStatus,
				      :failSendCount,
				      'SYSTEM',
				  	  CURRENT_TIMESTAMP,
				  	  'SYSTEM',
				  	  CURRENT_TIMESTAMP
				    )
				""";
		return jdbcTemplate.update(sql, toParams(dto));
	}

	// --------------------------------------------------------
	// 通知ステータスの更新（配信結果・開封通知の反映用）
	// --------------------------------------------------------
	public int updateNotifyStatus(String mailSendKey, String notifyStatus) {
		String sql = """
				    UPDATE mail_send_manage
				    SET
				      notify_status = :notifyStatus,
				      update_time = CURRENT_TIMESTAMP
				    WHERE
				      mail_send_key = :mailSendKey
				""";
		return jdbcTemplate.update(
				sql,
				new MapSqlParameterSource()
						.addValue("mailSendKey", mailSendKey)
						.addValue("notifyStatus", notifyStatus));
	}

	// --------------------------------------------------------
	// メッセージIDの更新（SMTP送信後に払い出されたIDを反映する場合）
	// --------------------------------------------------------
	public int updateMessageId(String mailSendKey, String messageId) {
		String sql = """
				    UPDATE mail_send_manage
				    SET
				      message_id = :messageId,
				      update_time = CURRENT_TIMESTAMP
				    WHERE
				      mail_send_key = :mailSendKey
				""";
		return jdbcTemplate.update(
				sql,
				new MapSqlParameterSource()
						.addValue("mailSendKey", mailSendKey)
						.addValue("messageId", messageId));
	}

	/**
	 * notify_statusを使用済み
	 * @param mailSendKey
	 * @return
	 */
	public int markSendedAsUsed(String mailSendKey) {
	    String sql = """
	            UPDATE mail_send_manage
	            SET
	              notify_status = :used,
	              update_time = CURRENT_TIMESTAMP
	            WHERE
	              mail_send_key = :mailSendKey
	              AND notify_status = :sended
	        """;
	    return jdbcTemplate.update(
	            sql,
	            new MapSqlParameterSource()
	                    .addValue("mailSendKey", mailSendKey)
	                    .addValue("used", MailNoticeEnum.NOTIFY_STATUS_USED.getNoticeStatus())
	                    .addValue("sended", MailNoticeEnum.NOTIFY_STATUS_SENDED.getNoticeStatus()));
	}

	private MapSqlParameterSource toParams(MailSendManagementEntity dto) {
		return new MapSqlParameterSource()
				.addValue("mailSendKey", dto.getMailSendKey())
				.addValue("messageId", dto.getMessageId())
				.addValue("toAddress", dto.getToAddress())
				.addValue("mailId", dto.getMailId())
				.addValue("envelopeFrom", dto.getEnvelopeFrom())
				.addValue("notifyStatus", dto.getNotifyStatus())
				.addValue("failSendCount", dto.getFailSendCount());
	}
}