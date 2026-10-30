package dev.web.repository.master;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.Data;

/**
 * 画面設定マスタ（system_view_master, soccer_bm_master）
 * <ul>
 *   <li>setup_flg = '1': 未対応があっても開ける画面（未対応を解消するための画面）</li>
 *   <li>check_code: この画面で解消するチェック（SetupCheck#code）</li>
 *   <li>block_flg = '1': このチェックが残っていたら、setup_flg = '1' 以外の画面を開けなくする（'0' は警告だけ）</li>
 * </ul>
 * @author shiraishitoshio
 */
@Repository
public class SystemViewMasterRepository {

	@Data
	public static class Row {
		private String viewCode;
		private String viewName;
		/** 管理画面のパス（/admin/ より後ろ。例: season） */
		private String path;
		private String checkCode;
		private boolean setup;
		private boolean block;
		private int dispOrder;
	}

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public SystemViewMasterRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	/** 有効な行（del_flg = '0'）を表示順に */
	public List<Row> findAll() {
		String sql = """
				SELECT view_code, view_name, path, check_code, setup_flg, block_flg, disp_order
				FROM system_view_master
				WHERE del_flg = '0'
				ORDER BY disp_order, view_code
				""";
		return masterJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, n) -> {
			Row r = new Row();
			r.setViewCode(rs.getString("view_code"));
			r.setViewName(rs.getString("view_name"));
			r.setPath(rs.getString("path"));
			r.setCheckCode(rs.getString("check_code"));
			r.setSetup("1".equals(rs.getString("setup_flg")));
			r.setBlock("1".equals(rs.getString("block_flg")));
			r.setDispOrder(rs.getInt("disp_order"));
			return r;
		});
	}
}
