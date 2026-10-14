package dev.web.api.bm_a025;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.web.repository.master.TeamLeagueMasterRepository.TeamLeagueRow;

/**
 * country_league_master から、対戦（home / away）の「国: リーグ」の候補を求め、今の dataCategory と照合する。
 *
 * <p>候補の決め方（バッチの DataCategoryBatchService と同じ考え方）</p>
 * <ol>
 *   <li>チームごとの候補は有効行（del_flg = '0'）を優先し、無ければ削除済みの行も使う。</li>
 *   <li>両チームの候補の共通部分があればそれ。
 *       例: LDU キト = {エクアドル: リーガ・プロ}、ｳﾆﾍﾞﾙｼﾀﾞ･ｶﾄﾘｶ = {エクアドル: リーガ・プロ, チリ: リーガ・デ・プリメーラ}
 *       → 共通部分は「エクアドル: リーガ・プロ」1つ。</li>
 *   <li>片方のチームしかマスタに無ければ、そのチームの候補。</li>
 *   <li>両方あるのに共通部分が無ければ、両方の候補。</li>
 * </ol>
 * チーム名は NFKC 正規化して空白を除いて比較する（「LDU キト」と「LDUキト」は同じ）。
 * @author shiraishitoshio
 *
 */
public class LeagueCandidateResolver {

	public static final String MATCH = "一致";
	public static final String MISMATCH = "不一致";
	public static final String MIXED = "混在";
	public static final String UNKNOWN = "不明";

	/** チームキー → 国・リーグ（有効のみ） */
	private final Map<String, Set<String>> active = new HashMap<>();
	/** チームキー → 国・リーグ（削除済みを含む） */
	private final Map<String, Set<String>> all = new HashMap<>();

	public LeagueCandidateResolver(Collection<TeamLeagueRow> rows) {
		for (TeamLeagueRow r : rows) {
			String key = teamKey(r.getTeam());
			if (key.isEmpty()) {
				continue;
			}
			String base = r.getCountry().trim() + ": " + r.getLeague().trim();
			all.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(base);
			if (r.isActive()) {
				active.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(base);
			}
		}
	}

	/** 判定結果 */
	public static class Result {
		public final String check;
		public final List<String> candidates;

		Result(String check, List<String> candidates) {
			this.check = check;
			this.candidates = candidates;
		}
	}

	/**
	 * 対戦の候補を求め、今の dataCategory（複数あり得る）と照合する。
	 *
	 * @param home ホームチーム名
	 * @param away アウェーチーム名
	 * @param currentCategories その対戦の今の dataCategory 一覧
	 */
	public Result check(String home, String away, Collection<String> currentCategories) {
		List<String> candidates = candidates(home, away);
		if (candidates.isEmpty()) {
			return new Result(UNKNOWN, List.of());
		}

		// 今の dataCategory の「国: リーグ」（国が入っているものだけ）
		Set<String> currentBases = new LinkedHashSet<>();
		if (currentCategories != null) {
			for (String c : currentCategories) {
				String b = countryLeagueOf(c);
				if (b != null) {
					currentBases.add(squash(b));
				}
			}
		}

		if (candidates.size() == 1) {
			String expected = squash(candidates.get(0));
			boolean allMatch = !currentBases.isEmpty() && currentBases.stream().allMatch(expected::equals);
			return new Result(allMatch ? MATCH : MISMATCH, candidates);
		}

		// 複数: 今の値と同じものを先頭に並べる
		List<String> sorted = new ArrayList<>(candidates);
		sorted.sort((x, y) -> {
			int ax = currentBases.contains(squash(x)) ? 0 : 1;
			int ay = currentBases.contains(squash(y)) ? 0 : 1;
			return ax != ay ? Integer.compare(ax, ay) : x.compareTo(y);
		});
		return new Result(MIXED, sorted);
	}

	/** 対戦の「国: リーグ」の候補 */
	public List<String> candidates(String home, String away) {
		Set<String> h = teamCandidates(home);
		Set<String> a = teamCandidates(away);
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

	private Set<String> teamCandidates(String team) {
		String key = teamKey(team);
		Set<String> s = active.get(key);
		if (s != null && !s.isEmpty()) {
			return s;
		}
		s = all.get(key);
		return s != null ? s : Set.of();
	}

	/** 「国: リーグ - ラウンド N」→「国: リーグ」。国が無い・仮の値（XXX）なら null */
	static String countryLeagueOf(String category) {
		if (category == null) {
			return null;
		}
		String c = category.trim();
		int dash = c.indexOf(" - ");
		String base = (dash >= 0 ? c.substring(0, dash) : c).trim();
		int colon = base.indexOf(':');
		if (colon < 0) {
			colon = base.indexOf('：');
		}
		if (colon <= 0 || colon == base.length() - 1) {
			return null;
		}
		String country = base.substring(0, colon).trim();
		if (country.equalsIgnoreCase("XXX") || country.equals("ＸＸＸ")) {
			return null;
		}
		return base;
	}

	static String teamKey(String team) {
		return team == null ? "" : squash(team);
	}

	/** NFKC 正規化して空白を除く（全角「：」も NFKC で「:」になる） */
	static String squash(String s) {
		return Normalizer.normalize(s, Normalizer.Form.NFKC).replaceAll("\\s+", "");
	}
}
