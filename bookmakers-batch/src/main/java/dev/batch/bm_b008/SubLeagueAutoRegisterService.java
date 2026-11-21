package dev.batch.bm_b008;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import dev.batch.repository.master.SubLeagueAutoBatchRepository;
import dev.common.entity.DataEntity;
import lombok.extern.slf4j.Slf4j;

/**
 * サブリーグ保有国リーグ登録設定の自動反映（B008 のリアルタイムデータ登録のあと。2026-11-21 追加）
 *
 * <p>リアルタイムデータのデータカテゴリ（例「コロンビア: プリメーラ A - クラウスーラ： クアルドラングラル」）から
 * サブリーグ名（「クラウスーラ」）を取り出し、サブリーグ保有国リーグ登録設定に反映する。</p>
 * <ol>
 *   <li>「国: リーグ - X」の X（「：」「:」「 - 」の手前まで）をサブリーグ名とみなす。
 *       リーグは country_league_season_master（del_flg = '0'）にあるものだけ（長いもの優先で前方一致）。</li>
 *   <li>サブリーグ名とみなすのは KEYWORDS（アペルトゥ・クラウス など）を含むものだけ
 *       （「プレーオフ」「ラウンド 3」などをサブリーグにしないため）。</li>
 *   <li>未設定・サブリーグなし → サブリーグありにして、その名前を登録する。</li>
 *   <li>サブリーグあり → 長音（ー）・中黒・空白・全角半角の違いだけの名前があれば、その名前を置き換える
 *       （「クラウスラ」→「クラウスーラ」。表示順はそのまま）。無ければ末尾に追加する（上限 10）。</li>
 * </ol>
 * 失敗しても B008 本体には影響させない（ログだけ）。更新者は B008。
 */
@Slf4j
@Component
public class SubLeagueAutoRegisterService {

	static final String UPDATE_ID = "B008";

	/** 1 つの 国・リーグ のサブリーグの数の上限（画面と同じ） */
	static final int MAX_SUB_LEAGUES = 10;

	@Value("${b008.sub-league.auto-register:true}")
	private boolean enabled;

	@Value("${b008.sub-league.keywords:アペルトゥ,クラウス,apertura,clausura}")
	private String keywordsCsv;

	@Autowired
	private SubLeagueAutoBatchRepository repository;

	@Autowired
	@Qualifier("masterTxManager")
	private PlatformTransactionManager masterTxManager;

	/**
	 * B008 で読んだ CSV のデータカテゴリからサブリーグを反映する
	 * @return 更新した 国・リーグ の数
	 */
	public int execute(Map<String, List<DataEntity>> entities) {
		if (!enabled || entities == null || entities.isEmpty()) {
			return 0;
		}
		Set<String> categories = new LinkedHashSet<>();
		for (List<DataEntity> list : entities.values()) {
			if (list == null) {
				continue;
			}
			for (DataEntity e : list) {
				if (e != null && e.getDataCategory() != null && !e.getDataCategory().isBlank()) {
					categories.add(e.getDataCategory().trim());
				}
			}
		}
		// 国・リーグ → 見つかったサブリーグ名
		Map<String, String[]> found = new LinkedHashMap<>();
		Map<String, List<String>> leaguesByCountry = new LinkedHashMap<>();
		for (String category : categories) {
			String[] cl = splitCountryRest(category);
			if (cl == null) {
				continue;
			}
			List<String> leagues = leaguesByCountry.computeIfAbsent(cl[0], c -> sortedByLengthDesc(repository.findSeasonLeagues(c)));
			String[] parsed = parse(cl[0], cl[1], leagues);
			if (parsed != null && isSubLeagueName(parsed[2])) {
				found.putIfAbsent(parsed[0] + "||" + parsed[1], parsed);
			}
		}
		int updated = 0;
		for (String[] p : found.values()) {
			try {
				if (apply(p[0], p[1], p[2])) {
					updated++;
				}
			} catch (Exception e) {
				log.warn("[B008] サブリーグの自動反映に失敗しました（続行）。country={}, league={}, subLeague={}, e={}",
						p[0], p[1], p[2], e.toString());
			}
		}
		if (updated > 0) {
			log.info("[B008] サブリーグ保有国リーグ登録設定を {} 件自動反映しました", updated);
		}
		return updated;
	}

