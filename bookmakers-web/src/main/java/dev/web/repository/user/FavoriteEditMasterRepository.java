package dev.web.repository.user;

import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.web.api.favorite.bm_w001.FavoriteLeagueDTO;
import dev.web.api.favorite.bm_w001.FavoriteTeamCandidateDTO;
import dev.web.api.dashboard.support.LeagueVisibility;
import dev.web.repository.master.AdminFavoriteRepository;
import dev.web.util.JapaneseOrderUtil;


/**
 * お気に入り編集（FavoriteEdit.tsx）用 Repository（soccer_master: country_league_master）
 * 管理者が非表示にした国・リーグ・チーム（admin_favorites_master）は候補・登録から外す。
 * @author shiraishitoshio
 */
@Repository
public class FavoriteEditMasterRepository {

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	public FavoriteEditMasterRepository(
			@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	/** 国・リーグの一覧（チーム数つき。国 → リーグのあいうえお順。2026-11 変更） */
	public List<FavoriteLeagueDTO> findLeagues() {
		String sql = """
				SELECT m.country, m.league, COUNT(DISTINCT m.team) AS team_count
				FROM country_league_master m
				WHERE m.del_flg = '0'
				  AND NULLIF(BTRIM(m.country), '') IS NOT NULL
				  AND NULLIF(BTRIM(m.league), '') IS NOT NULL
				  AND NOT """ + AdminFavoriteRepository.HIDDEN_EXISTS + """
				  AND """ + LeagueVisibility.SCRAPE_VISIBLE + """
				GROUP BY m.country, m.league
				ORDER BY m.country COLLATE "ja-x-icu", m.league COLLATE "ja-x-icu"
				""";
		List<FavoriteLeagueDTO> list = masterJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, n) -> {
			FavoriteLeagueDTO d = new FavoriteLeagueDTO();
			d.setCountry(rs.getString("country"));
			d.setLeague(rs.getString("league"));
			d.setTeamCount(rs.getInt("team_count"));
			return d;
		});
		// DB の並びは文字コード順なので、あいうえお順に並べ直す
		list.sort(Comparator.comparing(FavoriteLeagueDTO::getCountry, JapaneseOrderUtil.ORDER)
				.thenComparing(FavoriteLeagueDTO::getLeague, JapaneseOrderUtil.ORDER));
		return list;
	}

	/**
	 * チームを探す（チーム名の部分一致・国・リーグで絞る。空なら絞らない）
	 * 国 → リーグ → チームのあいうえお順（2026-11 変更）。件数の上限は DB の並びで切ってから並べ直す。
	 */
	public List<FavoriteTeamCandidateDTO> searchTeams(String keyword, String country, String league, int limit) {
		String sql = """
				SELECT m.country, m.league, m.team
				FROM country_league_master m
				WHERE m.del_flg = '0'
				  AND NULLIF(BTRIM(m.team), '') IS NOT NULL
				  AND (:keyword = '' OR normalize(m.team, NFKC) ILIKE '%' || normalize(:keyword, NFKC) || '%')
				  AND (:country = '' OR m.country = :country)
				  AND (:league  = '' OR m.league  = :league)
				  AND NOT """ + AdminFavoriteRepository.HIDDEN_EXISTS + """
				  AND """ + LeagueVisibility.SCRAPE_VISIBLE + """
				GROUP BY m.country, m.league, m.team
				ORDER BY m.country COLLATE "ja-x-icu", m.league COLLATE "ja-x-icu", m.team COLLATE "ja-x-icu"
				LIMIT :limit
				""";
		MapSqlParameterSource p = new MapSqlParameterSource()
				.addValue("keyword", keyword == null ? "" : keyword.trim())
				.addValue("country", country == null ? "" : country.trim())
				.addValue("league", league == null ? "" : league.trim())
				.addValue("limit", limit);
		List<FavoriteTeamCandidateDTO> list = masterJdbcTemplate.query(sql, p, (rs, n) -> {
			FavoriteTeamCandidateDTO d = new FavoriteTeamCandidateDTO();
			d.setCountry(rs.getString("country"));
			d.setLeague(rs.getString("league"));
			d.setTeam(rs.getString("team"));
			return d;
		});
		list.sort(Comparator.comparing(FavoriteTeamCandidateDTO::getCountry, JapaneseOrderUtil.ORDER)
				.thenComparing(FavoriteTeamCandidateDTO::getLeague, JapaneseOrderUtil.ORDER)
				.thenComparing(FavoriteTeamCandidateDTO::getTeam, JapaneseOrderUtil.ORDER));
		return list;
	}

	/** マスタにあるチームか */
	public boolean exists(String country, String league, String team) {
		String sql = """
				SELECT COUNT(*)
				FROM country_league_master m
				WHERE m.del_flg = '0' AND m.country = :country AND m.league = :league AND m.team = :team
				  AND NOT """ + AdminFavoriteRepository.HIDDEN_EXISTS + """
				  AND """ + LeagueVisibility.SCRAPE_VISIBLE + """
				""";
		Integer c = masterJdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
				.addValue("country", country).addValue("league", league).addValue("team", team), Integer.class);
		return c != null && c > 0;
	}
}
