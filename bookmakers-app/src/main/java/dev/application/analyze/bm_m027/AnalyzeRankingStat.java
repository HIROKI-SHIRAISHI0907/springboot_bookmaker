package dev.application.analyze.bm_m027;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.bm_m023.ScoreBasedFeatureStatsEntity;
import dev.application.analyze.bm_m026.EachTeamScoreBasedFeatureEntity;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M027統計分析ロジック
 */
@Component
public class AnalyzeRankingStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = AnalyzeRankingStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = AnalyzeRankingStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M027_ANALYZE_RANKING";

	/** 上位件数 */
	private static final int TOP_N = 68;

	/** Beanクラス */
	@Autowired
	private BmM023M026InitStatRankingBean bean;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/** ScoreBasedFeature 更新Writer */
	@Autowired
	private AnalyzeRankingScoreBasedFeatureWriter analyzeRankingScoreBasedFeatureWriter;

	/** EachTeamScoreBasedFeature 更新Writer */
	@Autowired
	private AnalyzeRankingEachTeamScoreBasedFeatureWriter analyzeRankingEachTeamScoreBasedFeatureWriter;

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";

		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		bean.init();

		int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
		ExecutorService exec = Executors.newFixedThreadPool(threads);

		try {
			// =========================
			// 1) overall
			// =========================
			Map<String, List<KeyRanking>> scoreMap = this.bean.getScoreMap();
			if (scoreMap == null || scoreMap.isEmpty()) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, "", null,
						"scoreMap empty");
			} else {
				Map<String, List<KeyRanking>> byScoreOverall = new HashMap<>();
				for (Map.Entry<String, List<KeyRanking>> e : scoreMap.entrySet()) {
					String sc = extractScoreFromOverallKey(e.getKey());
					if (sc == null) {
						continue;
					}
					byScoreOverall.computeIfAbsent(sc, k -> new ArrayList<>()).addAll(e.getValue());
				}

				List<CompletableFuture<List<ScoreBasedFeatureStatsEntity>>> tasks = new ArrayList<>();
				for (Map.Entry<String, List<KeyRanking>> g : byScoreOverall.entrySet()) {
					final String sc = g.getKey();
					final List<KeyRanking> items = g.getValue();

					tasks.add(CompletableFuture.supplyAsync(() -> {
						try {
							Map<ScoreFieldKey, List<KeyRanking>> ordered = rankByScoreField(items, TOP_N);
							assignRanksWithTies(ordered);
							concatValueWithRank(ordered);
							return mergeToScoreBased(ordered);
						} catch (Exception ex) {
							String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
							this.manageLoggerComponent.debugErrorLog(
									PROJECT_NAME, CLASS_NAME, METHOD_NAME,
									messageCd, ex, "overall score task failed: " + sc);
							return Collections.emptyList();
						}
					}, exec));
				}

				List<ScoreBasedFeatureStatsEntity> overallEntities = tasks.stream()
						.map(CompletableFuture::join)
						.flatMap(Collection::stream)
						.collect(Collectors.toList());

				for (ScoreBasedFeatureStatsEntity entity : overallEntities) {
					this.analyzeRankingScoreBasedFeatureWriter.write(entity);
				}
			}

			// =========================
			// 2) eachTeam
			// =========================
			Map<String, List<KeyRanking>> eachTeamScoreMap = this.bean.getEachScoreMap();
			if (eachTeamScoreMap == null || eachTeamScoreMap.isEmpty()) {
				String messageCd = MessageCdConst.MCD00014I_NO_MAP_DATA;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd, null, "eachTeamScoreMap empty");
			} else {
				Map<String, List<KeyRanking>> byScoreEachTeam = new HashMap<>();
				for (Map.Entry<String, List<KeyRanking>> e : eachTeamScoreMap.entrySet()) {
					String sc = extractScoreFromEachTeamKey(e.getKey());
					if (sc == null) {
						continue;
					}
					byScoreEachTeam.computeIfAbsent(sc, k -> new ArrayList<>()).addAll(e.getValue());
				}

				List<CompletableFuture<List<EachTeamScoreBasedFeatureEntity>>> tasks = new ArrayList<>();
				for (Map.Entry<String, List<KeyRanking>> g : byScoreEachTeam.entrySet()) {
					final String sc = g.getKey();
					final List<KeyRanking> items = g.getValue();

					tasks.add(CompletableFuture.supplyAsync(() -> {
						try {
							Map<ScoreFieldKey, List<KeyRanking>> ordered = rankByScoreField(items, TOP_N);
							assignRanksWithTies(ordered);
							concatValueWithRank(ordered);
							return mergeToEachScoreBased(ordered);
						} catch (Exception ex) {
							String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
							this.manageLoggerComponent.debugErrorLog(
									PROJECT_NAME, CLASS_NAME, METHOD_NAME,
									messageCd, ex, "eachTeam score task failed: " + sc);
							return Collections.emptyList();
						}
					}, exec));
				}

				List<EachTeamScoreBasedFeatureEntity> eachTeamEntities = tasks.stream()
						.map(CompletableFuture::join)
						.flatMap(Collection::stream)
						.collect(Collectors.toList());

				for (EachTeamScoreBasedFeatureEntity entity : eachTeamEntities) {
					this.analyzeRankingEachTeamScoreBasedFeatureWriter.write(entity);
				}
			}

			this.manageLoggerComponent.debugEndInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();

		} finally {
			exec.shutdown();
		}
	}

	/**
	 * (score, field) ごとにグルーピングし、
	 * avg が数値のものだけを降順にソート
	 */
	private Map<ScoreFieldKey, List<KeyRanking>> rankByScoreField(List<KeyRanking> items, int topN) {
		if (items == null || items.isEmpty()) {
			return Collections.emptyMap();
		}

		Map<ScoreFieldKey, List<KeyRanking>> grouped = items.stream()
				.collect(Collectors.groupingBy(
						kr -> new ScoreFieldKey(nullToEmpty(kr.getScore()), nullToEmpty(kr.getField())),
						LinkedHashMap::new,
						Collectors.toCollection(ArrayList::new)));

		Map<ScoreFieldKey, List<KeyRanking>> ordered = new LinkedHashMap<>();

		grouped.forEach((key, group) -> {
			List<KeyRanking> withAvg = new ArrayList<>();
			List<KeyRanking> noAvg = new ArrayList<>();
			for (KeyRanking kr : group) {
				Double v = parseAvgOrNull(kr.getAvg());
				if (v == null) {
					noAvg.add(kr);
				} else {
					withAvg.add(kr);
				}
			}

			withAvg.sort(
					Comparator.comparingDouble((KeyRanking kr) -> parseAvgOrNull(kr.getAvg()))
							.reversed()
							.thenComparing(KeyRanking::getCountry, Comparator.nullsLast(String::compareTo))
							.thenComparing(KeyRanking::getLeague, Comparator.nullsLast(String::compareTo))
							.thenComparing(KeyRanking::getTeam, Comparator.nullsLast(String::compareTo)));

			if (topN > 0 && withAvg.size() > topN) {
				withAvg = new ArrayList<>(withAvg.subList(0, topN));
			}

			List<KeyRanking> orderedList = new ArrayList<>(withAvg);
			orderedList.addAll(noAvg);

			ordered.put(key, orderedList);
		});

		return ordered;
	}

	/**
	 * (score, field) をキー化するためのクラス
	 */
	private static final class ScoreFieldKey {
		final String score;
		final String field;

		ScoreFieldKey(String score, String field) {
			this.score = score;
			this.field = field;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof ScoreFieldKey)) {
				return false;
			}
			ScoreFieldKey that = (ScoreFieldKey) o;
			return Objects.equals(score, that.score) &&
					Objects.equals(field, that.field);
		}

		@Override
		public int hashCode() {
			return Objects.hash(score, field);
		}

		@Override
		public String toString() {
			return "score=" + score + ", field=" + field;
		}
	}

	private static Double parseAvgOrNull(String in) {
		if (in == null) {
			return null;
		}
		String s = in.trim();
		if (s.isEmpty()) {
			return null;
		}
		if (s.endsWith("%")) {
			s = s.substring(0, s.length() - 1);
		}
		s = s.replace(",", "");
		try {
			double v = Double.parseDouble(s);
			if (Double.isNaN(v) || Double.isInfinite(v)) {
				return null;
			}
			return v;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String nullToEmpty(String s) {
		return s == null ? "" : s;
	}

	private static void assignRanksWithTies(Map<ScoreFieldKey, List<KeyRanking>> ordered) {
		ordered.forEach((key, list) -> {
			int i = 0;
			int rankNo = 1;
			Double prev = null;
			for (KeyRanking kr : list) {
				Double v = parseAvgOrNull(kr.getAvg());
				if (v == null) {
					kr.setRank(null);
					continue;
				}
				i++;
				if (prev != null && Double.compare(v, prev) != 0) {
					rankNo = i;
				}
				kr.setRank(rankNo + "位");
				prev = v;
			}
		});
	}

	private static void concatValueWithRank(Map<ScoreFieldKey, List<KeyRanking>> ordered) {
		ordered.forEach((k, list) -> {
			for (KeyRanking kr : list) {
				String r = kr.getRank();
				if (r == null || r.isBlank()) {
					continue;
				}
				kr.setValue(joinComma(kr.getValue(), r));
			}
		});
	}

	private static String joinComma(String a, String b) {
		String s1 = (a == null) ? "" : a.trim();
		String s2 = (b == null) ? "" : b.trim();
		if (s1.isEmpty()) {
			return s2;
		}
		if (s2.isEmpty()) {
			return s1;
		}
		return s1 + "," + s2;
	}

	private List<ScoreBasedFeatureStatsEntity> mergeToScoreBased(
			Map<ScoreFieldKey, List<KeyRanking>> ordered) {

		Map<String, List<KeyRanking>> byId = ordered.values().stream()
				.flatMap(Collection::stream)
				.collect(Collectors.groupingBy(
						KeyRanking::getId,
						LinkedHashMap::new,
						Collectors.toCollection(ArrayList::new)));

		List<ScoreBasedFeatureStatsEntity> out = new ArrayList<>();

		byId.forEach((id, list) -> {
			if (list == null || list.isEmpty()) {
				return;
			}

			KeyRanking base = list.get(0);

			ScoreBasedFeatureStatsEntity e = new ScoreBasedFeatureStatsEntity();
			e.setId(id);
			e.setCountry(base.getCountry());
			e.setLeague(base.getLeague());
			e.setScore(base.getScore());
			e.setSituation("得点あり");

			for (KeyRanking kr : list) {
				if (kr == null) {
					continue;
				}
				String fieldName = kr.getField();
				String val = kr.getValue();
				if (isBlank(fieldName) || isBlank(val)) {
					continue;
				}
				setIfEmptyByReflection(e, normalizeToCamel(fieldName), val);
			}

			out.add(e);
		});

		return out;
	}

	private List<EachTeamScoreBasedFeatureEntity> mergeToEachScoreBased(
			Map<ScoreFieldKey, List<KeyRanking>> ordered) {

		Map<String, List<KeyRanking>> byId = ordered.values().stream()
				.flatMap(Collection::stream)
				.collect(Collectors.groupingBy(
						KeyRanking::getId,
						LinkedHashMap::new,
						Collectors.toCollection(ArrayList::new)));

		List<EachTeamScoreBasedFeatureEntity> out = new ArrayList<>();

		byId.forEach((id, list) -> {
			if (list == null || list.isEmpty()) {
				return;
			}

			KeyRanking base = list.get(0);

			EachTeamScoreBasedFeatureEntity e = new EachTeamScoreBasedFeatureEntity();
			e.setId(id);
			e.setCountry(base.getCountry());
			e.setLeague(base.getLeague());
			e.setScore(base.getScore());
			e.setTeam(base.getTeam());
			e.setSituation("得点あり");

			for (KeyRanking kr : list) {
				if (kr == null) {
					continue;
				}
				String fieldName = kr.getField();
				String val = kr.getValue();
				if (isBlank(fieldName) || isBlank(val)) {
					continue;
				}
				setIfEmptyByReflection(e, normalizeToCamel(fieldName), val);
			}

			out.add(e);
		});

		return out;
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	private static String normalizeToCamel(String name) {
		if (name == null) {
			return null;
		}
		if (!name.contains("_")) {
			return name.trim();
		}
		String[] parts = name.toLowerCase().split("_");
		StringBuilder sb = new StringBuilder(parts[0]);
		for (int i = 1; i < parts.length; i++) {
			if (parts[i].isEmpty()) {
				continue;
			}
			sb.append(Character.toUpperCase(parts[i].charAt(0)))
					.append(parts[i].substring(1));
		}
		return sb.toString().trim();
	}

	private void setIfEmptyByReflection(ScoreBasedFeatureStatsEntity target, String fieldName, String value) {
		final String METHOD_NAME = "setIfEmptyByReflection";
		try {
			Field f = ScoreBasedFeatureStatsEntity.class.getDeclaredField(fieldName);
			f.setAccessible(true);
			Object curr = f.get(target);
			if (curr == null || String.valueOf(curr).trim().isEmpty()) {
				f.set(target, value);
			}
		} catch (NoSuchFieldException nsfe) {
			String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, nsfe,
					"未知のフィールド名(ScoreBasedFeatureStatsEntity): " + fieldName + ", 値:" + value);
		} catch (IllegalAccessException iae) {
			String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, iae,
					"プロパティ設定失敗(ScoreBasedFeatureStatsEntity): " + fieldName + ", 値:" + value);
		}
	}

	private void setIfEmptyByReflection(EachTeamScoreBasedFeatureEntity target, String fieldName, String value) {
		final String METHOD_NAME = "setIfEmptyByReflection";
		try {
			Field f = EachTeamScoreBasedFeatureEntity.class.getDeclaredField(fieldName);
			f.setAccessible(true);
			Object curr = f.get(target);
			if (curr == null || String.valueOf(curr).trim().isEmpty()) {
				f.set(target, value);
			}
		} catch (NoSuchFieldException nsfe) {
			String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, nsfe,
					"未知のフィールド名(EachTeamScoreBasedFeatureEntity): " + fieldName + ", 値:" + value);
		} catch (IllegalAccessException iae) {
			String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, iae,
					"プロパティ設定失敗(EachTeamScoreBasedFeatureEntity): " + fieldName + ", 値:" + value);
		}
	}

	/**
	 * "country_league_score" → score
	 */
	private static String extractScoreFromOverallKey(String key) {
		if (key == null) {
			return null;
		}
		String[] parts = key.split("_", 3);
		return parts.length == 3 ? parts[2].trim() : null;
	}

	/**
	 * "country_league_team_score" → score
	 */
	private static String extractScoreFromEachTeamKey(String key) {
		if (key == null) {
			return null;
		}
		String[] parts = key.split("_", 4);
		return parts.length == 4 ? parts[3].trim() : null;
	}
}
