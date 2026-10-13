package dev.batch.bm_b010;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.batch.repository.bm.BookDataRepository;
import dev.batch.repository.master.CountryLeagueMasterBatchRepository;
import dev.batch.repository.master.FutureMasterRepository;
import dev.common.entity.CountryLeagueMasterEntity;

/**
 * data_category発番処理
 *
 * <p>2026-10 修正（Web 側 BM_M001 の DataCategoryService と同じ考え方）</p>
 * <ul>
 *   <li>見る範囲・書き換える範囲を「同じ試合」（同じ match_id、または同じカードで記録時刻が前後 6 時間以内）に限定。</li>
 *   <li>future_master も記録時刻の前後 1 日の予定だけを見る・書き換える。</li>
 *   <li>「XXX: YYY - ラウンド 0」を完成したカテゴリと見なさない。</li>
 *   <li>チーム名が空なら例外（呼び出し元で StaticDataRowValidator により事前に除く）。</li>
 * </ul>
 *
 * <p>2026-10 修正2（B010 の終了済データで「J3 リーグ」「日本: J3 リーグ」になる問題）</p>
 * <ul>
 *   <li>FinGettingStat が match_id・記録時刻を渡していなかったため、同じ試合のライブ行（ラウンド付き）を
 *       「現在時刻の前後 6 時間」で探して見つけられていなかった。呼び出し側を 5 引数版に変更。</li>
 *   <li>国・リーグは CSV（国: リーグ）を最優先 → マスタ（両チーム共通 → 片方）→ static_data の同じチーム（同名チーム対策）。</li>
 *   <li>国・リーグの候補に「static_data の同じチームの行」を追加（マスタ・CSV で分からないとき）。
 *       記録時刻の前後 180 日で、そのチームの試合数が一番多い「国: リーグ」を使う（カップ戦より通常リーグが優先される）。</li>
 *   <li>CSV が「J3 リーグ」のように国なしのときは、上の候補のうち末尾がそのリーグ名と一致するものだけ採用する。</li>
 *   <li>ラウンドの候補に「future_master の同じ mid の予定」を追加（時刻がずれていても拾える）。</li>
 * </ul>
 * @author shiraishitoshio
 *
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

		// 1) 同じ試合（同じ match_id / 同じカードで記録時刻の前後6時間）に、ラウンドを含む完成したカテゴリがあればそれを使う
		List<DataCategoryDTO> existDto = bookDataRepository.findDataCategoryForMatch(home, away, matchId, baseTime);
		String complete = searchCompleteDataCategoryChk(existDto);
		if (complete != null) {
			update(home, away, matchId, baseTime, complete);
			return complete;
		}

		// 2) 国・リーグ
		String base = resolveBase(home, away, dataCategory, baseTime);
		if (base == null) {
			// 国・リーグが分からない。仮の値を返すが、他の行・future_master は書き換えない
			return PLACEHOLDER;
		}

		// 3) ラウンド: future_master（同じ mid → 記録時刻の前後1日の予定）→ CSV のカテゴリ
		String round = null;
		if (!isBlank(matchId)) {
			round = findRound(futureMasterRepository.findGameTeamCategoryByMid(matchId));
		}
		if (round == null) {
			round = findRound(futureMasterRepository.findGameTeamCategoryNearTime(home, away, baseTime));
		}
		if (round == null && isUsable(dataCategory)) {
			round = findRound(dataCategory);
		}
		if (round == null || "0".equals(round)) {
			// ラウンドが分からない。国・リーグだけ返し、他の行は書き換えない
			return base;
		}

		String category = base + " - " + ROUND + " " + round;
		update(home, away, matchId, baseTime, category);
		return category;
	}

	/**
	 * 「国: リーグ」を決める。
	 * <ol>
	 *   <li>CSV のカテゴリが「国: リーグ …」の形ならその「国: リーグ」（試合ページの表記が一番確か）</li>
	 *   <li>country_league_master で両チームが同じ国・リーグに属するもの</li>
	 *   <li>country_league_master（ホーム → アウェー）</li>
	 *   <li>static_data の同じチームの行（ホーム → アウェー）。
	 *       CSV が「J3 リーグ」のように国なしなら、末尾がそのリーグ名と一致するものだけ</li>
	 * </ol>
	 * 2026-10 修正: 以前はマスタを CSV より先に見ていたため、同名チーム（例: チリの「エヴァートン」）で
	 * 別の国のリーグ（イングランド: プレミアリーグ）になっていた。
	 * @return 「国: リーグ」。分からなければ null
	 */
	private String resolveBase(String home, String away, String dataCategory, String baseTime) {
		// 2-1) CSV（国: リーグ …）
		if (isUsable(dataCategory)) {
			return stripRound(dataCategory);
		}

		// 2-2) マスタ: 両チームが同じ国・リーグ
		CountryLeagueMasterEntity common = countryLeagueMasterBatchRepository.findCommonCountryLeagueByTeams(home, away);
		if (common != null && !isBlank(common.getCountry()) && !isBlank(common.getLeague())) {
			return common.getCountry().trim() + ": " + common.getLeague().trim();
		}

		// 2-3) マスタ: ホーム → アウェー
		CountryLeagueMasterEntity entity = countryLeagueMasterBatchRepository.findCountryLeagueByTeam(home);
		if (entity == null) {
			entity = countryLeagueMasterBatchRepository.findCountryLeagueByTeam(away);
		}
		String leagueOnly = isBlank(dataCategory) ? null : stripRound(dataCategory);
		if (entity != null && !isBlank(entity.getCountry()) && !isBlank(entity.getLeague())) {
			String b = entity.getCountry().trim() + ": " + entity.getLeague().trim();
			// CSV に国なしのリーグ名があるなら、それと一致するときだけ
			if (leagueOnly == null || leagueOnly.isEmpty() || sameLeague(b, leagueOnly)) {
				return b;
			}
		}

		// 2-4) static_data の同じチームの行
		for (String team : new String[] { home, away }) {
			String b = bookDataRepository.findMainCountryLeagueByTeam(team, baseTime);
			if (b == null || !isUsable(b)) {
				continue;
			}
			if (leagueOnly != null && !leagueOnly.isEmpty() && !sameLeague(b, leagueOnly)) {
				continue;
			}
			return b.trim();
		}
		return null;
	}

	/**
	 * 同じ試合の static_data と future_master を完成したカテゴリに更新する
	 */
	private void update(String home, String away, String matchId, String baseTime, String category)
			throws IllegalAccessException {
		try {
			bookDataRepository.updateDataCategoryForMatch(category, home, away, matchId, baseTime);
			futureMasterRepository.updateGameTeamCategoryNearTime(category, home, away, baseTime);
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
		int dash = category.indexOf(" - ");
		return (dash >= 0 ? category.substring(0, dash) : category).trim();
	}

	/** 「日本: J3 リーグ」のリーグ部分が leagueOnly（例: 「J3 リーグ」）と一致するか（空白・全半角を無視） */
	private static boolean sameLeague(String base, String leagueOnly) {
		int i = base.indexOf(':');
		if (i < 0) {
			i = base.indexOf('：');
		}
		String league = i >= 0 ? base.substring(i + 1) : base;
		String target = leagueOnly;
		int j = target.indexOf(':');
		if (j < 0) {
			j = target.indexOf('：');
		}
		if (j >= 0) {
			target = target.substring(j + 1);
		}
		return squash(league).equals(squash(target));
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
