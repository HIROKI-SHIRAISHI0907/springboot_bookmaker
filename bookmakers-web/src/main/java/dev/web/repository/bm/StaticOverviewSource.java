package dev.web.repository.bm;

/**
 * 統計（surface_overview_match）がまだ無いリーグのための、static_data から作る代わりのデータ（2026-11-28 追加）。
 *
 * <p>surface_overview_match と同じ列名の副問い合わせ（チーム視点で 1 試合 2 行）を返す。
 * 画面の SQL は「FROM surface_overview_match」を {@link #from(String)} に置き換えるだけで同じように使える。
 * season は固定の {@link #SEASON}（"STATIC"）なので、WHERE season = :season もそのまま使える。</p>
 *
 * <p>統計ができたリーグは surface_overview_match を使う（こちらは毎回 static_data を集計するので重い）。
 * 呼び出し側は「最新シーズンが無いときだけ {@link #SEASON} を渡す」こと。</p>
 *
 * <p>作り方（bookmakers-app BM_M031 SurfaceOverviewStat と同じ考え方）</p>
 * <ul>
 *   <li>対象: 直近 {@value #WINDOW_DAYS} 日の、このリーグ（data_category の「国: リーグ」）の行。
 *       「ラウンド 1」があれば一番新しいラウンド 1 の 14 日前から（前のシーズンを混ぜない）。取得エラー行は除く。</li>
 *   <li>1 試合 = match_id（無ければ ホーム・アウェー・ラウンド）。終了済（またはペナルティ）の行がある試合だけ。行は通番の数値順。</li>
 *   <li>スコア: ペナルティ以外で、スコアの入った最後の行。PK: ペナルティの最後の行（試合本体が同点のときだけ）。</li>
 *   <li>前半: 最初のハーフタイム（第一ハーフ）の行（最終スコア以下のときだけ）。後半 = 最終 − 前半。</li>
 *   <li>先制: 両者無得点 N / 最初に点が入った行が片方だけなら T / O / それ以外 U。</li>
 *   <li>推移が分かる（flow_known）: ペナルティ以外の行が 2 行以上。リードされた（ever_trailed）: どこかの行で負けていた。</li>
 *   <li>phase（序盤・中盤・終盤）は総ラウンド数が要るので null（そのアイコンは出ない）。</li>
 *   <li>team_rank: 最後の行のサイト表示の順位。</li>
 * </ul>
 * 取れていない試合があると、勝ち点などは実際より少なくなる（画面に注意書きを出す）。
 * :country / :league のパラメータを使う。
 * @author shiraishitoshio
 */
public final class StaticOverviewSource {

	/** 終了済の試合から作ったデータのシーズン（固定値） */
	public static final String SEASON = "STATIC";

	/** 見る日数 */
	static final int WINDOW_DAYS = 300;

	private StaticOverviewSource() {
	}

	/** 統計から作ったデータか */
	public static boolean isStatic(String season) {
		return SEASON.equals(season);
	}

	/** FROM に書くもの（統計なら surface_overview_match、STATIC なら代わりの副問い合わせ） */
	public static String from(String season) {
		return isStatic(season) ? DERIVED : "surface_overview_match";
	}

	private static String key(String expr) {
		return "normalize(BTRIM(" + expr + "), NFKC)";
	}

	private static final String ORDER_ASC = "ORDER BY seq_no NULLS LAST, record_time";
	private static final String ORDER_DESC = "ORDER BY seq_no DESC NULLS LAST, record_time DESC";

