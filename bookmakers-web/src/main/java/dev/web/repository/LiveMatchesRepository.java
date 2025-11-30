package dev.web.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import dev.web.api.bm_w007.LiveMatchResponse;
import lombok.RequiredArgsConstructor;

/**
 * 現在開催中の試合（LIVE）取得 Repository
 * Node 実装 src/routes/lives.ts 相当
 *
 * @author shiraishitoshio
 */
@Repository
@RequiredArgsConstructor
public class LiveMatchesRepository {

	@Qualifier("bmJdbcTemplate")
    private final NamedParameterJdbcTemplate bmJdbcTemplate;

    @Qualifier("masterJdbcTemplate")
    private final NamedParameterJdbcTemplate masterJdbcTemplate;

	/**
	 * LIVE 試合一覧を取得。
	 *
	 * @param country 国名（null/空なら全カテゴリ）
	 * @param league  リーグ名（null/空なら全カテゴリ）
	 * @return LiveMatchDTO のリスト
	 */
    public List<LiveMatchResponse> findLiveMatches(String country, String league) {
        final String like;
        if (StringUtils.hasText(country) && StringUtils.hasText(league)) {
            like = country + ": " + league + "%";
        } else {
            like = "%";
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("pattern", like);

        // ① soccer_bm.data から LIVE 試合だけ取得
        List<LiveMatchResponse> rows = bmJdbcTemplate.query(SQL, params, ROW_MAPPER);

        // ② master 側から team → slug のマップを作成
        MapSqlParameterSource mparams = new MapSqlParameterSource();
        String masterSql;

        if (StringUtils.hasText(country) && StringUtils.hasText(league)) {
            masterSql = """
              SELECT
                team,
                NULLIF(substring(link from '/team/([^/]+)/'), '') AS slug
              FROM country_league_master
              WHERE country = :country
                AND league  = :league
            """;
            mparams.addValue("country", country)
                   .addValue("league", league);
        } else {
            // country / league 未指定なら全件（必要に応じて絞り込み検討）
            masterSql = """
              SELECT
                team,
                NULLIF(substring(link from '/team/([^/]+)/'), '') AS slug
              FROM country_league_master
            """;
        }

        var slugMap = masterJdbcTemplate.query(
                masterSql,
                mparams,
                rs -> {
                    java.util.Map<String, String> m = new java.util.HashMap<>();
                    while (rs.next()) {
                        m.put(rs.getString("team"), rs.getString("slug"));
                    }
                    return m;
                }
        );

        // ③ LiveMatchDTO に slug をセット
        for (LiveMatchResponse dto : rows) {
            String homeSlug = slugMap.get(dto.getHomeTeamName());
            String awaySlug = slugMap.get(dto.getAwayTeamName());
            dto.setHomeSlug(homeSlug);
            dto.setAwaySlug(awaySlug);
        }

        return rows;
    }


	// ========= RowMapper =========

	private static final RowMapper<LiveMatchResponse> ROW_MAPPER = new RowMapper<LiveMatchResponse>() {
	    @Override
	    public LiveMatchResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
	        LiveMatchResponse dto = new LiveMatchResponse();

	        dto.setSeq(rs.getLong("seq"));
	        dto.setDataCategory(nz(rs.getString("data_category")));
	        dto.setTimes(nz(rs.getString("times")));
	        dto.setHomeTeamName(nz(rs.getString("home_team_name")));
	        dto.setAwayTeamName(nz(rs.getString("away_team_name")));

	        dto.setHomeScore(toInteger(rs, "home_score"));
	        dto.setAwayScore(toInteger(rs, "away_score"));

	        dto.setHomeExp(toDouble(rs, "home_exp"));
	        dto.setAwayExp(toDouble(rs, "away_exp"));

	        dto.setHomeShootIn(toInteger(rs, "home_shoot_in"));
	        dto.setAwayShootIn(toInteger(rs, "away_shoot_in"));

	        Timestamp recordTime = rs.getTimestamp("record_time");
	        Timestamp updateTime = rs.getTimestamp("update_time");
	        String recordTimeStr = null;
	        if (recordTime != null) {
	            recordTimeStr = recordTime.toInstant().atOffset(ZoneOffset.UTC).toString();
	        } else if (updateTime != null) {
	            recordTimeStr = updateTime.toInstant().atOffset(ZoneOffset.UTC).toString();
	        }
	        dto.setRecordTime(recordTimeStr);

	        String goalTime = rs.getString("goal_time");
	        dto.setLink(goalTime != null && !goalTime.isBlank() ? goalTime : null);

	        // スラグはここではまだ null。後で Java 側で埋める
	        dto.setHomeSlug(null);
	        dto.setAwaySlug(null);

	        return dto;
	    }
	};

	// ========= helpers =========

	private static String nz(String s) {
		return s == null ? "" : s;
	}

	private static Integer toInteger(ResultSet rs, String col) throws SQLException {
		int v = rs.getInt(col);
		return rs.wasNull() ? null : v;
	}

