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
 *   <li>見る範囲・書き換える範囲を「同じ試合」（同じ match_id、または同じカードで記録時刻が前後 6 時間以内）に限定。
 *       以前は同じホーム・アウェーの全期間の行を見て・全期間の行と future_master を書き換えていた。</li>
 *   <li>future_master も記録時刻の前後 1 日の予定だけを見る・書き換える。</li>
 *   <li>「XXX: YYY - ラウンド 0」を完成したカテゴリと見なさない（以前は「ラウンド」を含むので完成扱いになり、ずっと XXX のままだった）。</li>
 *   <li>マスタでチームが見つからないときは CSV のカテゴリ（「国: リーグ …」の形なら）を使う。
 *       どちらも無いときだけ「XXX: YYY - ラウンド 0」を返し、その場合は他の行・future_master を書き換えない。</li>
 *   <li>チーム名が空なら例外（呼び出し元で StaticDataRowValidator により事前に除く）。</li>
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

		// 1) 同じ試合に、ラウンドを含む完成したカテゴリがあればそれを使う
		List<DataCategoryDTO> existDto = bookDataRepository.findDataCategoryForMatch(home, away, matchId, baseTime);
		String complete = searchCompleteDataCategoryChk(existDto);
		if (complete != null) {
			update(home, away, matchId, baseTime, complete);
			return complete;
		}

		// 2) 国・リーグ: マスタ（ホーム → アウェー）、無ければ CSV のカテゴリ
		String base = null;
		CountryLeagueMasterEntity entity = countryLeagueMasterBatchRepository.findCountryLeagueByTeam(home);
		if (entity == null) {
			entity = countryLeagueMasterBatchRepository.findCountryLeagueByTeam(away);
		}
		if (entity != null && !isBlank(entity.getCountry()) && !isBlank(entity.getLeague())) {
			base = entity.getCountry() + ": " + entity.getLeague();
		} else if (isUsable(dataCategory)) {
			int dash = dataCategory.indexOf(" - ");
			base = (dash >= 0 ? dataCategory.substring(0, dash) : dataCategory).trim();
		}
		if (base == null) {
			// 国・リーグが分からない。仮の値を返すが、他の行・future_master は書き換えない
			return PLACEHOLDER;
		}

		// 3) ラウンド: future_master（記録時刻の前後1日の予定）→ CSV のカテゴリ
		String round = findRound(futureMasterRepository.findGameTeamCategoryNearTime(home, away, baseTime));
		if (round == null && isUsable(dataCategory)) {
			round = findRound(dataCategory);
		}
		if (round == null) {
			// ラウンドが分からない。国・リーグだけ返し、他の行は書き換えない
			return base;
		}

		String category = base + " - " + ROUND + " " + round;
		update(home, away, matchId, baseTime, category);
		return category;
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
