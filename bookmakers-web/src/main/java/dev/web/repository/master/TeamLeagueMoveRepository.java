package dev.web.repository.master;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import dev.web.api.bm_a039.TeamLeagueMoveDTO;

/**
 * チーム昇格・降格確認設定（bm_a039。2026-11-24 追加）用リポジトリ（soccer_bm_master）
 * <ul>
 *   <li>team_league_move_history : チームの所属リーグの移動。country_league_master のトリガー
 *       （fn_team_league_move_detect）が記録する。confirm_flg = '0' 未確定 / '1' 確定 / '9' 取り消し。</li>
 *   <li>確定したときに、大会出場チーム（cup_competition_team_master）のリーグと、
 *       移動したチームの行のリーグレベル（country_league_master.league_level）を移動先に合わせる。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Repository
public class TeamLeagueMoveRepository {

	public static final String FLG_UNCONFIRMED = "0";
	public static final String FLG_CONFIRMED = "1";
	public static final String FLG_CANCELED = "9";

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	/** soccer_bm_master 用のトランザクション（DataSource が複数あるため、この JdbcTemplate の DataSource で作る） */
	private final TransactionTemplate transactionTemplate;

	public TeamLeagueMoveRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
		this.transactionTemplate = new TransactionTemplate(
				new DataSourceTransactionManager(masterJdbcTemplate.getJdbcTemplate().getDataSource()));
	}

	/** 1 つのトランザクションで実行する（例外が出たら全部戻す） */
	public void inTransaction(Runnable work) {
		transactionTemplate.executeWithoutResult(status -> work.run());
	}

	private static final String SELECT = """
			SELECT id, country, team, link, team_id, from_league, to_league, from_level, to_level,
			       suggest_type, move_type, source, confirm_flg,
			       TO_CHAR(detected_time, 'YYYY-MM-DD HH24:MI:SS') AS detected_time,
			       TO_CHAR(confirm_time,  'YYYY-MM-DD HH24:MI:SS') AS confirm_time,
			       update_id
			FROM team_league_move_history
			""";

	/**
	 * 一覧。all = false なら未確定だけ、true なら全件（新しい順に最大 limit 件。未確定は必ず全部）
	 */
	public List<TeamLeagueMoveDTO> findAll(boolean all, int limit) {
		String sql = all
				? SELECT + """
						WHERE confirm_flg = '0'
						   OR id IN (SELECT id FROM team_league_move_history WHERE confirm_flg <> '0'
						             ORDER BY detected_time DESC, id DESC LIMIT :limit)
						ORDER BY CASE WHEN confirm_flg = '0' THEN 0 ELSE 1 END,
						         detected_time DESC, country COLLATE "ja-x-icu", team COLLATE "ja-x-icu", id
						"""
				: SELECT + """
						WHERE confirm_flg = '0'
						ORDER BY country COLLATE "ja-x-icu", from_league COLLATE "ja-x-icu", team COLLATE "ja-x-icu", id
						""";
		return masterJdbcTemplate.query(sql, new MapSqlParameterSource("limit", limit), (rs, n) -> {
			TeamLeagueMoveDTO d = new TeamLeagueMoveDTO();
			d.setId(rs.getLong("id"));
			d.setCountry(rs.getString("country"));
			d.setTeam(rs.getString("team"));
			d.setLink(rs.getString("link"));
			d.setTeamId(rs.getObject("team_id") == null ? null : rs.getInt("team_id"));
			d.setFromLeague(rs.getString("from_league"));
			d.setToLeague(rs.getString("to_league"));
			d.setFromLevel(rs.getObject("from_level") == null ? null : rs.getInt("from_level"));
			d.setToLevel(rs.getObject("to_level") == null ? null : rs.getInt("to_level"));
			d.setSuggestType(rs.getString("suggest_type"));
			d.setMoveType(rs.getString("move_type"));
			d.setSource(rs.getString("source"));
			d.setConfirmFlg(rs.getString("confirm_flg"));
			d.setDetectedTime(rs.getString("detected_time"));
			d.setConfirmTime(rs.getString("confirm_time"));
			d.setUpdateId(rs.getString("update_id"));
			return d;
		});
	}

	/** 未確定の 1 件（無い・確定済みなら null） */
	public TeamLeagueMoveDTO findUnconfirmed(long id) {
		List<TeamLeagueMoveDTO> list = masterJdbcTemplate.query(SELECT + " WHERE id = :id AND confirm_flg = '0'",
				new MapSqlParameterSource("id", id), (rs, n) -> {
					TeamLeagueMoveDTO d = new TeamLeagueMoveDTO();
					d.setId(rs.getLong("id"));
					d.setCountry(rs.getString("country"));
					d.setTeam(rs.getString("team"));
					d.setLink(rs.getString("link"));
					d.setTeamId(rs.getObject("team_id") == null ? null : rs.getInt("team_id"));
					d.setFromLeague(rs.getString("from_league"));
					d.setToLeague(rs.getString("to_league"));
					d.setToLevel(rs.getObject("to_level") == null ? null : rs.getInt("to_level"));
					return d;
				});
		return list.isEmpty() ? null : list.get(0);
	}

	/** 未確定の件数 */
	public int countUnconfirmed() {
		Integer n = masterJdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM team_league_move_history WHERE confirm_flg = '0'",
				new MapSqlParameterSource(), Integer.class);
		return n == null ? 0 : n;
	}

	/** 未確定を確定・取り消しにする（未確定のものだけ） */
	public int updateFlg(long id, String moveType, String confirmFlg, String operatorId) {
		return masterJdbcTemplate.update("""
				UPDATE team_league_move_history
				SET move_type = COALESCE(:moveType, move_type),
				    confirm_flg = :flg,
				    confirm_time = CURRENT_TIMESTAMP,
				    update_id = :op,
				    update_time = CURRENT_TIMESTAMP
				WHERE id = :id AND confirm_flg = '0'
				""", new MapSqlParameterSource()
				.addValue("id", id)
				.addValue("moveType", moveType)
				.addValue("flg", confirmFlg)
				.addValue("op", operatorId));
	}

	/**
	 * リーグ所属チーム付け替え設定（リーグ名の変更）で動かしたチームの記録を、確定（RENAME）にする。
	 * リーグ名が変わっただけで昇格・降格ではないため、確認画面には出さない。
	 */
	public int markRename(String country, String fromLeague, String toLeague, String operatorId) {
		return masterJdbcTemplate.update("""
				UPDATE team_league_move_history
				SET move_type = 'RENAME',
				    confirm_flg = '1',
				    confirm_time = CURRENT_TIMESTAMP,
				    update_id = :op,
				    update_time = CURRENT_TIMESTAMP
				WHERE confirm_flg = '0' AND country = :country AND from_league = :from AND to_league = :to
				""", new MapSqlParameterSource()
				.addValue("country", country)
				.addValue("from", fromLeague)
				.addValue("to", toLeague)
				.addValue("op", operatorId));
	}

	/**
	 * 大会出場チーム（有効な行）のリーグを移動先へ。
	 * 同じ大会に移動先リーグの同じチームの行があれば（一意キー: competition_id, country, league, team）、
	 * その行を有効にして、移動元の行は論理削除する。
	 * @return 移動先にした行数
	 */
	public int moveCupTeams(String country, String team, String fromLeague, String toLeague, String operatorId) {
		MapSqlParameterSource p = new MapSqlParameterSource()
				.addValue("country", country)
				.addValue("team", team)
				.addValue("from", fromLeague)
				.addValue("to", toLeague)
				.addValue("op", operatorId);
		// 1. 移動先の行が既にある → 有効にする
		int revived = masterJdbcTemplate.update("""
				UPDATE cup_competition_team_master t
				SET del_flg = '0', link = COALESCE(s.link, t.link), update_id = :op, update_time = CURRENT_TIMESTAMP
				FROM cup_competition_team_master s
				WHERE s.country = :country AND s.league = :from AND s.team = :team AND s.del_flg = '0'
				  AND t.competition_id = s.competition_id AND t.country = s.country
				  AND t.league = :to AND t.team = s.team
				""", p);
		// 2. その場合の移動元の行は論理削除
		masterJdbcTemplate.update("""
				UPDATE cup_competition_team_master s
				SET del_flg = '1', update_id = :op, update_time = CURRENT_TIMESTAMP
				WHERE s.country = :country AND s.league = :from AND s.team = :team AND s.del_flg = '0'
				  AND EXISTS (SELECT 1 FROM cup_competition_team_master t
				               WHERE t.competition_id = s.competition_id AND t.country = s.country
				                 AND t.league = :to AND t.team = s.team)
				""", p);
		// 3. 移動先の行が無い → リーグを書き換える
		int moved = masterJdbcTemplate.update("""
				UPDATE cup_competition_team_master s
				SET league = :to, update_id = :op, update_time = CURRENT_TIMESTAMP
				WHERE s.country = :country AND s.league = :from AND s.team = :team AND s.del_flg = '0'
				  AND NOT EXISTS (SELECT 1 FROM cup_competition_team_master t
				                   WHERE t.competition_id = s.competition_id AND t.country = s.country
				                     AND t.league = :to AND t.team = s.team)
				""", p);
		return revived + moved;
	}

	/**
	 * 移動したチームの行（country_league_master）のリーグレベルを移動先のリーグに合わせる。
	 * 移動先リーグのレベルが分からないときは何もしない。
	 */
	public int syncTeamLevel(Integer teamId, String country, String toLeague, String operatorId) {
		if (teamId == null) {
			return 0;
		}
		return masterJdbcTemplate.update("""
				UPDATE country_league_master m
				SET league_level = lv.level, update_id = :op, update_time = CURRENT_TIMESTAMP
				FROM (SELECT MIN(league_level) AS level FROM country_league_master
				       WHERE country = :country AND league = :to AND id <> :id AND league_level IS NOT NULL) lv
				WHERE m.id = :id AND m.league = :to AND lv.level IS NOT NULL
				  AND m.league_level IS DISTINCT FROM lv.level
				""", new MapSqlParameterSource()
				.addValue("id", teamId)
				.addValue("country", country)
				.addValue("to", toLeague)
				.addValue("op", operatorId));
	}
}
