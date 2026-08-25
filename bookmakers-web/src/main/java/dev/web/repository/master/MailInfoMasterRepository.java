package dev.web.repository.master;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.common.entity.MailInfoMasterEntity;


/**
 * MailInfoMasterRepositoryクラス
 * メール情報マスタ（mail_info_master）へのアクセスを担当する。
 *
 * 想定テーブル定義
 *   mail_id       : メールID（主キー）
 *   mail_subject  : メール件名
 *   mail_body     : メール本文
 *   from_address  : 送信元メールアドレス
 *
 * ※テーブル名・カラム名・@Qualifierの値（今回はサンプルとして
 *   AllLeagueMasterWebRepositoryと同じ webMasterJdbcTemplate を指定しています）は
 *   実際のプロジェクトの命名規則・データソース構成に合わせて置き換えてください。
 *
 * @author shiraishitoshio
 */
@Repository
public class MailInfoMasterRepository {

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	private static final RowMapper<MailInfoMasterEntity> ROW_MAPPER = (rs, n) -> {
		MailInfoMasterEntity dto = new MailInfoMasterEntity();
		dto.setMailId(rs.getString("mail_id"));
		dto.setMailSubject(rs.getString("mail_subject"));
		dto.setMailBody(rs.getString("mail_body"));
		dto.setFromAddress(rs.getString("from_address"));
		return dto;
	};

	public MailInfoMasterRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	// --------------------------------------------------------
	// 全件取得
	// --------------------------------------------------------
	public List<MailInfoMasterEntity> findAll() {
		String sql = """
				    SELECT
				      mail_id,
				      mail_subject,
				      mail_body,
				      from_address
				    FROM mail_info_master
				    ORDER BY mail_id
				""";
		return masterJdbcTemplate.query(sql, new MapSqlParameterSource(), ROW_MAPPER);
	}

	// --------------------------------------------------------
	// 1件取得（メールID指定）
	// --------------------------------------------------------
	public Optional<MailInfoMasterEntity> findById(String mailId) {
		String sql = """
				    SELECT
				      mail_id,
				      mail_subject,
				      mail_body,
				      from_address
				    FROM mail_info_master
				    WHERE
				      mail_id = :mailId
				""";
		List<MailInfoMasterEntity> list = masterJdbcTemplate.query(
				sql,
				new MapSqlParameterSource().addValue("mailId", mailId),
				ROW_MAPPER);
		return list.stream().findFirst();
	}

	// --------------------------------------------------------
	// 登録
	// --------------------------------------------------------
	public int insert(MailInfoMasterEntity dto) {
		String sql = """
				    INSERT INTO mail_info_master (
				      mail_id,
				      mail_subject,
				      mail_body,
				      from_address,
				      register_id,
				   	  register_time,
				  	  update_id,
				  	  update_time
				    ) VALUES (
				      :mailId,
				      :mailSubject,
				      :mailBody,
				      :fromAddress,
				      'SYSTEM',
				  	  CURRENT_TIMESTAMP,
				  	  'SYSTEM',
				  	  CURRENT_TIMESTAMP
				    )
				""";
		return masterJdbcTemplate.update(sql, toParams(dto));
	}

	// --------------------------------------------------------
	// 更新
	// --------------------------------------------------------
	public int update(MailInfoMasterEntity dto) {
		String sql = """
				    UPDATE mail_info_master
				    SET
				      mail_subject = :mailSubject,
				      mail_body = :mailBody,
				      from_address = :fromAddress,
				      update_time = CURRENT_TIMESTAMP
				    WHERE
				      mail_id = :mailId
				""";
		return masterJdbcTemplate.update(sql, toParams(dto));
	}

	private MapSqlParameterSource toParams(MailInfoMasterEntity dto) {
		return new MapSqlParameterSource()
				.addValue("mailId", dto.getMailId())
				.addValue("mailSubject", dto.getMailSubject())
				.addValue("mailBody", dto.getMailBody())
				.addValue("fromAddress", dto.getFromAddress());
	}
}