package dev.web.api.matchDetail.availability;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import dev.web.repository.bm.MatchDetailAvailabilityRepository;
import dev.web.repository.bm.MatchDetailDataWebRepository;
import lombok.RequiredArgsConstructor;

/**
 * 統計詳細データ（9 テーブル）のうち、その試合で「取れるはず」のものを決める（2026-11-22 追加）
 * <ul>
 *   <li>提供なし: 同じリーグの直近 {@link #RECENT} 試合で、データがある試合が {@link #UNAVAILABLE_RATE} 未満のテーブル
 *       （例: J2・J3 のモメンタム、インドネシアの選手スタッツ）。Flashscore がそのリーグでは出していないので、取り直しても増えない。
 *       リーグの試合が {@link #MIN_SAMPLE} 試合未満のときは判断しない（全部「取れるはず」）。</li>
 *   <li>無くても正常: ゴール（0-0）・欠場者（いない）は、無くても取り直さない（分母にも入れない）。</li>
 *   <li>試合情報（match）は必ず取れるはず。</li>
 *   <li>取れるはず = 9 −（提供なし）−（無くても正常で、無いもの）。データがあれば提供なしでも数える。</li>
 * </ul>
 * 使うところ: 突合確認（IngestedDataService の n/m）・統計詳細データ参照（MatchDetailReferenceService の「一部なし」）。
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class DetailAvailabilityService {

	/** リーグごとに見る直近の試合数 */
	public static final int RECENT = 50;

	/** これより少ない試合数のリーグは判断しない */
	public static final int MIN_SAMPLE = 5;

	/** データがある試合の割合がこれ未満なら「提供なし」 */
	public static final double UNAVAILABLE_RATE = 0.10;

	/** 無くても正常なテーブル（ゴール 0-0・欠場者なし） */
	public static final Set<String> OPTIONAL_TABLES = Set.of("goal", "missing");

	/** 日本語名（画面に出す） */
	public static final Map<String, String> LABELS;
	static {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("match", "試合情報");
		m.put("summary", "サマリー");
		m.put("goal", "ゴール");
		m.put("momentum", "モメンタム");
		m.put("momentumIncident", "モメンタムの出来事");
		m.put("formation", "フォーメーション");
		m.put("lineup", "ラインアップ");
		m.put("missing", "欠場者");
		m.put("memberStat", "選手スタッツ");
		LABELS = java.util.Collections.unmodifiableMap(m);
	}

	private final MatchDetailAvailabilityRepository repository;

	/** 1 試合の判定結果 */
	public static final class Judge {
		/** 取れるはずのテーブル（分母） */
		public final Set<String> expected;
		/** データがあるテーブル */
		public final Set<String> has;
		/** 取れるはずなのに無い（取り直す余地あり） */
		public final Set<String> missing;
		/** このリーグでは提供なし（分母から外した） */
		public final Set<String> unavailable;

		Judge(Set<String> expected, Set<String> has, Set<String> missing, Set<String> unavailable) {
			this.expected = expected;
			this.has = has;
			this.missing = missing;
			this.unavailable = unavailable;
		}

		public List<String> missingLabels() {
			return missing.stream().map(k -> LABELS.getOrDefault(k, k)).collect(Collectors.toList());
		}

		public List<String> unavailableLabels() {
			return unavailable.stream().map(k -> LABELS.getOrDefault(k, k)).collect(Collectors.toList());
		}
	}

	/** リーグ → 提供なしのテーブル。まとめて引く */
	public Map<String, Set<String>> unavailableByLeague(Collection<String> leagues) {
		Map<String, Set<String>> out = new LinkedHashMap<>();
		Set<String> ls = leagues.stream().filter(l -> l != null && !l.isBlank()).collect(Collectors.toSet());
		if (ls.isEmpty()) {
			return out;
		}
		Map<String, Map<String, Integer>> counts = repository.findLeagueTableCounts(ls,
				MatchDetailDataWebRepository.DETAIL_TABLES, RECENT);
		for (String l : ls) {
			Map<String, Integer> c = counts.getOrDefault(l, Map.of());
			int total = c.getOrDefault(MatchDetailAvailabilityRepository.TOTAL, 0);
			Set<String> un = new LinkedHashSet<>();
			if (total >= MIN_SAMPLE) {
				for (String k : MatchDetailDataWebRepository.DETAIL_TABLES.keySet()) {
					if ("match".equals(k) || OPTIONAL_TABLES.contains(k)) {
						continue;
					}
					if (c.getOrDefault(k, 0) < total * UNAVAILABLE_RATE) {
						un.add(k);
					}
				}
			}
			out.put(l, un);
		}
		return out;
	}

	/** 1 試合を判定する（unavailable は {@link #unavailableByLeague} のそのリーグの値。無ければ空） */
	public static Judge judge(Set<String> has, Set<String> unavailable) {
		Set<String> h = has == null ? Set.of() : has;
		Set<String> un = new LinkedHashSet<>();
		Set<String> expected = new LinkedHashSet<>();
		Set<String> missing = new LinkedHashSet<>();
		for (String k : MatchDetailDataWebRepository.DETAIL_TABLES.keySet()) {
			if (h.contains(k)) {
				expected.add(k);
				continue;
			}
			if (unavailable != null && unavailable.contains(k)) {
				un.add(k);
				continue;
			}
			if (OPTIONAL_TABLES.contains(k)) {
				continue;
			}
			expected.add(k);
			missing.add(k);
		}
		return new Judge(expected, h, missing, un);
	}
}
