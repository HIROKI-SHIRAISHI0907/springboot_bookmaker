package dev.web.api.dashboard.support;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 一般ユーザー画面に出さない国・リーグの判定（2026-11-23 追加）
 *
 * <p>次のどちらかに当たる国・リーグは、一般ユーザー画面（トップ・試合一覧・お気に入り・チーム・試合詳細）に出さない。
 * DB のデータは消さないので、設定を戻せば再び表示される。管理画面には影響しない。</p>
 * <ul>
 *   <li>スクレイピング対象外: all_league_scrape_master の同じ国・リーグの行がすべて disp_flg = '1'
 *       （管理画面「スクレイピング対象データ設定」でチェックを外したもの）。
 *       all_league_scrape_master に無い国・リーグ（大会など）は表示する。</li>
 *   <li>管理者が強制非表示にした国・リーグ: admin_favorites_master の disp_flg = '1'（level 1 = 国、level 2 = リーグ）</li>
 * </ul>
 * <p>マスタは別 DB（soccer_bm_master）のため、一覧を読み込んでメモリで判定する（{@value #TTL_MS} ms キャッシュ）。</p>
 */
@Slf4j
@Component
public class LeagueVisibility {

	/** キャッシュの有効時間 */
	static final long TTL_MS = 60_000L;

	/** 国全体を非表示にするときのリーグ側のキー */
	private static final String ANY_LEAGUE = "*";

	/**
	 * マスタ DB の SQL で使う「表示する国・リーグか」の条件（別名 m の country・league を見る）。
	 * all_league_scrape_master に行が無ければ表示、あれば 1 行でも disp_flg = '0' なら表示。
	 */
	public static final String SCRAPE_VISIBLE = " " + """
			COALESCE((
			    SELECT BOOL_OR(COALESCE(s.disp_flg, '0') = '0')
			    FROM all_league_scrape_master s
			    WHERE BTRIM(s.country) = BTRIM(m.country)
			      AND BTRIM(s.league)  = BTRIM(m.league)
			), TRUE)
			""";

	private static final String HIDDEN_SQL = """
			SELECT BTRIM(country) AS country, BTRIM(league) AS league
			FROM all_league_scrape_master
			WHERE NULLIF(BTRIM(country), '') IS NOT NULL
			  AND NULLIF(BTRIM(league), '') IS NOT NULL
			GROUP BY BTRIM(country), BTRIM(league)
			HAVING NOT BOOL_OR(COALESCE(disp_flg, '0') = '0')
			UNION
			SELECT BTRIM(country) AS country,
			       CASE WHEN level = '1' THEN '*' ELSE BTRIM(league) END AS league
			FROM admin_favorites_master
			WHERE disp_flg = '1'
			  AND level IN ('1', '2')
			  AND NULLIF(BTRIM(country), '') IS NOT NULL
			""";

	private final NamedParameterJdbcTemplate masterJdbcTemplate;

	private volatile Set<String> hidden = Collections.emptySet();

	private volatile long loadedAt = 0L;

	public LeagueVisibility(@Qualifier("webMasterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate) {
		this.masterJdbcTemplate = masterJdbcTemplate;
	}

	/** 一般ユーザー画面に出さない国・リーグか（国・リーグが空なら判定しない＝表示） */
	public boolean isHidden(String country, String league) {
		String c = DashboardSupport.norm(country);
		String l = DashboardSupport.norm(league);
		if (c.isEmpty()) {
			return false;
		}
		Set<String> set = hiddenSet();
		return set.contains(key(c, ANY_LEAGUE)) || (!l.isEmpty() && set.contains(key(c, l)));
	}

	/** カテゴリ「国: リーグ - ラウンド N」で判定（国が読めなければ表示） */
	public boolean isHiddenCategory(String dataCategory) {
		String[] cl = DashboardSupport.splitCategory(dataCategory);
		return cl != null && isHidden(cl[0], cl[1]);
	}

	/** 設定を変えたときにキャッシュを捨てる */
	public void invalidate() {
		this.loadedAt = 0L;
	}

	private Set<String> hiddenSet() {
		long now = System.currentTimeMillis();
		if (now - this.loadedAt < TTL_MS) {
			return this.hidden;
		}
		synchronized (this) {
			if (now - this.loadedAt < TTL_MS) {
				return this.hidden;
			}
			try {
				Set<String> set = new HashSet<>();
				this.masterJdbcTemplate.query(HIDDEN_SQL, new MapSqlParameterSource(), rs -> {
					set.add(key(DashboardSupport.norm(rs.getString("country")),
							ANY_LEAGUE.equals(rs.getString("league")) ? ANY_LEAGUE
									: DashboardSupport.norm(rs.getString("league"))));
				});
				this.hidden = Collections.unmodifiableSet(set);
				this.loadedAt = now;
				log.info("[LeagueVisibility] 非表示の国・リーグ {} 件を読み込みました", set.size());
			} catch (Exception e) {
				// 読めなかったときは前回の結果を使い、少し後に読み直す
				log.warn("[LeagueVisibility] 非表示の国・リーグを読み込めませんでした（前回の結果を使います） e={}", e.toString());
				this.loadedAt = now - TTL_MS + 10_000L;
			}
			return this.hidden;
		}
	}

	private static String key(String country, String league) {
		return country + "|" + league;
	}
}
