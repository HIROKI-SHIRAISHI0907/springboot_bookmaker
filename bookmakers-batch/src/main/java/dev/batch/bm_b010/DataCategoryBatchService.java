package dev.batch.bm_b010;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.batch.repository.bm.BookDataRepository;
import dev.batch.repository.master.CountryLeagueMasterBatchRepository;
import dev.batch.repository.master.FutureMasterRepository;
import dev.common.entity.CountryLeagueMasterEntity;

/**
 * data_category発番処理（batch: B010 ほか）
 *
 * <p>決め方（2026-10 見直し後・同名チーム対策済み）</p>
 * <ol>
 *   <li>同じ試合（同じ match_id / 同じカードで record_time が基準時刻の前後6時間）の static_data に、
 *       ラウンドまで入った完成したカテゴリがあればそれを使う。</li>
 *   <li>同じ試合の future_master の予定（mid 一致 → 同じカードで基準時刻の前後1日）の game_team_category を取る。
 *       試合予定スクレイピングが試合ページのパンくずから「国: リーグ - ラウンド N」で登録するので、これが一番確か。</li>
 *   <li>国・リーグ: CSV（国: リーグ …）→ future_master（国: リーグ …）→ マスタ（両チーム共通 → 片方）→ static_data の同じチーム。</li>
 *   <li>ラウンド: future_master → CSV の順。ただし<b>決めた国・リーグと同じ大会のものだけ</b>使う
 *       （例: 国・リーグが「日本: J3 リーグ」なのに予定が「日本: 天皇杯 - ラウンド 3」なら使わない）。
 *       future_master が古い形式（「プレミアリーグ - ラウンド 7」のように国なし）でもリーグ名が一致すれば使う。</li>
 *   <li>ラウンドまで決まったら、同じ試合の static_data と future_master を更新する。
 *       future_master は国なし・ラウンドなし・仮の値の行だけ直す（パンくず由来の値は上書きしない）。</li>
 *   <li>国・リーグが分からなければ「XXX: YYY - ラウンド 0」、ラウンドが分からなければ「国: リーグ」を返し、他の行は書き換えない。</li>
 * </ol>
 * 以前の修正の経緯:
 * <ul>
 *   <li>見る範囲・書き換える範囲を「同じ試合」に限定（以前は同じカードの全期間で、去年のラウンドが混ざった）。</li>
 *   <li>「XXX: YYY - ラウンド 0」を完成したカテゴリと見なさない。</li>
 *   <li>マスタより CSV を優先（同名チーム「エヴァートン」でチリの試合がイングランドになっていた）。</li>
 *   <li>マスタは両チームの候補の共通部分で決め、同名チームで候補が複数あるときは当て推量で選ばない
 *       （「LDU キト vs ｳﾆﾍﾞﾙｼﾀﾞ･ｶﾄﾘｶ」がチリになっていた。ホームがマスタに無く、アウェーの同名チームのチリが選ばれた）。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Component
public class DataCategoryBatchService {

	private static final String ROUND = "ラウンド";

	/** 国・リーグが分からないときの仮の値 */
	public static final String PLACEHOLDER = "XXX: YYY - " + ROUND + " 0";

	/** 「国: リーグ …」の形 */
	private static final Pattern COUNTRY_LEAGUE = Pattern.compile("^\\s*[^:：]+[:：]\\s*\\S.*$");

	/** 仮の値（XXX:） */
	private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("^\\s*(XXX|ＸＸＸ)\\s*[:：]");

	private static final Pattern ROUND_NO = Pattern.compile(ROUND + "\\s*(\\d+)");

	@Autowired
	private BookDataRepository bookDataRepository;

	@Autowired
	private CountryLeagueMasterBatchRepository countryLeagueMasterBatchRepository;

	@Autowired
	private FutureMasterRepository futureMasterRepository;

	/**
	 * 互換用（match_id・記録時刻なし＝現在時刻を基準に「同じ試合」を探す）。
	 * 新しいコードは {@link #create(String, String, String, String, String)} を使うこと。
	 */
	public synchronized String create(String home, String away, String dataCategory) throws IllegalAccessException {
		return create(home, away, dataCategory, null, null);
	}

	/**
	 * static_dataテーブルのdata_categoryを生成する。
	 *
	 * @param home 対象試合のホームチーム名（空不可）
	 * @param away 対象試合のアウェーチーム名（空不可）
	 * @param dataCategory CSV のデータカテゴリ（null 可）
	 * @param matchId 対象試合ID（null 可）
	 * @param baseTime 対象行の記録時刻（"yyyy-MM-dd HH:mm:ss" JST。null・空なら現在時刻）
	 * @return 生成されたdataCategory（例: "日本: J1 リーグ - ラウンド 1"）
	 * @throws IllegalAccessException
	 */
	public synchronized String create(String home, String away, String dataCategory, String matchId,
			String baseTime) throws IllegalAccessException {
		if (isBlank(home) || isBlank(away)) {
			throw new IllegalArgumentException("チーム名が空のため data_category を決められません: home=[" + home
					+ "], away=[" + away + "]");
		}

		// 1) 同じ試合の static_data に完成したカテゴリがあればそれを使う
		List<DataCategoryDTO> existDto = bookDataRepository.findDataCategoryForMatch(home, away, matchId, baseTime);
		String complete = searchCompleteDataCategoryChk(existDto);
		if (complete != null) {
			update(home, away, matchId, baseTime, complete);
			return complete;
		}

		// 2) 同じ試合の future_master の予定（mid 一致 → 同じカードで前後1日）
		String futureCategory = findFutureCategory(home, away, matchId, baseTime);

		// 3) 国・リーグ
		String base = resolveBase(home, away, dataCategory, futureCategory, baseTime);
		if (base == null) {
			// 国・リーグが分からない。仮の値を返すが、他の行・future_master は書き換えない
			return PLACEHOLDER;
		}

		// 4) ラウンド: future_master → CSV（決めた国・リーグと同じ大会のものだけ）
		String round = null;
		for (String candidate : new String[] { futureCategory, dataCategory }) {
			if (isCompatible(base, candidate)) {
				String r = findRound(candidate);
				if (r != null && !"0".equals(r)) {
					round = r;
					break;
				}
			}
		}
		if (round == null) {
			// ラウンドが分からない。国・リーグだけ返し、他の行は書き換えない
			return base;
		}

		String category = base + " - " + ROUND + " " + round;
		update(home, away, matchId, baseTime, category);
		return category;
	}

	/** future_master の同じ試合の game_team_category（mid 一致 → 同じカードで基準時刻の前後1日）。無ければ null */
	private String findFutureCategory(String home, String away, String matchId, String baseTime) {
		String c = null;
		if (!isBlank(matchId)) {
			c = futureMasterRepository.findGameTeamCategoryByMid(matchId);
		}
		if (isBlank(c)) {
			c = futureMasterRepository.findGameTeamCategoryNearTime(home, away, baseTime);
		}
		if (isBlank(c) || PLACEHOLDER_PATTERN.matcher(c).find()) {
			return null;
		}
		return c.trim();
	}

	/**
	 * 「国: リーグ」を決める。
	 * <ol>
	 *   <li>CSV のカテゴリが「国: リーグ …」の形ならその「国: リーグ」（試合ページの表記）</li>
	 *   <li>future_master が「国: リーグ …」の形ならその「国: リーグ」（試合予定ページのパンくず）</li>
	 *   <li>country_league_master: 両チームの候補の共通部分（1つに決まるときだけ。同名チームで複数に当たるときは決めない）</li>
	 *   <li>static_data の同じチームの行（両チームが同じ値、または片方だけ取れてそのチーム名が一意のときだけ）</li>
	 * </ol>
	 * 3〜5 は、CSV / future_master に国なしのリーグ名（例: 「J3 リーグ」）があれば、それと一致するものだけ使う。
	 * @return 「国: リーグ」。分からなければ null
	 */
	private String resolveBase(String home, String away, String dataCategory, String futureCategory, String baseTime) {
		if (isUsable(dataCategory)) {
			return stripRound(dataCategory);
		}
		if (isUsable(futureCategory)) {
			return stripRound(futureCategory);
		}

		// 国なしのリーグ名（照合用）
		String leagueOnly = !isBlank(dataCategory) ? stripRound(dataCategory)
				: (!isBlank(futureCategory) ? stripRound(futureCategory) : null);

		// country_league_master（両チームの候補の共通部分 → 片方だけ分かる場合はその候補が1つのときだけ）
		List<String> masterCandidates = masterCandidates(home, away, leagueOnly);
		if (masterCandidates.size() == 1) {
			return masterCandidates.get(0);
		}
		if (masterCandidates.size() > 1) {
			// 同名チームが複数の国・リーグにいて決められない（例: ｳﾆﾍﾞﾙｼﾀﾞ･ｶﾄﾘｶ = チリ / エクアドル）。
			// 当て推量で片方を選ぶと別の国になるため、ここでは決めない。
			return null;
		}

		// static_data の同じチームの行（両チームが同じ値のとき、または片方しか取れずそのチーム名が一意のときだけ）
		String homeMain = mainCountryLeague(home, baseTime, leagueOnly);
		String awayMain = mainCountryLeague(away, baseTime, leagueOnly);
		if (homeMain != null && awayMain != null) {
			return squash(homeMain).equals(squash(awayMain)) ? homeMain : null;
		}
		String one = homeMain != null ? homeMain : awayMain;
		String oneTeam = homeMain != null ? home : away;
		if (one != null && teamCountryLeagues(oneTeam, null).size() <= 1) {
			return one;
		}
		return null;
	}

	/**
	 * country_league_master から「国: リーグ」の候補を求める。
	 * <ul>
	 *   <li>両チームの候補の共通部分があればそれ（同名チームがいても、相手チームと同じリーグに絞れる）</li>
	 *   <li>片方のチームしかマスタに無ければ、そのチームの候補</li>
	 *   <li>両チームとも候補があるのに共通部分が無ければ、両方の候補（＝決められない）</li>
	 * </ul>
	 * 候補は有効行（del_flg = '0'）を優先し、無ければ削除済みの行も使う（チームの所属国の手がかりとしては有効なため）。
	 */
	private List<String> masterCandidates(String home, String away, String leagueOnly) {
		Set<String> h = teamCountryLeagues(home, leagueOnly);
		Set<String> a = teamCountryLeagues(away, leagueOnly);
		if (h.isEmpty() && a.isEmpty()) {
			return List.of();
		}
		if (h.isEmpty()) {
			return new ArrayList<>(a);
		}
		if (a.isEmpty()) {
			return new ArrayList<>(h);
		}
		Set<String> common = new LinkedHashSet<>(h);
		common.retainAll(a);
		if (!common.isEmpty()) {
			return new ArrayList<>(common);
		}
		Set<String> union = new LinkedHashSet<>(h);
		union.addAll(a);
		return new ArrayList<>(union);
	}

	/** チームの「国: リーグ」の一覧（有効行優先。leagueOnly があればリーグ名が一致するものだけ） */
	private Set<String> teamCountryLeagues(String team, String leagueOnly) {
		Set<String> active = new LinkedHashSet<>();
		Set<String> all = new LinkedHashSet<>();
		List<CountryLeagueMasterEntity> rows = countryLeagueMasterBatchRepository.findCountryLeaguesByTeam(team);
		if (rows != null) {
			for (CountryLeagueMasterEntity e : rows) {
				if (e == null || isBlank(e.getCountry()) || isBlank(e.getLeague())) {
					continue;
				}
				String b = e.getCountry().trim() + ": " + e.getLeague().trim();
				if (!isBlank(leagueOnly) && !sameLeague(b, leagueOnly)) {
					continue;
				}
				all.add(b);
				if ("0".equals(e.getDelFlg())) {
					active.add(b);
				}
			}
		}
		return active.isEmpty() ? all : active;
	}

	/** static_data からチームの主な「国: リーグ」（leagueOnly があれば一致するものだけ）。無ければ null */
	private String mainCountryLeague(String team, String baseTime, String leagueOnly) {
		String b = bookDataRepository.findMainCountryLeagueByTeam(team, baseTime);
		if (b == null || !isUsable(b)) {
			return null;
		}
		if (!isBlank(leagueOnly) && !sameLeague(b, leagueOnly)) {
			return null;
		}
		return b.trim();
	}

	/**
	 * candidate（「国: リーグ - ラウンド N」または国なしの「リーグ - ラウンド N」）が base（「国: リーグ」）と同じ大会か
	 */
	private static boolean isCompatible(String base, String candidate) {
		if (isBlank(base) || isBlank(candidate) || PLACEHOLDER_PATTERN.matcher(candidate).find()) {
			return false;
		}
		String cBase = stripRound(candidate);
		if (COUNTRY_LEAGUE.matcher(cBase).matches()) {
			return squash(cBase).equals(squash(base));
		}
		return sameLeague(base, cBase);
	}

	/**
	 * 同じ試合の static_data と future_master を完成したカテゴリに更新する
	 * （future_master は国なし・ラウンドなし・仮の値の行だけ）
	 */
	private void update(String home, String away, String matchId, String baseTime, String category)
			throws IllegalAccessException {
		try {
			bookDataRepository.updateDataCategoryForMatch(category, home, away, matchId, baseTime);
			futureMasterRepository.updateGameTeamCategoryForMatch(category, home, away, matchId, baseTime);
		} catch (Exception e) {
			throw new IllegalAccessException("システムエラー: " + e.getMessage());
		}
	}

	/**
	 * 「ラウンド」を含んだ完全なdata_category（仮の値 XXX・ラウンド 0 は除く）
	 */
	private String searchCompleteDataCategoryChk(List<DataCategoryDTO> existDto) {
		if (existDto == null) {
			return null;
		}
		for (DataCategoryDTO dto : existDto) {
			String c = dto.getDataCategory();
			if (c != null && c.contains(ROUND) && isUsable(c) && !"0".equals(findRound(c))) {
				return c;
			}
		}
		return null;
	}

	/** 「国: リーグ - ラウンド N」→「国: リーグ」 */
	private static String stripRound(String category) {
		String c = category.trim();
		int dash = c.indexOf(" - ");
		return (dash >= 0 ? c.substring(0, dash) : c).trim();
	}

	/** 「日本: J3 リーグ」のリーグ部分が leagueOnly（例: 「J3 リーグ」）と一致するか（空白・全半角を無視） */
	private static boolean sameLeague(String base, String leagueOnly) {
		return squash(leaguePart(base)).equals(squash(leaguePart(leagueOnly)));
	}

	/** 「国: リーグ」→「リーグ」（国なしならそのまま） */
	private static String leaguePart(String s) {
		int i = s.indexOf(':');
		if (i < 0) {
			i = s.indexOf('：');
		}
		return i >= 0 ? s.substring(i + 1) : s;
	}

	private static String squash(String s) {
		return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC).replaceAll("\\s+", "");
	}

	/** 「国: リーグ …」の形で、仮の値でない */
	private static boolean isUsable(String category) {
		return category != null && COUNTRY_LEAGUE.matcher(category).matches()
				&& !PLACEHOLDER_PATTERN.matcher(category).find();
	}

	/** 「ラウンド N」の N（無ければ null） */
	private static String findRound(String category) {
		if (category == null) {
			return null;
		}
		Matcher m = ROUND_NO.matcher(category);
		return m.find() ? m.group(1) : null;
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

}
