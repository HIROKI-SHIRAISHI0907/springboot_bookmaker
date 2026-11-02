package dev.web.repository.master;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import dev.web.api.bm_a036.CupCompetitionResponse;

/**
 * 大会出場チーム登録設定（bm_a036）用リポジトリ（soccer_bm_master）
 * <ul>
 *   <li>cup_competition_master      : 大会（ACL など）。Flashscore の大会ページ URL を持つ。</li>
 *   <li>cup_competition_team_master : 大会に出場するチーム（country_league_master の国・リーグ・チーム・リンク）。</li>
 *   <li>候補のチーム = スクレイピング対象（all_league_scrape_master.disp_flg = '0'。b001_country_league.json と同じ）
 *       のリーグにいる有効なチーム（country_league_master.del_flg = '0'）。</li>
 *   <li>削除はどちらも論理削除（del_flg = '1'）。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Repository
public class CupCompetitionRepository {

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	/**
	 * soccer_bm_master 用のトランザクション（2026-11-01 追加）。
	 * DataSource が複数あり、@Transactional は Primary（user DB）のトランザクションになるため、
	 * この JdbcTemplate の DataSource で作る（MasterCsvDataUploadRepository と同じやり方）。
	 */
	private final TransactionTemplate transactionTemplate;

	public CupCompetitionRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
		this.transactionTemplate = new TransactionTemplate(
				new DataSourceTransactionManager(masterJdbcTemplate.getJdbcTemplate().getDataSource()));
	}

	/** 1 つのトランザクションで実行する（例外が出たら全部戻す） */
	public void inTransaction(Runnable work) {
		transactionTemplate.executeWithoutResult(status -> work.run());
	}

	/** 有効な大会（表示順・id 順。出場チーム数つき） */
	public List<CupCompetitionResponse.Competition> findCompetitions() {
		String sql = """
				SELECT
				  c.id, c.competition_name, c.region, c.competition_url, c.season_year, c.disp_order, c.scrape_flg,
				  (SELECT COUNT(*) FROM cup_competition_team_master t
				    WHERE t.competition_id = c.id AND t.del_flg = '0') AS team_count
				FROM cup_competition_master c
				WHERE c.del_flg = '0'
				ORDER BY c.disp_order, c.id
				""";
		return masterJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, n) -> {
			CupCompetitionResponse.Competition c = new CupCompetitionResponse.Competition();
			c.setId(rs.getInt("id"));
			c.setCompetitionName(rs.getString("competition_name"));
			c.setRegion(rs.getString("region"));
			c.setCompetitionUrl(rs.getString("competition_url"));
			c.setSeasonYear(rs.getString("season_year"));
			c.setDispOrder(rs.getInt("disp_order"));
			c.setScrapeFlg(rs.getString("scrape_flg"));
			c.setTeamCount(rs.getInt("team_count"));
			return c;
		});
	}

	/** 同じ URL の有効な大会の id（自分以外）。無ければ空 */
	public List<Integer> findOtherIdsByUrl(String url, Integer exceptId) {
		String sql = """
				SELECT id FROM cup_competition_master
				WHERE del_flg = '0' AND competition_url = :url AND (CAST(:exceptId AS INTEGER) IS NULL OR id <> :exceptId)
				""";
		return masterJdbcTemplate.queryForList(sql, new MapSqlParameterSource()
				.addValue("url", url)
				.addValue("exceptId", exceptId), Integer.class);
	}

	/** 大会を登録する（id を返す） */
	public Integer insertCompetition(String name, String region, String url, String seasonYear, int dispOrder,
			String scrapeFlg, String operatorId) {
		String sql = """
				INSERT INTO cup_competition_master (
				  competition_name, region, competition_url, season_year, disp_order, scrape_flg, del_flg,
				  register_id, register_time, update_id, update_time
				) VALUES (
				  :name, :region, :url, :seasonYear, :dispOrder, :scrapeFlg, '0',
				  :operatorId, CURRENT_TIMESTAMP, :operatorId, CURRENT_TIMESTAMP
				)
				RETURNING id
				""";
		return masterJdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
				.addValue("name", name)
				.addValue("region", region)
				.addValue("url", url)
				.addValue("seasonYear", seasonYear)
				.addValue("dispOrder", dispOrder)
				.addValue("scrapeFlg", scrapeFlg)
				.addValue("operatorId", operatorId), Integer.class);
	}

	/** 大会を更新する */
	public int updateCompetition(Integer id, String name, String region, String url, String seasonYear, int dispOrder,
			String scrapeFlg, String operatorId) {
		String sql = """
				UPDATE cup_competition_master
				SET competition_name = :name,
				    region           = :region,
				    competition_url  = :url,
				    season_year      = :seasonYear,
				    disp_order       = :dispOrder,
				    scrape_flg       = :scrapeFlg,
				    update_id        = :operatorId,
				    update_time      = CURRENT_TIMESTAMP
				WHERE id = :id AND del_flg = '0'
				""";
		return masterJdbcTemplate.update(sql, new MapSqlParameterSource()
				.addValue("id", id)
				.addValue("name", name)
				.addValue("region", region)
				.addValue("url", url)
				.addValue("seasonYear", seasonYear)
				.addValue("dispOrder", dispOrder)
				.addValue("scrapeFlg", scrapeFlg)
				.addValue("operatorId", operatorId));
	}

	/**
	 * 同じ URL の有効な大会の大会名を更新し、日程を取る状態（scrape_flg = '1'）にする（大会一括アップロード）
	 * @return 更新件数（0 なら同じ URL の大会は無い）
	 */
	public int updateCompetitionByUrl(String url, String name, String operatorId) {
		String sql = """
				UPDATE cup_competition_master
				SET competition_name = :name,
				    scrape_flg       = '1',
				    update_id        = :operatorId,
				    update_time      = CURRENT_TIMESTAMP
				WHERE competition_url = :url AND del_flg = '0'
				""";
		return masterJdbcTemplate.update(sql, new MapSqlParameterSource()
				.addValue("url", url)
				.addValue("name", name)
				.addValue("operatorId", operatorId));
	}

	/** 大会を論理削除する（出場チームも論理削除） */
	public int deleteCompetition(Integer id, String operatorId) {
		MapSqlParameterSource p = new MapSqlParameterSource()
				.addValue("id", id)
				.addValue("operatorId", operatorId);
		masterJdbcTemplate.update("""
				UPDATE cup_competition_team_master
				SET del_flg = '1', update_id = :operatorId, update_time = CURRENT_TIMESTAMP
				WHERE competition_id = :id AND del_flg = '0'
				""", p);
		return masterJdbcTemplate.update("""
				UPDATE cup_competition_master
				SET del_flg = '1', update_id = :operatorId, update_time = CURRENT_TIMESTAMP
				WHERE id = :id AND del_flg = '0'
				""", p);
	}

	/** 大会の出場チーム（有効な行）。competitionId が null なら全大会 */
	public List<CupCompetitionResponse.Team> findTeams(Integer competitionId) {
		String sql = """
				SELECT t.id, t.competition_id, t.country, t.league, t.team, t.link
				FROM cup_competition_team_master t
				JOIN cup_competition_master c ON c.id = t.competition_id AND c.del_flg = '0'
				WHERE t.del_flg = '0'
				  AND (CAST(:competitionId AS INTEGER) IS NULL OR t.competition_id = :competitionId)
				ORDER BY t.competition_id, t.country, t.league, t.team
				""";
		return masterJdbcTemplate.query(sql, new MapSqlParameterSource("competitionId", competitionId), (rs, n) -> {
			CupCompetitionResponse.Team t = new CupCompetitionResponse.Team();
			t.setId(rs.getInt("id"));
			t.setCompetitionId(rs.getInt("competition_id"));
			t.setCountry(rs.getString("country"));
			t.setLeague(rs.getString("league"));
			t.setTeam(rs.getString("team"));
			t.setLink(rs.getString("link"));
			return t;
		});
	}

	/**
	 * 候補のチーム: スクレイピング対象のリーグ（all_league_scrape_master.disp_flg = '0'）にいる有効なチーム。
	 * 国・リーグ・チーム順。
	 */
	public List<CupCompetitionResponse.CandidateTeam> findCandidateTeams() {
		String sql = """
				SELECT m.country, m.league, m.team, MAX(m.link) AS link
				FROM country_league_master m
				WHERE m.del_flg = '0'
				  AND NULLIF(BTRIM(m.team), '') IS NOT NULL
				  AND EXISTS (
				    SELECT 1 FROM all_league_scrape_master a
				    WHERE a.disp_flg = '0' AND a.country = m.country AND a.league = m.league
				  )
				GROUP BY m.country, m.league, m.team
				ORDER BY m.country, m.league, m.team
				""";
		return masterJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, n) -> {
			CupCompetitionResponse.CandidateTeam t = new CupCompetitionResponse.CandidateTeam();
			t.setCountry(rs.getString("country"));
			t.setLeague(rs.getString("league"));
			t.setTeam(rs.getString("team"));
			t.setLink(rs.getString("link"));
			return t;
		});
	}

	/** 出場チームを登録する（同じ大会・国・リーグ・チームの行があれば有効に戻してリンクを更新） */
	public int upsertTeam(Integer competitionId, String country, String league, String team, String link,
			String operatorId) {
		String sql = """
				INSERT INTO cup_competition_team_master (
				  competition_id, country, league, team, link, del_flg,
				  register_id, register_time, update_id, update_time
				) VALUES (
				  :competitionId, :country, :league, :team, :link, '0',
				  :operatorId, CURRENT_TIMESTAMP, :operatorId, CURRENT_TIMESTAMP
				)
				ON CONFLICT (competition_id, country, league, team) DO UPDATE SET
				  link        = EXCLUDED.link,
				  del_flg     = '0',
				  update_id   = EXCLUDED.update_id,
				  update_time = CURRENT_TIMESTAMP
				""";
		return masterJdbcTemplate.update(sql, new MapSqlParameterSource()
				.addValue("competitionId", competitionId)
				.addValue("country", country)
				.addValue("league", league)
				.addValue("team", team)
				.addValue("link", link)
				.addValue("operatorId", operatorId));
	}

	/** 出場チームを外す（論理削除） */
	public int removeTeams(Integer competitionId, List<Integer> ids, String operatorId) {
		if (ids == null || ids.isEmpty()) {
			return 0;
		}
		String sql = """
				UPDATE cup_competition_team_master
				SET del_flg = '1', update_id = :operatorId, update_time = CURRENT_TIMESTAMP
				WHERE competition_id = :competitionId AND id IN (:ids) AND del_flg = '0'
				""";
		return masterJdbcTemplate.update(sql, new MapSqlParameterSource()
				.addValue("competitionId", competitionId)
				.addValue("ids", ids)
				.addValue("operatorId", operatorId));
	}

	/** 国・リーグ・チームの有効なリンク（country_league_master）。無ければ空文字 */
	public String findTeamLink(String country, String league, String team) {
		List<String> links = masterJdbcTemplate.queryForList("""
				SELECT COALESCE(link, '') FROM country_league_master
				WHERE del_flg = '0' AND country = :country AND league = :league AND team = :team
				ORDER BY id DESC
				LIMIT 1
				""", new MapSqlParameterSource()
				.addValue("country", country)
				.addValue("league", league)
				.addValue("team", team), String.class);
		return links.isEmpty() ? "" : links.get(0);
	}
}