	private static Double toDouble(ResultSet rs, String col) throws SQLException {
		double v = rs.getDouble(col);
		return rs.wasNull() ? null : v;
	}

	// ========= SQL（$1 → :pattern に変更） =========

	private static final String SQL = """
		    WITH data_norm AS (
		      SELECT
		        d.seq::bigint AS seq_big,
		        d.seq::text   AS seq,
		        NULLIF(TRIM(d.data_category), '')           AS data_category,
		        NULLIF(TRIM(d.times), '')                   AS times,
		        NULLIF(TRIM(d.home_team_name), '')          AS home_team_name,
		        NULLIF(TRIM(d.away_team_name), '')          AS away_team_name,

		        /* スコア: 非数字と '.' '-' を除去 → float → floor → int */
		        CASE
		          WHEN NULLIF(regexp_replace(TRIM(d.home_score), '[-0-9.]', '', 'g'), '') IS NULL
		          THEN NULL
		          ELSE floor(NULLIF(regexp_replace(TRIM(d.home_score), '[^0-9.-]', '', 'g'), '')::float)::int
		        END AS home_score,
		        CASE
		          WHEN NULLIF(regexp_replace(TRIM(d.away_score), '[-0-9.]', '', 'g'), '') IS NULL
		          THEN NULL
		          ELSE floor(NULLIF(regexp_replace(TRIM(d.away_score), '[^0-9.-]', '', 'g'), '')::float)::int
		        END AS away_score,

		        /* xG: float 正規化（数字と '.' '-' のみ残す） */
		        NULLIF(regexp_replace(TRIM(d.home_exp),      '[^0-9.-]', '', 'g'), '')::float AS home_exp,
		        NULLIF(regexp_replace(TRIM(d.away_exp),      '[^0-9.-]', '', 'g'), '')::float AS away_exp,

		        /* 枠内シュート: int 正規化（数字と '-' のみ残す） */
		        NULLIF(regexp_replace(TRIM(d.home_shoot_in), '[^0-9-]',  '', 'g'), '')::int   AS home_shoot_in,
		        NULLIF(regexp_replace(TRIM(d.away_shoot_in), '[^0-9-]',  '', 'g'), '')::int   AS away_shoot_in,

		        d.record_time,
		        d.update_time,
		        NULLIF(TRIM(d.goal_time), '')               AS goal_time,

		        /* data_category → country / league 抜き出し（例: "日本: J1 リーグ - ラウンド 12"） */
		        btrim(split_part(COALESCE(NULLIF(TRIM(d.data_category), ''), ''), ':', 1)) AS dc_country,
		        btrim(split_part(split_part(COALESCE(NULLIF(TRIM(d.data_category), ''), ''), ':', 2), '-', 1)) AS dc_league,

		        /* チーム名の正規化キー（全角/nbsp→半角、空白畳み、lower） */
		        lower(
		          btrim(
		            regexp_replace(
		              translate(TRIM(d.home_team_name), CHR(12288) || CHR(160), '  '),
		              '[[:space:]]+', ' ', 'g'
		            )
		          )
		        ) AS home_key,
		        lower(
		          btrim(
		            regexp_replace(
		              translate(TRIM(d.away_team_name), CHR(12288) || CHR(160), '  '),
		              '[[:space:]]+', ' ', 'g'
		            )
		          )
		        ) AS away_key
		      FROM public.data d
		      WHERE d.home_team_name IS NOT NULL
		        AND d.away_team_name IS NOT NULL
		        AND d.data_category LIKE :pattern
		        AND (d.record_time AT TIME ZONE 'Asia/Tokyo')::date = (now() AT TIME ZONE 'Asia/Tokyo')::date
		    ),
		    data_keyed AS (
		      SELECT
		        dn.*,
		        CASE WHEN dn.home_key <= dn.away_key
		             THEN dn.home_key || '|' || dn.away_key
		             ELSE dn.away_key || '|' || dn.home_key
		        END AS pair_key
		      FROM data_norm dn
		    ),
		    latest_any AS (
		      SELECT pair_key, MAX(seq_big) AS seq_any
		      FROM data_keyed
		      GROUP BY pair_key
		    ),
		    latest_rows AS (
		      SELECT dk.*
		      FROM data_keyed dk
		      JOIN latest_any la
		        ON la.pair_key = dk.pair_key
		       AND la.seq_any  = dk.seq_big
		    )
		    SELECT
		      lr.seq,
		      lr.data_category,
		      lr.times,
		      lr.home_team_name,
		      lr.away_team_name,
		      lr.home_score,
		      lr.away_score,
		      lr.home_exp,
		      lr.away_exp,
		      lr.home_shoot_in,
		      lr.away_shoot_in,
		      lr.record_time,
		      lr.update_time,
		      lr.goal_time
		    FROM latest_rows lr
		    WHERE lr.times IS NOT NULL
		      AND lr.times NOT ILIKE '%終了%'
		    ORDER BY
		      COALESCE(lr.data_category, '') ASC,
		      lr.seq DESC
		    """;
}
