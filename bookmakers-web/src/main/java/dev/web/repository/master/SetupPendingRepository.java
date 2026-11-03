package dev.web.repository.master;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 管理画面の「やり残し」（setup_pending_manage, soccer_bm_master。2026-11-03 追加）
 * <ul>
 *   <li>ある画面で変更したら pending_flg = '1' にして、何を変えたかを detail に 1 行ずつ足す。</li>
 *   <li>決まった操作（例: 対象データアップロード実行）をしたら pending_flg = '0'・detail を空にする。</li>
 *   <li>未対応チェック（SetupCheck）がこれを見て、管理画面をブロックする。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Repository
public class SetupPendingRepository {

	/** リーグ所属チームを変えた → スクレイピング対象データ設定の「対象データアップロード実行」待ち */
	public static final String SCRAPE_TARGET_UPLOAD = "SCRAPE_TARGET_UPLOAD";

	/** detail に残す行数の上限（古い行から捨てる） */
	private static final int MAX_LINES = 200;

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public SetupPendingRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	/**
	 * やり残しにする（すでにやり残しなら detail の後ろに 1 行足す）
	 * @param code pending_code
	 * @param line 何を変えたか（1 行）
	 * @param operatorId 操作した人
	 */
	public void markPending(String code, String line, String operatorId) {
		String sql = """
				INSERT INTO setup_pending_manage (
				    pending_code, detail, pending_flg, register_id, register_time, update_id, update_time
				) VALUES (
				    :code, :line, '1', :user, CURRENT_TIMESTAMP, :user, CURRENT_TIMESTAMP
				)
				ON CONFLICT (pending_code) DO UPDATE SET
				    detail = CASE
				               WHEN setup_pending_manage.pending_flg = '1' AND COALESCE(setup_pending_manage.detail, '') <> ''
				               THEN setup_pending_manage.detail || E'\\n' || EXCLUDED.detail
				               ELSE EXCLUDED.detail
				             END,
				    pending_flg = '1',
				    update_id   = EXCLUDED.update_id,
				    update_time = CURRENT_TIMESTAMP
				""";
		masterJdbcTemplate.update(sql, new MapSqlParameterSource()
				.addValue("code", code)
				.addValue("line", line)
				.addValue("user", operatorId));
	}

	/**
	 * やり残しを解消する
	 * @return 解消した件数（やり残しが無かったときは 0）
	 */
	public int resolve(String code, String operatorId) {
		String sql = """
				UPDATE setup_pending_manage
				   SET pending_flg = '0',
				       detail      = NULL,
				       update_id   = :user,
				       update_time = CURRENT_TIMESTAMP
				 WHERE pending_code = :code
				   AND pending_flg = '1'
				""";
		return masterJdbcTemplate.update(sql, new MapSqlParameterSource()
				.addValue("code", code)
				.addValue("user", operatorId));
	}

	/**
	 * やり残しの内容（やり残しでなければ空）
	 * @return detail を 1 行ずつ（新しいものが後ろ。多いときは新しい方から MAX_LINES 行）
	 */
	public List<String> findPendingDetails(String code) {
		String sql = """
				SELECT COALESCE(detail, '') AS detail
				FROM setup_pending_manage
				WHERE pending_code = :code AND pending_flg = '1'
				""";
		List<String> rows = masterJdbcTemplate.query(sql, new MapSqlParameterSource("code", code),
				(rs, n) -> rs.getString("detail"));
		if (rows.isEmpty()) {
			return new ArrayList<>();
		}
		List<String> lines = new ArrayList<>();
		for (String s : Arrays.asList(rows.get(0).split("\n"))) {
			if (!s.isBlank()) {
				lines.add(s.strip());
			}
		}
		if (lines.isEmpty()) {
			lines.add("（変更内容の記録なし）");
		}
		return lines.size() > MAX_LINES ? new ArrayList<>(lines.subList(lines.size() - MAX_LINES, lines.size())) : lines;
	}
}
