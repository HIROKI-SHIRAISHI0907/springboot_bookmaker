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

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.bm_m023.AverageStatisticsSituationConst;
import dev.application.analyze.bm_m023.ScoreBasedFeatureStat;
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
	private static final String PROJECT_NAME = ScoreBasedFeatureStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ScoreBasedFeatureStat.class.getSimpleName();

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
		for (int featInd = 0; featInd < AverageStatisticsSituationConst.COUNTER; featInd++) {
			String[] xList = new String[AverageStatisticsSituationConst.SPLIT_COUNTER];
			String[] yList = new String[AverageStatisticsSituationConst.SPLIT_COUNTER];
			// データは BookDataEntity のリスト
			for (int ent_ind = 1; ent_ind < filteredList.size(); ent_ind++) {
				BookDataEntity prev = filteredList.get(ent_ind - 1);
				BookDataEntity curr = filteredList.get(ent_ind);
				// xList ← 特徴量 featInd 番目の値
				String[] allFeatures = new String[AverageStatisticsSituationConst.SPLIT_COUNTER];
				initFormat(curr, allFeatures);
				xList[ent_ind - 1] = allFeatures[featInd];

				// yList ← スコア増加判定（0 or 1）
				String[] allFlags = new String[AverageStatisticsSituationConst.SPLIT_COUNTER];
				initScoreFormat(prev, curr, allFlags);
				yList[ent_ind - 1] = allFlags[featInd];

				// 無効ゴールの削除
				if (BookMakersCommonConst.GOAL_DELETE.equals(prev.getJudge())
						|| BookMakersCommonConst.GOAL_DELETE.equals(curr.getJudge())) {
					yList[ent_ind - 1] = "0";
				}
			}
			boolean isPercent = xList[0] != null && xList[0].contains("%");
			double[] xDoubles = convertToDoubleArray(xList, isPercent);
			double[] yDoubles = convertToDoubleArray(yList, false); // yList は "0"/"1" なのでパーセント処理不要
			double pearson = calculatePearsonCorrelation(xDoubles, yDoubles);

			// 相関係数をエンティティのフィールドに設定
			if (featInd < fields.length) {
				Field field = fields[featInd];
				field.setAccessible(true);
				try {
					field.set(entity, String.format("%.5f", pearson)); // 小数点5桁で保存
				} catch (IllegalAccessException e) {
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, "setCorrelationValuesToEntity",
							"相関係数フィールドの設定失敗: " + field.getName(), e);
				}
			}
			// スレッドセーフな格納
			String mapKey = flg + "_" + chkBody;
			insertMap.put(mapKey, entity);
		}
	}

	/**
	 * 形式を揃える
	 * @param entity BookDataEntity
	 * @param xList
	 */
	private void initFormat(BookDataEntity entity, String[] xList) {
		final String METHOD_NAME = "initFormat";
		final int INDEX = 11;
		int index = 0;
		for (int i = INDEX; i < AverageStatisticsSituationConst.COUNTER + INDEX; i++) {
			try {
				Field[] fields = BookDataEntity.class.getDeclaredFields();
				Field field = fields[i];
				field.setAccessible(true);
				String value = (String) field.get(entity);

				if (i >= 51 && i < 59) {
					List<String> parts = ExecuteMainUtil.splitFlgGroup(value); // ["65%", "13", "20"]
					xList[index++] = parts.get(0); // 成功率（"65%"）
					xList[index++] = parts.get(1); // 成功数（"13"）
					xList[index++] = parts.get(2); // 試行数（"20"）
				} else {
					xList[index++] = value;
				}
			} catch (Exception e) {
				String messageCd = "リフレクションエラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
			}
		}
	}

	/**
	 * 特徴量に応じて得点の増加フラグ（0 or 1）を yList にセット
	 * 特徴量名に "home" または "away" が含まれていることを前提とする
	 */
	private void initScoreFormat(BookDataEntity prev, BookDataEntity curr,
			String[] yList) {
		int prevHome = Integer.parseInt(prev.getHomeScore());
		int currHome = Integer.parseInt(curr.getHomeScore());
		int prevAway = Integer.parseInt(prev.getAwayScore());
		int currAway = Integer.parseInt(curr.getAwayScore());

		final int INDEX = 11; // homeExp 以降が特徴量開始
		int index = 0;
		Field[] fields = BookDataEntity.class.getDeclaredFields();
		for (int i = INDEX; i < AverageStatisticsSituationConst.COUNTER + INDEX; i++) {
			String fieldName = fields[i].getName().toLowerCase();
			if (i >= 51 && i < 59) {
				// 3分割データ → 成功率・成功数・試行数すべてに y 値をつける
				for (int j = 0; j < 3; j++) {
					if (fieldName.contains("home")) {
						yList[index++] = (currHome > prevHome) ? "1" : "0";
					} else if (fieldName.contains("away")) {
						yList[index++] = (currAway > prevAway) ? "1" : "0";
					} else {
						yList[index++] = "0";
					}
				}
			} else {
				if (fieldName.contains("home")) {
					yList[index++] = (currHome > prevHome) ? "1" : "0";
				} else if (fieldName.contains("away")) {
					yList[index++] = (currAway > prevAway) ? "1" : "0";
				} else {
					yList[index++] = "0";
				}
			}
		}
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
		for (int i = 0; i < strArray.length; i++) {
			String val = strArray[i];
			try {
				if (val == null || val.isBlank()) {
					result[i] = 0.0;
				} else if (isPercent && val.contains("%")) {
					result[i] = Double.parseDouble(val.replace("%", "").trim()) / 100.0;
				} else {
					result[i] = Double.parseDouble(val.trim());
				}
			} catch (NumberFormatException e) {
				result[i] = 0.0; // 異常値は0扱いに
			}
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
