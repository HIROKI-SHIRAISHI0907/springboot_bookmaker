package dev.web.repository.bm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 大会（ACL など）の出場選手（B016 が登録する match_detail_* テーブル）参照用リポジトリ（soccer_bm）
 * @author shiraishitoshio
 */
@Repository
public class CupFatigueRepository {

	private final NamedParameterJdbcTemplate bmJdbcTemplate;

	public CupFatigueRepository(
			@Qualifier("bmJdbcTemplate") NamedParameterJdbcTemplate bmJdbcTemplate) {
		this.bmJdbcTemplate = bmJdbcTemplate;
	}

	/** 試合の基本情報（スコア・状態） */
	public List<Map<String, Object>> findMatches(Collection<String> matchIds) {
		if (matchIds == null || matchIds.isEmpty()) {
			return new ArrayList<>();
		}
		return bmJdbcTemplate.queryForList("""
				SELECT match_id, home_team_name, away_team_name, home_score, away_score, status, start_time
				FROM match_detail_match
				WHERE match_id IN (:ids)
				""", new MapSqlParameterSource("ids", matchIds));
	}

	/**
	 * 出場した選手（スタメン・途中出場）。出場時間は選手スタッツ（match_detail_member_stat）にあればそれも返す。
	 */
	public List<Map<String, Object>> findPlayedMembers(Collection<String> matchIds) {
		if (matchIds == null || matchIds.isEmpty()) {
			return new ArrayList<>();
		}
		return bmJdbcTemplate.queryForList("""
				SELECT
				  l.match_id, l.side, l.lineup_type, l.player_id, l.jersey_number, l.player_name, l.role,
				  l.sub_in_minute, l.sub_out_minute, l.final_rating,
				  s.match_minutes_played
				FROM match_detail_lineup l
				LEFT JOIN LATERAL (
				  SELECT ms.match_minutes_played
				  FROM match_detail_member_stat ms
				  WHERE ms.match_id = l.match_id
				    AND NULLIF(l.player_id, '') IS NOT NULL
				    AND ms.player_id = l.player_id
				  LIMIT 1
				) s ON TRUE
				WHERE l.match_id IN (:ids)
				  AND l.played_flg = '1'
				ORDER BY l.match_id, l.row_no
				""", new MapSqlParameterSource("ids", matchIds));
	}
}