	/** 1 国・リーグ 分を反映する（1 トランザクション）。変えたら true */
	boolean apply(String country, String league, String subLeague) {
		TransactionTemplate tx = new TransactionTemplate(masterTxManager);
		Boolean changed = tx.execute(status -> {
			String flg = repository.findFlg(country, league);
			List<String> names = new ArrayList<>(repository.findNames(country, league));
			List<String> next = merge("1".equals(flg) ? names : new ArrayList<>(), subLeague);
			if ("1".equals(flg) && next.equals(names)) {
				return false;
			}
			repository.upsertHas(country, league, UPDATE_ID);
			repository.deleteNames(country, league);
			int order = 0;
			for (String n : next) {
				repository.insertName(country, league, n, ++order, UPDATE_ID);
			}
			log.info("[B008] サブリーグ自動反映 {} / {}: 状態={} 名前={} → あり {}", country, league,
					flg == null ? "未設定" : ("1".equals(flg) ? "あり" : "なし"), names, next);
			return true;
		});
		return Boolean.TRUE.equals(changed);
	}

	/** 似た名前（長音・中黒・空白・全角半角の違い）があれば置き換え、無ければ追加 */
	static List<String> merge(List<String> names, String subLeague) {
		List<String> next = new ArrayList<>(names);
		String key = loose(subLeague);
		for (int i = 0; i < next.size(); i++) {
			if (next.get(i).equals(subLeague)) {
				return next;
			}
			if (loose(next.get(i)).equals(key)) {
				next.set(i, subLeague);
				return next;
			}
		}
		if (next.size() < MAX_SUB_LEAGUES) {
			next.add(subLeague);
		}
		return next;
	}

	/** 「国: 残り」に分ける（最初の「:」または「：」で） */
	static String[] splitCountryRest(String category) {
		int a = category.indexOf(':');
		int b = category.indexOf('：');
		int i = a < 0 ? b : (b < 0 ? a : Math.min(a, b));
		if (i <= 0) {
			return null;
		}
		String country = category.substring(0, i).trim();
		String rest = category.substring(i + 1).trim();
		return country.isEmpty() || rest.isEmpty() ? null : new String[] { country, rest };
	}

	/**
	 * 残り「リーグ - X：…」から [国, リーグ, X] を取る。リーグは対象リーグの前方一致（長いもの優先）
	 */
	static String[] parse(String country, String rest, List<String> leagues) {
		for (String league : leagues) {
			if (league == null || league.isBlank() || !rest.startsWith(league)) {
				continue;
			}
			String tail = rest.substring(league.length());
			if (!tail.matches("^\\s*[-－–]\\s*.+")) {
				continue;
			}
			tail = tail.replaceFirst("^\\s*[-－–]\\s*", "");
			String sub = tail.split("\\s*[:：]\\s*|\\s+[-－–]\\s+", 2)[0].trim();
			if (sub.isEmpty() || sub.startsWith("ラウンド")) {
				return null;
			}
			return new String[] { country, league, sub };
		}
		return null;
	}

	boolean isSubLeagueName(String name) {
		String n = Normalizer.normalize(name, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		return Arrays.stream(keywordsCsv.split(","))
				.map(s -> Normalizer.normalize(s.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT))
				.filter(s -> !s.isEmpty())
				.anyMatch(n::contains);
	}

	/** 比較用（NFKC・小文字・長音／中黒／空白を除く） */
	static String loose(String s) {
		String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		return n.replaceAll("[ー－\\-‐・･\\s]", "");
	}

	private static List<String> sortedByLengthDesc(List<String> leagues) {
		List<String> l = new ArrayList<>(leagues == null ? List.of() : leagues);
		l.sort(Comparator.comparingInt(String::length).reversed());
		return l;
	}
}
