package dev.web.api.ranking.bm_w001;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import dev.web.api.bm_a010.TeamColorDTO;
import dev.web.api.favorite.bm_w001.FavoriteLeagueDTO;
import dev.web.repository.bm.RankingRepository;
import dev.web.repository.bm.StaticOverviewSource;
import dev.web.repository.master.TeamColorMasterWebRepository;
import dev.web.repository.user.FavoriteEditMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * データ別のランキングと、その順位の変遷（2026-11-28 追加）
 *
 * <ul>
 *   <li>統計（B006）があるリーグ: 結果は surface_overview_match、スタッツは match_team_snapshot_last から。</li>
 *   <li>統計が無いリーグ: 結果だけ、終了済の試合から作ったデータ（StaticOverviewSource）から。スタッツは出さない。</li>
 *   <li>値は「そのラウンドまでの累計（平均・率）」。順位は値が同じなら同じ順位（1, 1, 3 …）。
 *       勝ち点だけは 得失点差 → 得点 で順位を分ける。</li>
 *   <li>変遷: ラウンド 1 〜 最大のラウンド。チームのそのラウンドの試合が無い（取れていない・未反映）ときは null（グラフをつなげない）。
 *       そのラウンドの順位は、そのラウンドの試合があるチームの中で付ける。</li>
 *   <li>チームカラー: team_color_master（同じ国。同じリーグの行を優先、チーム名は NFKC・空白を除いて比べる）。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

	private final RankingRepository repository;

	private final TeamColorMasterWebRepository colorRepository;

	private final FavoriteEditMasterRepository leagueRepository;

	/** GET /api/rankings/leagues（スクレイピング対象・非表示でない国・リーグ） */
	public List<FavoriteLeagueDTO> leagues() {
		return leagueRepository.findLeagues();
	}

	/** GET /api/rankings/metrics */
	public RankingMetricsResponse metrics(String country, String league) {
		RankingMetricsResponse res = new RankingMetricsResponse();
		res.setCountry(country);
		res.setLeague(league);
		String[] latest = blank(country) || blank(league) ? null : repository.findLatest(country, league);
		res.setSeason(latest == null ? null : latest[0]);
		res.setSource(latest == null ? "STATIC" : "STATS");
		for (RankingMetric m : RankingMetric.values()) {
			RankingMetricDTO d = new RankingMetricDTO();
			d.setKey(m.name());
			d.setLabel(m.getLabel());
			d.setType(m.getType().name());
			d.setUnit(m.getUnit());
			d.setHigherBetter(m.isHigherBetter());
			d.setAvailable(m.getType() == RankingMetric.Type.RESULT || latest != null);
			res.getMetrics().add(d);
		}
		return res;
	}

	/** GET /api/rankings */
	public RankingResponse get(String country, String league, String metricKey) {
		RankingMetric metric = RankingMetric.of(metricKey);
		if (blank(country) || blank(league) || metric == null) {
			throw new IllegalArgumentException("国・リーグ・データを指定してください。");
		}
		RankingResponse res = new RankingResponse();
		res.setCountry(country);
		res.setLeague(league);
		res.setMetric(metric.name());
		res.setLabel(metric.getLabel());
		res.setUnit(metric.getUnit());
		res.setHigherBetter(metric.isHigherBetter());
		res.setScale(metric.getScale());

		String[] latest = repository.findLatest(country, league);
		List<RankingRow> rows = new ArrayList<>();
		try {
			if (metric.getType() == RankingMetric.Type.STAT) {
				if (latest == null) {
					res.getNotes().add("このリーグの統計がまだ無いため、スタッツのランキングは出せません（結果のデータは選べます）");
					return res;
				}
				res.setSeason(latest[0]);
				res.setSource("STATS");
				rows = repository.findStatRows(latest[1], latest[2], latest[0]);
			} else if (latest != null) {
				res.setSeason(latest[0]);
				res.setSource("STATS");
				rows = repository.findResultRows(country, league, latest[0]);
			} else {
				res.setSource("STATIC");
				rows = repository.findResultRows(country, league, StaticOverviewSource.SEASON);
				res.getNotes().add("統計がまだ無いため、取得済みの終了済の試合から計算しています。取れていない試合があると実際と違うことがあります。");
			}
		} catch (Exception e) {
			log.warn("[Ranking] データを取れませんでした {}:{} metric={} e={}", country, league, metric, e.toString());
			res.getNotes().add("データを取れませんでした");
			return res;
		}
		if (rows.isEmpty()) {
			res.getNotes().add("このリーグのデータがまだありません");
			return res;
		}
		build(res, metric, rows, country, league);
		return res;
	}

	private void build(RankingResponse res, RankingMetric metric, List<RankingRow> rows, String country, String league) {
		// チームごと（表示名は最初に出てきた表記）
		Map<String, String> names = new LinkedHashMap<>();
		Map<String, List<RankingRow>> byTeam = new HashMap<>();
		TreeSet<Integer> rounds = new TreeSet<>();
		for (RankingRow r : rows) {
			if (r.getTeam() == null || r.getRoundNo() <= 0) {
				continue;
			}
			String k = norm(r.getTeam());
			names.putIfAbsent(k, r.getTeam().trim());
			byTeam.computeIfAbsent(k, x -> new ArrayList<>()).add(r);
			rounds.add(r.getRoundNo());
		}
		if (rounds.isEmpty()) {
			res.getNotes().add("ラウンド番号のある試合がありません");
			return;
		}
		int maxRound = rounds.last();
		for (int i = 1; i <= maxRound; i++) {
			res.getRounds().add(i);
		}

		// 現在
		Map<String, RankingTeamDTO> dtos = new LinkedHashMap<>();
		Map<String, Double> current = new HashMap<>();
		Map<String, double[]> tiebreak = new HashMap<>();
		for (Map.Entry<String, String> e : names.entrySet()) {
			List<RankingRow> list = byTeam.get(e.getKey());
			RankingTeamDTO d = new RankingTeamDTO();
			d.setTeam(e.getValue());
			d.setGames(list.size());
			d.setLastRound(list.stream().mapToInt(RankingRow::getRoundNo).max().orElse(0));
			Double v = metric.calc(list);
			d.setValue(round(v, metric.getScale()));
			current.put(e.getKey(), v);
			tiebreak.put(e.getKey(), tiebreak(metric, list));
			dtos.put(e.getKey(), d);
		}
		Map<String, Integer> currentRank = rank(current, tiebreak, metric);
		for (Map.Entry<String, RankingTeamDTO> e : dtos.entrySet()) {
			e.getValue().setRank(currentRank.get(e.getKey()));
		}

		// 変遷（ラウンドごと）
		for (int round = 1; round <= maxRound; round++) {
			Map<String, Double> values = new HashMap<>();
			Map<String, double[]> tb = new HashMap<>();
			for (Map.Entry<String, List<RankingRow>> e : byTeam.entrySet()) {
				final int rr = round;
				boolean played = e.getValue().stream().anyMatch(r -> r.getRoundNo() == rr);
				if (!played) {
					continue;
				}
				List<RankingRow> upTo = new ArrayList<>();
				for (RankingRow r : e.getValue()) {
					if (r.getRoundNo() <= round) {
						upTo.add(r);
					}
				}
				Double v = metric.calc(upTo);
				if (v != null) {
					values.put(e.getKey(), v);
					tb.put(e.getKey(), tiebreak(metric, upTo));
				}
			}
			Map<String, Integer> ranks = rank(values, tb, metric);
			for (Map.Entry<String, RankingTeamDTO> e : dtos.entrySet()) {
				Double v = values.get(e.getKey());
				e.getValue().getSeries().add(new RankingPointDTO(round, ranks.get(e.getKey()), round(v, metric.getScale())));
			}
		}

		// チームカラー
		applyColors(dtos, country, league);

		List<RankingTeamDTO> list = new ArrayList<>(dtos.values());
		list.sort(Comparator.comparing((RankingTeamDTO d) -> d.getRank() == null ? Integer.MAX_VALUE : d.getRank())
				.thenComparing(RankingTeamDTO::getTeam));
		res.setTeams(list);
	}

	/** 勝ち点のときだけ 得失点差 → 得点 で順位を分ける */
	private static double[] tiebreak(RankingMetric metric, List<RankingRow> rows) {
		if (metric != RankingMetric.POINTS) {
			return new double[0];
		}
		double gf = 0;
		double ga = 0;
		for (RankingRow r : rows) {
			gf += r.getGoalsFor() == null ? 0 : r.getGoalsFor();
			ga += r.getGoalsAgainst() == null ? 0 : r.getGoalsAgainst();
		}
		return new double[] { gf - ga, gf };
	}

	/** 順位（値が同じなら同じ順位。値が無いチームは入れない） */
	private static Map<String, Integer> rank(Map<String, Double> values, Map<String, double[]> tb, RankingMetric metric) {
		List<String> keys = new ArrayList<>(values.keySet());
		Comparator<String> cmp = (a, b) -> {
			int c = Double.compare(values.get(b), values.get(a));
			if (!metric.isHigherBetter()) {
				c = -c;
			}
			if (c != 0) {
				return c;
			}
			double[] x = tb.get(a);
			double[] y = tb.get(b);
			for (int i = 0; x != null && y != null && i < Math.min(x.length, y.length); i++) {
				int d = Double.compare(y[i], x[i]);
				if (d != 0) {
					return d;
				}
			}
			return 0;
		};
		keys.sort(cmp);
		Map<String, Integer> out = new HashMap<>();
		for (int i = 0; i < keys.size(); i++) {
			if (i > 0 && cmp.compare(keys.get(i - 1), keys.get(i)) == 0) {
				out.put(keys.get(i), out.get(keys.get(i - 1)));
			} else {
				out.put(keys.get(i), i + 1);
			}
		}
		return out;
	}

	private void applyColors(Map<String, RankingTeamDTO> dtos, String country, String league) {
		List<TeamColorDTO> colors;
		try {
			colors = colorRepository.findActiveByCountry(country);
		} catch (Exception e) {
			log.warn("[Ranking] チームカラーを取れませんでした {} e={}", country, e.toString());
			return;
		}
		String lk = norm(league);
		Map<String, TeamColorDTO> sameLeague = new HashMap<>();
		Map<String, TeamColorDTO> any = new HashMap<>();
		for (TeamColorDTO c : colors) {
			if (blank(c.getTeam()) || blank(c.getTeamColorMainHex())) {
				continue;
			}
			String k = norm(c.getTeam());
			any.putIfAbsent(k, c);
			if (lk.equals(norm(c.getLeague()))) {
				sameLeague.putIfAbsent(k, c);
			}
		}
		for (Map.Entry<String, RankingTeamDTO> e : dtos.entrySet()) {
			TeamColorDTO c = sameLeague.getOrDefault(e.getKey(), any.get(e.getKey()));
			if (c != null) {
				e.getValue().setColorMain(hex(c.getTeamColorMainHex()));
				e.getValue().setColorSub(hex(c.getTeamColorSubHex()));
			}
		}
	}

	/** "#RRGGBB" に揃える（読めなければ null） */
	private static String hex(String s) {
		if (blank(s)) {
			return null;
		}
		String v = s.trim();
		if (!v.startsWith("#")) {
			v = "#" + v;
		}
		return v.matches("#[0-9A-Fa-f]{3}([0-9A-Fa-f]{3})?") ? v.toUpperCase() : null;
	}

	private static Double round(Double v, int scale) {
		if (v == null) {
			return null;
		}
		double p = Math.pow(10, scale);
		return Math.round(v * p) / p;
	}

	/** チーム名などの比較キー（NFKC・空白を全部除く） */
	private static String norm(String s) {
		return s == null ? "" : Normalizer.normalize(s, Normalizer.Form.NFKC).replaceAll("\\s+", "");
	}

	private static boolean blank(String s) {
		return s == null || s.trim().isEmpty();
	}
}