	private static String side(boolean home) {
		String t = home ? "home" : "away";
		String o = home ? "away" : "home";
		String gf = home ? "fh" : "fa";
		String ga = home ? "fa" : "fh";
		String hf = home ? "hh" : "hta";
		String ha = home ? "hta" : "hh";
		String f1f = home ? "f1h" : "f1a";
		String f1a = home ? "f1a" : "f1h";
		String pf = home ? "ph" : "pa";
		String pa = home ? "pa" : "ph";
		return " SELECT '" + SEASON + "'::text AS season, country, league,"
				+ " " + t + " AS team, " + o + " AS opponent, '" + (home ? "H" : "A") + "'::text AS ha,"
				+ " match_id, round_no, NULL::text AS phase, match_time,"
				+ " " + gf + " AS goals_for, " + ga + " AS goals_against,"
				+ " CASE WHEN " + gf + " > " + ga + " THEN 'W' WHEN " + gf + " < " + ga + " THEN 'L' ELSE 'D' END AS result,"
				+ " CASE WHEN " + gf + " > " + ga + " THEN 3 WHEN " + gf + " < " + ga + " THEN 0 ELSE 1 END AS points,"
				+ " CASE WHEN ht_ok THEN " + hf + " END AS goals_for_1st,"
				+ " CASE WHEN ht_ok THEN " + gf + " - " + hf + " END AS goals_for_2nd,"
				+ " CASE WHEN ht_ok THEN " + ha + " END AS goals_against_1st,"
				+ " CASE WHEN ht_ok THEN " + ga + " - " + ha + " END AS goals_against_2nd,"
				+ " CASE WHEN " + gf + " = 0 AND " + ga + " = 0 THEN 'N'"
				+ "      WHEN " + f1f + " IS NULL THEN 'U'"
				+ "      WHEN " + f1f + " > 0 AND " + f1a + " = 0 THEN 'T'"
				+ "      WHEN " + f1a + " > 0 AND " + f1f + " = 0 THEN 'O' ELSE 'U' END AS first_goal,"
				+ " (n_regular >= 2) AS flow_known,"
				+ " COALESCE(" + (home ? "away_led" : "home_led") + ", FALSE) AS ever_trailed,"
				+ " COALESCE(" + (home ? "home_led" : "away_led") + ", FALSE) AS ever_led,"
				+ " CASE WHEN BTRIM(" + t + "_rank) ~ '^[0-9]+$' THEN BTRIM(" + t + "_rank)::INTEGER END AS team_rank,"
				+ " (" + pf + " IS NOT NULL AND fh = fa) AS pk_flg,"
				+ " CASE WHEN fh = fa THEN " + pf + " END AS pk_goals_for,"
				+ " CASE WHEN fh = fa THEN " + pa + " END AS pk_goals_against"
				+ " FROM gg";
	}

