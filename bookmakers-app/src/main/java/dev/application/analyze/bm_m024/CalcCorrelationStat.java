package dev.application.analyze.bm_m024;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Triple;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.bm_m023.AverageStatisticsSituationConst;
import dev.application.analyze.bm_m023.StatFormatResolver;
import dev.application.analyze.common.util.BookMakersCommonConst;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.CalcCorrelationRepository;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M024統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
@Transactional
public class CalcCorrelationStat extends StatFormatResolver implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CalcCorrelationStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CalcCorrelationStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M024_CALC_CORRELATION";

	/** CalcCorrelationRepositoryレポジトリクラス */
	@Autowired
	private CalcCorrelationRepository calcCorrelationRepository;

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

		// 全リーグ・国を走査
		ConcurrentHashMap<String, CalcCorrelationEntity> resultMap = new ConcurrentHashMap<>();
		for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
			String[] data_category = entry.getKey().split("-");
			String country = data_category[0];
			String league = data_category[1];

			Map<String, List<BookDataEntity>> entrySub = entry.getValue();
			for (List<BookDataEntity> entityList : entrySub.values()) {
				// null や空リストはスキップ
				if (entityList == null || entityList.isEmpty())
					continue;
				String home = entityList.get(0).getHomeTeamName();
				String away = entityList.get(0).getAwayTeamName();
				// decideBasedMain を呼び出して集計マップを取得
				ConcurrentHashMap<String, CalcCorrelationEntity> partialMap = decideBasedMain(entityList, country,
						league, home, away);
				resultMap.putAll(partialMap);
			}
		}

		// 登録
		ExecutorService executor = Executors.newFixedThreadPool(resultMap.size());
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (Map.Entry<String, CalcCorrelationEntity> entry : resultMap.entrySet()) {
			CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
				CalcCorrelationEntity entity = entry.getValue();
				insert(entity);
			}, executor);
			futures.add(future);
		}
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		executor.shutdown();

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * 処理メインロジック
	 * @param entities エンティティ
	 * @param country 国
	 * @param league リーグ
	 * @param home ホーム
	 * @param away アウェー
	 * @return
	 */
	private ConcurrentHashMap<String, CalcCorrelationEntity> decideBasedMain(List<BookDataEntity> entities,
			String country, String league, String home, String away) {
		// 各種flg
		List<String> flgs = List.of(
				AverageStatisticsSituationConst.ALL_DATA,
				AverageStatisticsSituationConst.FIRST_DATA,
				AverageStatisticsSituationConst.SECOND_DATA);

		ExecutorService executor = Executors.newFixedThreadPool(20); // スレッド数は状況に応じて調整
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		ConcurrentHashMap<String, CalcCorrelationEntity> allMap = new ConcurrentHashMap<>();
		for (String flg : flgs) {
			// ALL_DATA / FIRST_DATA / SECOND_DATA → スコア単位でなく全体処理なので null を渡す
			CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
				basedEntities(allMap, entities, flg, country, league, home, away);
			}, executor);
			futures.add(future);
		}
		// すべての非同期処理が終わるのを待つ
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		executor.shutdown();
		return allMap;
	}

	/**
	 * 基準エンティティ指定
	 * @param insertMap map
	 * @param entities 全体エンティティ
	 * @param flg 設定フラグ
	 * @param country 国
	 * @param league リーグ
	 * @param home ホーム
	 * @param away アウェー
	 * @return
	 */
	private void basedEntities(ConcurrentHashMap<String, CalcCorrelationEntity> insertMap,
			List<BookDataEntity> entities, String flg,
			String country, String league, String home, String away) {
		// 既存のリスト
		List<BookDataEntity> filteredList = null;
		if (AverageStatisticsSituationConst.ALL_DATA.equals(flg)) {
			filteredList = entities;
		} else {
			// ハーフタイムの通番を特定
			String halfTimeSeq = ExecuteMainUtil.getHalfEntities(entities).getSeq();
			// ハーフタイム前の試合時間のデータをフィルタリング（通番が半分より小さいもの）
			if (AverageStatisticsSituationConst.FIRST_DATA.equals(flg)) {
				filteredList = entities.stream()
						.filter(entity -> entity.getSeq().compareTo(halfTimeSeq) <= 0) // 通番がハーフタイム(含む)より前
						.collect(Collectors.toList());
			} else if (AverageStatisticsSituationConst.SECOND_DATA.equals(flg)) {
				filteredList = entities.stream()
						.filter(entity -> entity.getSeq().compareTo(halfTimeSeq) > 0) // 通番がハーフタイムより後
						.collect(Collectors.toList());
			}
		}

		// 空マップならskip
		if (filteredList == null || filteredList.isEmpty()) {
			return;
		}

		// getDataを呼び出すのはPearsonのみ
		String chkBody = (AverageStatisticsSituationConst.ALL_DATA.equals(flg) ||
				AverageStatisticsSituationConst.FIRST_DATA.equals(flg) ||
				AverageStatisticsSituationConst.SECOND_DATA.equals(flg))
						? CalcCorrelationConst.PEARSON
						: "";

		// 相関係数データ初期化リスト(特徴量分だけloop)
		Field[] fields = CalcCorrelationEntity.class.getDeclaredFields();
		CalcCorrelationEntity entity = new CalcCorrelationEntity();
		entity.setCountry(country);
		entity.setLeague(league);
		entity.setHome(home);
		entity.setAway(away);
		entity.setScore(flg);
		entity.setChkBody(chkBody);

		// データ(CalcCorrelation)
		for (int featInd = 9; featInd < AverageStatisticsSituationConst.COUNTER; featInd++) {
			// ★ サンプル数ぶん確保
			String[] xList = new String[filteredList.size() - 1];
			String[] yList = new String[filteredList.size() - 1];
			Field[] bookFields = BookDataEntity.class.getDeclaredFields();
			Field f = bookFields[featInd + 1];
			f.setAccessible(true);
			String name = f.getName();

			// tri-split のどれを使うか（全部やるならこのブロックを3回まわす）
			int subIdx = decideSubIndexFor(featInd, name); // 0/1/2 or -1(非3分割)

			try {
				for (int ent_ind = 1; ent_ind < filteredList.size(); ent_ind++) {
					int pos = ent_ind - 1;
					BookDataEntity prev = filteredList.get(ent_ind - 1);
					BookDataEntity curr = filteredList.get(ent_ind);
					String raw = (String) f.get(curr);
					// x: 特徴量
					xList[pos] = extractOneValue(name, raw, subIdx); // tri-splitなら1つ選ぶ
					// y: 得点増加フラグ
					yList[pos] = makeFlag(prev, curr, name);
					// 無効ゴールは0に
					if (BookMakersCommonConst.GOAL_DELETE.equals(prev.getJudge())
							|| BookMakersCommonConst.GOAL_DELETE.equals(curr.getJudge())) {
						yList[pos] = "0";
					}
				}
			} catch (Exception e) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, "setCorrelationValuesToEntity",
						"設定失敗: " + name, e);
			}

			boolean isPercent = firstNonNullContainsPercent(xList);
			double[] xDoubles = convertToDoubleArray(xList, isPercent);
			double[] yDoubles = convertToDoubleArray(yList, false);
			double pearson = calculatePearsonCorrelation(xDoubles, yDoubles);

			// featInd→CalcCorrelationEntity の対応が「1対1」のときだけこれでOK
			if (featInd < fields.length) {
				Field out = fields[featInd];
				out.setAccessible(true);
				try {
					out.set(entity, String.format("%.5f", pearson));
				} catch (IllegalAccessException e) {
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, "setCorrelationValuesToEntity",
							"相関係数フィールドの設定失敗: " + out.getName(), e);
				}
			}
		}
		// スレッドセーフな格納
		String mapKey = flg + "_" + chkBody;
		insertMap.put(mapKey, entity);
	}

	/**
	 * featInd→どのサブ要素を使うか（例: 0=成功率, 1=成功数, 2=試行数）
	 * @param featInd
	 * @param fieldName
	 * @return
	 */
	// ルールは「CalcCorrelationEntity 側のフィールド順」に合わせて決めてください。
	// 簡易例: すべて成功率に寄せる場合は 0 固定でもOK。
	private int decideSubIndexFor(int featInd, String fieldName) {
		if (!isTriSplitFieldName(fieldName))
			return -1; // 非3分割
		// 例1: 全部「成功率」を使う
		return 0;

		// 例2: featInd の並びに応じて ratio/count/try を割り振るならここで計算
		// return (featInd - baseIndexForThisTriField) % 3;
	}

	/**
	 * 値の抽出（3分割なら subIdx を使って1つだけ返す）
	 * @param fieldName
	 * @param raw
	 * @param subIdx
	 * @return
	 */
	private String extractOneValue(String fieldName, String raw, int subIdx) {
		if (isTriSplitFieldName(fieldName)) {
			// "65% (13/20)" or "93%" → 3要素に（常に3返す安全版）
			Triple<String, String, String> t = split3Safe(raw); // StatFormatResolver に追加推奨
			if (subIdx == 0)
				return t.getLeft(); // 成功率
			if (subIdx == 1)
				return t.getMiddle(); // 成功数
			if (subIdx == 2)
				return t.getRight(); // 試行数
			// subIdx未指定なら比率にフォールバック
			return t.getLeft();
		}
		return raw;
	}

	/**
	 * フラグに対して値抽出
	 * @param prev
	 * @param curr
	 * @param fieldName
	 * @return
	 */
	private String makeFlag(BookDataEntity prev, BookDataEntity curr, String fieldName) {
		int prevHome = Integer.parseInt(prev.getHomeScore());
		int currHome = Integer.parseInt(curr.getHomeScore());
		int prevAway = Integer.parseInt(prev.getAwayScore());
		int currAway = Integer.parseInt(curr.getAwayScore());
		String n = fieldName.toLowerCase();
		if (n.contains("home"))
			return (currHome > prevHome) ? "1" : "0";
		if (n.contains("away"))
			return (currAway > prevAway) ? "1" : "0";
		return "0";
	}

	/**
	 * nullpercent
	 * @param arr
	 * @return
	 */
	private boolean firstNonNullContainsPercent(String[] arr) {
		for (String s : arr)
			if (s != null)
				return s.contains("%");
		return false;
	}

	/**
	 * 登録メソッド
	 * @param entity エンティティ
	 */
	private synchronized void insert(CalcCorrelationEntity entity) {
		final String METHOD_NAME = "insert";
		String fillChar = setLoggerFillChar(
				entity.getChkBody(),
				entity.getScore(),
				entity.getCountry(),
				entity.getLeague(),
				entity.getHome(),
				entity.getAway());
		int result = this.calcCorrelationRepository.insert(entity);
		if (result != 1) {
			String messageCd = "新規登録エラー";
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
			this.manageLoggerComponent.createSystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					null);
		}
		String messageCd = "登録件数";
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar, "BM_M024 登録件数: 1件");
	}

	/**
	 * ピアソン相関係数を導出
	 * @param x 特徴量
	 * @param y 得点
	 * @return
	 */
	public double calculatePearsonCorrelation(double[] x, double[] y) {
		final String METHOD_NAME = "calculatePearsonCorrelation";
		if (x == null || y == null || x.length != y.length || x.length < 2) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					"ピアソン相関係数が導出できない", null);
		}
		PearsonsCorrelation pc = new PearsonsCorrelation();
		return pc.correlation(x, y);
	}

	/**
	 * doubleに変換する
	 * @param strArray
	 * @param isPercent
	 * @return
	 */
	private double[] convertToDoubleArray(String[] strArray, boolean isPercent) {
		double[] result = new double[strArray.length];
		int ind = 0;
		for (String val : strArray) {
			try {
				if (val == null || val.isBlank()) {
					result[ind] = 0.0;
				} else if (isPercent && val.contains("%")) {
					result[ind] = Double.parseDouble(val.replace("%", "").trim()) / 100.0;
				} else {
					result[ind] = Double.parseDouble(val.trim());
				}
			} catch (NumberFormatException e) {
				result[ind] = 0.0; // 異常値は0扱いに
			}
			ind++;
		}
		return result;
	}

	/**
	 * 埋め字設定
	 * @param chk_body 状況
	 * @param score スコア
	 * @param country 国
	 * @param league リーグ
	 * @param home ホーム
	 * @param away アウェー
	 * @return
	 */
	private String setLoggerFillChar(String chk_body, String score,
			String country, String league, String home, String away) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("調査内容: " + chk_body + ", ");
		stringBuilder.append("スコア: " + score + ", ");
		stringBuilder.append("国: " + country + ", ");
		stringBuilder.append("リーグ: " + league + ", ");
		stringBuilder.append("ホーム: " + home + ", ");
		stringBuilder.append("アウェー: " + away);
		return stringBuilder.toString();
	}

}
