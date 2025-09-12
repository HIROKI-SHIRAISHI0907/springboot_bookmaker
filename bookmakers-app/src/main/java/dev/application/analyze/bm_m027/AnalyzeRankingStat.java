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
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.bm_m023.ScoreBasedFeatureStatsEntity;
import dev.application.analyze.bm_m026.EachTeamScoreBasedFeatureEntity;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.EachTeamScoreBasedFeatureStatsRepository;
import dev.application.domain.repository.ScoreBasedFeatureStatsRepository;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M027統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
@Transactional
public class AnalyzeRankingStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = AnalyzeRankingStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = AnalyzeRankingStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M027_ANALYZE_RANKING";

	/** 上位件数（必要に応じて調整） */
	private static final int TOP_N = 68;

	/** Beanクラス */
	@Autowired
	private BmM023M026InitStatRankingBean bean;

	/** ScoreBasedFeatureStatsRepositoryレポジトリクラス */
	@Autowired
	private ScoreBasedFeatureStatsRepository scoreBasedFeatureStatsRepository;

	/** EachTeamScoreBasedFeatureStatsRepositoryレポジトリクラス */
	@Autowired
	private EachTeamScoreBasedFeatureStatsRepository eachTeamScoreBasedFeatureStatsRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 並列実行基盤
		int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
		ExecutorService exec = Executors.newFixedThreadPool(threads);
		try {
			// =========================
			// 1) overall（country-league-score）→ score 単位で並列
			// =========================
			Map<String, List<KeyRanking>> scoreMap = this.bean.getScoreMap();
			if (scoreMap == null || scoreMap.isEmpty()) {
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, "", null,
						"scoreMap empty");
			} else {
				// score → List<KeyRanking> に再グルーピング
				Map<String, List<KeyRanking>> byScoreOverall = new HashMap<>();
				for (Map.Entry<String, List<KeyRanking>> e : scoreMap.entrySet()) {
					String sc = extractScoreFromOverallKey(e.getKey()); // country-league-score の3番目
					if (sc == null)
						continue;
					byScoreOverall.computeIfAbsent(sc, k -> new ArrayList<>()).addAll(e.getValue());
				}

				// score ごとに並列実行
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
							this.manageLoggerComponent.debugErrorLog(
									PROJECT_NAME, CLASS_NAME, METHOD_NAME,
									"overall score task failed: " + sc, ex);
							return Collections.emptyList();
						}
					}, exec));
				}

				// 結果収集 & 更新（更新は必要に応じて parallel にしてOK）
				List<ScoreBasedFeatureStatsEntity> overallEntities = tasks.stream()
						.map(CompletableFuture::join)
						.flatMap(Collection::stream)
						.collect(Collectors.toList());

				for (ScoreBasedFeatureStatsEntity entity : overallEntities) {
					update(entity);
				}
			}

			// =========================
			// 2) eachTeam（country-league-score-team）→ score 単位で並列
			// =========================
			Map<String, List<KeyRanking>> eachTeamScoreMap = this.bean.getEachScoreMap();
			if (eachTeamScoreMap == null || eachTeamScoreMap.isEmpty()) {
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, "", null,
						"eachTeamScoreMap empty");
			} else {
				Map<String, List<KeyRanking>> byScoreEachTeam = new java.util.HashMap<>();
				for (Map.Entry<String, List<KeyRanking>> e : eachTeamScoreMap.entrySet()) {
					String sc = extractScoreFromEachTeamKey(e.getKey()); // country-league-score-team の3番目
					if (sc == null)
						continue;
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
							this.manageLoggerComponent.debugErrorLog(
									PROJECT_NAME, CLASS_NAME, METHOD_NAME,
									"eachTeam score task failed: " + sc, ex);
							return Collections.emptyList();
						}
					}, exec));
				}

				List<EachTeamScoreBasedFeatureEntity> eachTeamEntities = tasks.stream()
						.map(CompletableFuture::join)
						.flatMap(Collection::stream)
						.collect(Collectors.toList());

				for (EachTeamScoreBasedFeatureEntity entity : eachTeamEntities) {
					update(entity);
				}
			}

			// endLog
			this.manageLoggerComponent.debugEndInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();
		} finally {
			exec.shutdown();
		}
	}

	/**
	 * (score, field) ごとにグルーピングし、
	 * avg が数値のものだけを降順にソート（同点は country→league→team 昇順）、
	 * avg 非数（null/空/NaN/非数）はソートせず元順で末尾に付ける。
	 * さらに上位 N にトリム（N<=0 の場合はトリムなし）。
	 */
	private Map<ScoreFieldKey, List<KeyRanking>> rankByScoreField(List<KeyRanking> items, int topN) {
		if (items == null || items.isEmpty())
			return Collections.emptyMap();

		// (score, field) でグルーピング
		Map<ScoreFieldKey, List<KeyRanking>> grouped = items.stream()
				.collect(Collectors.groupingBy(
						kr -> new ScoreFieldKey(nullToEmpty(kr.getScore()), nullToEmpty(kr.getField())),
						LinkedHashMap::new,
						Collectors.toCollection(ArrayList::new)));

		Map<ScoreFieldKey, List<KeyRanking>> ordered = new LinkedHashMap<>();

		// 並べ替え（avg があるものだけ）
		grouped.forEach((key, group) -> {
			List<KeyRanking> withAvg = new ArrayList<>();
			List<KeyRanking> noAvg = new ArrayList<>();
			for (KeyRanking kr : group) {
				Double v = parseAvgOrNull(kr.getAvg());
				if (v == null) {
					noAvg.add(kr); // 並べ替え対象外：元順のまま最後に
				} else {
					withAvg.add(kr); // 降順でソートする
				}
			}

			// 降順（同値時は country→league→team 昇順で安定）
			withAvg.sort(
					Comparator.comparingDouble((KeyRanking kr) -> parseAvgOrNull(kr.getAvg()))
							.reversed()
							.thenComparing(KeyRanking::getCountry, Comparator.nullsLast(String::compareTo))
							.thenComparing(KeyRanking::getLeague, Comparator.nullsLast(String::compareTo))
							.thenComparing(KeyRanking::getTeam, Comparator.nullsLast(String::compareTo)));

			// 上位 N 件にトリム（topN <= 0 ならトリムしない）
			if (topN > 0 && withAvg.size() > topN) {
				withAvg = new ArrayList<>(withAvg.subList(0, topN));
			}

			// 連結：ソート済み + 非ソート（avgなし）
			List<KeyRanking> orderedList = new ArrayList<>(withAvg);
			orderedList.addAll(noAvg);

			ordered.put(key, orderedList);
		});

		return ordered;
	}

	/**
	 * 更新メソッド
	 * @param entity エンティティ
	 */
	private synchronized void update(ScoreBasedFeatureStatsEntity entity) {
		final String METHOD_NAME = "update";
		String fillChar = setLoggerFillChar(
				entity.getScore(),
				entity.getCountry(),
				entity.getLeague(),
				"");
		int result = this.scoreBasedFeatureStatsRepository.updateStatValues(entity);
		if (result != 1) {
			String messageCd = "更新エラー average_statistics_data";
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
			this.manageLoggerComponent.createSystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					null);
		}
		String messageCd = "更新件数 average_statistics_data";
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar, "BM_M027 更新件数: 1件");
	}

	/**
	 * 更新メソッド
	 * @param entity エンティティ
	 */
	private synchronized void update(EachTeamScoreBasedFeatureEntity entity) {
		final String METHOD_NAME = "update";
		String fillChar = setLoggerFillChar(
				entity.getScore(),
				entity.getCountry(),
				entity.getLeague(),
				"");
		int result = this.eachTeamScoreBasedFeatureStatsRepository.updateStatValues(entity);
		if (result != 1) {
			String messageCd = "更新エラー average_statistics_data_detail";
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
			this.manageLoggerComponent.createSystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					null);
		}
		String messageCd = "更新件数 average_statistics_data_detail";
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar, "BM_M027 更新件数: 1件");
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
			if (this == o)
				return true;
			if (!(o instanceof ScoreFieldKey))
				return false;
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

	/** avg を数値にパース。null/空/非数/NaN/∞ は null を返す（＝並べ替え対象外） */
	private static Double parseAvgOrNull(String in) {
		if (in == null)
			return null;
		String s = in.trim();
		if (s.isEmpty())
			return null;
		if (s.endsWith("%"))
			s = s.substring(0, s.length() - 1);
		s = s.replace(",", "");
		try {
			double v = Double.parseDouble(s);
			if (Double.isNaN(v) || Double.isInfinite(v))
				return null;
			return v;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * 空チェック
	 * @param s
	 * @return
	 */
	private static String nullToEmpty(String s) {
		return s == null ? "" : s;
	}

	/**
	 * 各 (score, field) グループ内で、avg が数値のものだけに "X位" を付与
	 * 同値 avg に同じ順位を付与（標準競技順位: 1,1,3…）。avg 無効は順位なし
	 * @param ordered
	 */
	private static void assignRanksWithTies(Map<ScoreFieldKey, List<KeyRanking>> ordered) {
		ordered.forEach((key, list) -> {
			int i = 0; // ソート済みの先頭からのインデックス（avgありのみを数える）
			int rankNo = 1; // 表示する順位
			Double prev = null; // 直前の avg（数値）
			for (KeyRanking kr : list) {
				Double v = parseAvgOrNull(kr.getAvg());
				if (v == null) { // 無効 → 順位なし
					kr.setRank(null);
					continue;
				}
				i++; // avg が数値の要素だけカウント
				if (prev != null && Double.compare(v, prev) != 0) {
					// 値が変わったら次の順位は現在のカウント i
					rankNo = i;
				}
				kr.setRank(rankNo + "位");
				prev = v;
			}
		});
	}

	/**
	 * 各 (score, field) グループの要素に対して、value と rank を「value,rank」で連結して value に格納
	 */
	private static void concatValueWithRank(Map<ScoreFieldKey, List<KeyRanking>> ordered) {
		ordered.forEach((k, list) -> {
			for (KeyRanking kr : list) {
				String r = kr.getRank();
				if (r == null || r.isBlank()) {
					continue; // 順位が無いものは変更しない
				}
				kr.setValue(joinComma(kr.getValue(), r));
			}
		});
	}

	/**
	 * null/空文字を無視してカンマ連結（どちらか片方が空なら空でない方だけ返す）
	 */
	private static String joinComma(String a, String b) {
		String s1 = (a == null) ? "" : a.trim();
		String s2 = (b == null) ? "" : b.trim();
		if (s1.isEmpty())
			return s2;
		if (s2.isEmpty())
			return s1;
		return s1 + "," + s2;
	}

	/**
	 *  (score, field) → List<KeyRanking> をフラット化し、id でまとめて ScoreBasedFeatureStatsEntity を作成
	 * @param ordered
	 * @return
	 */
	private List<ScoreBasedFeatureStatsEntity> mergeToScoreBased(
			Map<ScoreFieldKey, List<KeyRanking>> ordered) {

		// すべてをフラット化 → id ごとにグルーピング
		Map<String, List<KeyRanking>> byId = ordered.values().stream()
				.flatMap(Collection::stream)
				.collect(Collectors.groupingBy(
						KeyRanking::getId,
						LinkedHashMap::new,
						Collectors.toCollection(ArrayList::new)));

		List<ScoreBasedFeatureStatsEntity> out = new ArrayList<>();

		byId.forEach((id, list) -> {
			if (list == null || list.isEmpty())
				return;

			// ベース情報（country/league/score）は最初の要素から採る（不一致があれば先勝ち）
			KeyRanking base = list.get(0);

			ScoreBasedFeatureStatsEntity e = new ScoreBasedFeatureStatsEntity();
			e.setId(id);
			e.setCountry(base.getCountry());
			e.setLeague(base.getLeague());
			e.setScore(base.getScore());
			// situation は KeyRanking に無いので、必要ならここで埋める
			e.setSituation("得点あり"); // 例

			// 同一 id 内の各 field を ScoreBasedFeatureStatsEntity の同名プロパティへ反映
			for (KeyRanking kr : list) {
				if (kr == null)
					continue;
				String fieldName = kr.getField();
				String val = kr.getValue(); // すでに "value,rank" 形式
				if (isBlank(fieldName) || isBlank(val))
					continue;

				// まだ値が入っていない場合のみセット（重複 field が来たら先勝ち）
				setIfEmptyByReflection(e, normalizeToCamel(fieldName), val);
			}

			out.add(e);
		});

		return out;
	}

	/**
	 *  (score, field) → List<KeyRanking> をフラット化し、id でまとめて EachTeamScoreBasedFeatureEntity を作成
	 * @param ordered
	 * @return
	 */
	private List<EachTeamScoreBasedFeatureEntity> mergeToEachScoreBased(
			Map<ScoreFieldKey, List<KeyRanking>> ordered) {

		// すべてをフラット化 → id ごとにグルーピング
		Map<String, List<KeyRanking>> byId = ordered.values().stream()
				.flatMap(java.util.Collection::stream)
				.collect(Collectors.groupingBy(
						KeyRanking::getId,
						LinkedHashMap::new,
						Collectors.toCollection(java.util.ArrayList::new)));

		List<EachTeamScoreBasedFeatureEntity> out = new ArrayList<>();

		byId.forEach((id, list) -> {
			if (list == null || list.isEmpty())
				return;

			// ベース情報（country/league/score/team）は最初の要素から採る（不一致があれば先勝ち）
			KeyRanking base = list.get(0);

			EachTeamScoreBasedFeatureEntity e = new EachTeamScoreBasedFeatureEntity();
			e.setId(id);
			e.setCountry(base.getCountry());
			e.setLeague(base.getLeague());
			e.setScore(base.getScore());
			e.setTeam(base.getTeam());
			// situation は KeyRanking に無いので、必要ならここで埋める
			e.setSituation("得点あり"); // 例

			// 同一 id 内の各 field を ScoreBasedFeatureStatsEntity の同名プロパティへ反映
			for (KeyRanking kr : list) {
				if (kr == null)
					continue;
				String fieldName = kr.getField();
				String val = kr.getValue(); // すでに "value,rank" 形式
				if (isBlank(fieldName) || isBlank(val))
					continue;

				// まだ値が入っていない場合のみセット（重複 field が来たら先勝ち）
				setIfEmptyByReflection(e, normalizeToCamel(fieldName), val);
			}

			out.add(e);
		});

		return out;
	}

	/**
	 *  文字列が null/空白のみ なら true
	 * @param s
	 * @return
	 */
	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	/**
	 *  フィールド名を camel に正規化（snake_case → camelCase）。既に camel の場合はそのまま
	 * @param name
	 * @return
	 */
	private static String normalizeToCamel(String name) {
		if (name == null)
			return null;
		if (!name.contains("_"))
			return name.trim();
		String[] parts = name.toLowerCase().split("_");
		StringBuilder sb = new StringBuilder(parts[0]);
		for (int i = 1; i < parts.length; i++) {
			if (parts[i].isEmpty())
				continue;
			sb.append(Character.toUpperCase(parts[i].charAt(0)))
					.append(parts[i].substring(1));
		}
		return sb.toString().trim();
	}

	/**
	 *  反射でプロパティへ文字列をセット。既に値があれば上書きしない（先勝ち）。一致するフィールドが無ければログだけ。
	 * @param target
	 * @param fieldName
	 * @param value
	 */
	private void setIfEmptyByReflection(ScoreBasedFeatureStatsEntity target, String fieldName, String value) {
		try {
			Field f = ScoreBasedFeatureStatsEntity.class.getDeclaredField(fieldName);
			f.setAccessible(true);
			Object curr = f.get(target);
			if (curr == null || String.valueOf(curr).trim().isEmpty()) {
				f.set(target, value);
			}
		} catch (NoSuchFieldException nsfe) {
			// 未対応フィールド名は情報ログに流す（必要なければ無視）
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, "mergeToScoreBased",
					"未知のフィールド名: " + fieldName, value, null);
		} catch (IllegalAccessException iae) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, "mergeToScoreBased",
					"プロパティ設定失敗: " + fieldName, iae, value);
		}
	}

	/**
	 *  反射でプロパティへ文字列をセット。既に値があれば上書きしない（先勝ち）。一致するフィールドが無ければログだけ。
	 * @param target
	 * @param fieldName
	 * @param value
	 */
	private void setIfEmptyByReflection(EachTeamScoreBasedFeatureEntity target, String fieldName, String value) {
		try {
			Field f = EachTeamScoreBasedFeatureEntity.class.getDeclaredField(fieldName);
			f.setAccessible(true);
			Object curr = f.get(target);
			if (curr == null || String.valueOf(curr).trim().isEmpty()) {
				f.set(target, value);
			}
		} catch (NoSuchFieldException nsfe) {
			// 未対応フィールド名は情報ログに流す（必要なければ無視）
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, "mergeToScoreBased",
					"未知のフィールド名: " + fieldName, value, null);
		} catch (IllegalAccessException iae) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, "mergeToScoreBased",
					"プロパティ設定失敗: " + fieldName, iae, value);
		}
	}

	/**
	 * "country-league-score" → score
	 * @param key
	 * @return
	 */
	private static String extractScoreFromOverallKey(String key) {
		if (key == null)
			return null;
		String[] parts = key.split("-", 3); // 3パーツに限定
		return parts.length == 3 ? parts[2].trim() : null;
	}

	/**
	 * "country-league-score-team" → score（team にハイフンが含まれてもOK）
	 * @param key
	 * @return
	 */
	private static String extractScoreFromEachTeamKey(String key) {
		if (key == null)
			return null;
		String[] parts = key.split("-", 4); // 先頭3つを country/league/score として確定
		return parts.length >= 3 ? parts[2].trim() : null;
	}

	/**
	 * 埋め字設定
	 * @param score スコア
	 * @param country 国
	 * @param league リーグ
	 * @param team チーム
	 * @return
	 */
	private String setLoggerFillChar(String score,
			String country, String league, String team) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("スコア: " + score + ", ");
		stringBuilder.append("国: " + country + ", ");
		stringBuilder.append("リーグ: " + league + ", ");
		stringBuilder.append("チーム: " + team);
		return stringBuilder.toString();
	}
}