	private static final String DERIVED = "(WITH y AS ("
			+ " SELECT BTRIM(d.home_team_name) AS home_team_name, BTRIM(d.away_team_name) AS away_team_name,"
			+ "        BTRIM(d.times) AS times, d.record_time, NULLIF(BTRIM(d.match_id), '') AS match_id,"
			+ "        CAST(d.home_rank AS text) AS home_rank, CAST(d.away_rank AS text) AS away_rank,"
			+ "        CASE WHEN BTRIM(d.home_score) ~ '^[0-9]+$' THEN BTRIM(d.home_score)::INTEGER END AS hs,"
			+ "        CASE WHEN BTRIM(d.away_score) ~ '^[0-9]+$' THEN BTRIM(d.away_score)::INTEGER END AS aws,"
			+ "        NULLIF(SUBSTRING(d.seq_key FROM '([0-9]+)$'), '')::BIGINT AS seq_no,"
			+ "        NULLIF(SUBSTRING(normalize(d.data_category, NFKC) FROM 'ラウンド\\s*([0-9]+)'), '')::INTEGER AS round_no,"
			+ "        BTRIM(split_part(d.data_category, ':', 1)) AS country,"
			+ "        BTRIM(split_part(split_part(d.data_category, ':', 2), ' - ', 1)) AS league,"
			+ "        (COALESCE(d.times, '') LIKE '%ペナルティ%') AS pk"
			+ " FROM static_data d"
			+ " WHERE d.record_time >= CURRENT_TIMESTAMP - INTERVAL '" + WINDOW_DAYS + " days'"
			+ "   AND " + key("split_part(d.data_category, ':', 1)") + " = " + key("CAST(:country AS text)")
			+ "   AND " + key("split_part(split_part(d.data_category, ':', 2), ' - ', 1)") + " = " + key("CAST(:league AS text)")
			+ "   AND COALESCE(BTRIM(d.goal_time), '') <> '取得エラー'"
			+ "   AND COALESCE(BTRIM(d.goal_team_member), '') <> '取得エラー'"
			+ "), st AS ("
			+ " SELECT COALESCE(MAX(record_time) FILTER (WHERE round_no = 1) - INTERVAL '14 days',"
			+ "                 CURRENT_TIMESTAMP - INTERVAL '" + WINDOW_DAYS + " days') AS from_time FROM y"
			+ "), z AS ("
			+ " SELECT y.*, COALESCE(y.match_id, " + key("y.home_team_name") + " || '|' || " + key("y.away_team_name")
			+ "          || '|' || COALESCE(y.round_no::text, '')) AS mk"
			+ " FROM y, st WHERE y.record_time >= st.from_time"
			+ "), g AS ("
			+ " SELECT mk, MAX(round_no) AS round_no, MAX(country) AS country, MAX(league) AS league, MAX(match_id) AS match_id,"
			+ "  (ARRAY_AGG(home_team_name " + ORDER_DESC + ") FILTER (WHERE NOT pk))[1] AS home,"
			+ "  (ARRAY_AGG(away_team_name " + ORDER_DESC + ") FILTER (WHERE NOT pk))[1] AS away,"
			+ "  (ARRAY_AGG(hs " + ORDER_DESC + ") FILTER (WHERE NOT pk AND hs IS NOT NULL AND aws IS NOT NULL))[1] AS fh,"
			+ "  (ARRAY_AGG(aws " + ORDER_DESC + ") FILTER (WHERE NOT pk AND hs IS NOT NULL AND aws IS NOT NULL))[1] AS fa,"
			+ "  (ARRAY_AGG(hs " + ORDER_ASC + ") FILTER (WHERE NOT pk AND times IN ('ハーフタイム', '第一ハーフ') AND hs IS NOT NULL AND aws IS NOT NULL))[1] AS hh,"
			+ "  (ARRAY_AGG(aws " + ORDER_ASC + ") FILTER (WHERE NOT pk AND times IN ('ハーフタイム', '第一ハーフ') AND hs IS NOT NULL AND aws IS NOT NULL))[1] AS hta,"
			+ "  (ARRAY_AGG(hs " + ORDER_ASC + ") FILTER (WHERE NOT pk AND hs + aws > 0))[1] AS f1h,"
			+ "  (ARRAY_AGG(aws " + ORDER_ASC + ") FILTER (WHERE NOT pk AND hs + aws > 0))[1] AS f1a,"
			+ "  BOOL_OR(hs > aws) FILTER (WHERE NOT pk) AS home_led,"
			+ "  BOOL_OR(aws > hs) FILTER (WHERE NOT pk) AS away_led,"
			+ "  COUNT(*) FILTER (WHERE NOT pk) AS n_regular,"
			+ "  (ARRAY_AGG(record_time " + ORDER_DESC + ") FILTER (WHERE NOT pk))[1] AS match_time,"
			+ "  (ARRAY_AGG(home_rank " + ORDER_DESC + ") FILTER (WHERE NOT pk))[1] AS home_rank,"
			+ "  (ARRAY_AGG(away_rank " + ORDER_DESC + ") FILTER (WHERE NOT pk))[1] AS away_rank,"
			+ "  (ARRAY_AGG(hs " + ORDER_DESC + ") FILTER (WHERE pk AND hs IS NOT NULL AND aws IS NOT NULL))[1] AS ph,"
			+ "  (ARRAY_AGG(aws " + ORDER_DESC + ") FILTER (WHERE pk AND hs IS NOT NULL AND aws IS NOT NULL))[1] AS pa"
			+ " FROM z"
			+ " WHERE mk IN (SELECT mk FROM z WHERE times = '終了済' OR pk)"
			+ " GROUP BY mk"
			+ "), gg AS ("
			+ " SELECT g.*, (hh IS NOT NULL AND hta IS NOT NULL AND hh <= fh AND hta <= fa) AS ht_ok"
			+ " FROM g WHERE fh IS NOT NULL AND fa IS NOT NULL AND home IS NOT NULL AND away IS NOT NULL"
			+ ")"
			+ side(true)
			+ " UNION ALL"
			+ side(false)
			+ ") som";
}
