package dev.application.analyze.bm_m023;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.ScoreBasedFeatureStatsRepository;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M023統計分析ロジック
 * @author shiraishitoshio
 *
 */
public class ScoreBasedFeatureStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ScoreBasedFeatureStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ScoreBasedFeatureStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M023_SCORE_BASED_FEATURE";

	/** Beanクラス */
	@Autowired
	private BmM023ScoreBasedFeatureBean bean;

	/** ScoreBasedFeatureStatsRepositoryレポジトリクラス */
	@Autowired
	private ScoreBasedFeatureStatsRepository scoreBasedFeatureStatsRepository;

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
		ConcurrentHashMap<String, ScoreBasedFeatureStatsEntity> map = new ConcurrentHashMap<>();
		for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
			String[] data_category = entry.getKey().split("-");
			String country = data_category[0];
			String league = data_category[1];
			Map<String, List<BookDataEntity>> entrySub = entry.getValue();
			for (List<BookDataEntity> entityList : entrySub.values()) {
				// null や空リストはスキップ
				if (entityList == null || entityList.isEmpty())
					continue;
				// decideBasedMain を呼び出して集計マップを取得
				map = decideBasedMain(entityList, country, league);
			}
		}

		// 登録・更新
		ExecutorService executor = Executors.newFixedThreadPool(map.size());
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (Map.Entry<String, ScoreBasedFeatureStatsEntity> entry : map.entrySet()) {
			CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
				ScoreBasedFeatureStatsEntity entity = entry.getValue();
				if (entity.isUpd()) {
					update(entity);
				} else {
					insert(entity);
				}
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
	 * @return
	 */
	private ConcurrentHashMap<String, ScoreBasedFeatureStatsEntity> decideBasedMain(List<BookDataEntity> entities,
			String country, String league) {
		BookDataEntity returnMaxEntity = ExecuteMainUtil.getMaxSeqEntities(entities);

		// situation決定
		String situation = (Integer.parseInt(returnMaxEntity.getHomeScore()) == 0
				&& Integer.parseInt(returnMaxEntity.getAwayScore()) == 0) ? AverageStatisticsSituationConst.NOSCORE
						: AverageStatisticsSituationConst.SCORE;

		// 各種flg + connectScoreの組み合わせ
		List<String> flgs = List.of(
				AverageStatisticsSituationConst.ALL_DATA,
				AverageStatisticsSituationConst.FIRST_DATA,
				AverageStatisticsSituationConst.SECOND_DATA,
				AverageStatisticsSituationConst.EACH_SCORE);

		// 各スコアの組み合わせ(例: ["0-0", "1-0", "1-1", ...])
		List<String> allScores = extractExistingScorePatterns(entities);
		ExecutorService executor = Executors.newFixedThreadPool(20); // スレッド数は状況に応じて調整
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		ConcurrentHashMap<String, ScoreBasedFeatureStatsEntity> allMap = new ConcurrentHashMap<>();
		for (String flg : flgs) {
			if (AverageStatisticsSituationConst.EACH_SCORE.equals(flg)) {
				if (!AverageStatisticsSituationConst.NOSCORE.equals(situation)) {
					for (String score : allScores) {
						if ("0-0".equals(score))
							continue; // 0-0 スコアはEACH_SCOREから除外
						CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
							basedEntities(allMap, entities, score, situation, flg, country, league);
						}, executor);
						futures.add(future);
					}
				} else {
					CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
						basedEntities(allMap, entities, null, situation, flg, country, league);
					}, executor);
					futures.add(future);
				}
			} else {
				// ALL_DATA / FIRST_DATA / SECOND_DATA → スコア単位でなく全体処理なので null を渡す
				CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
					basedEntities(allMap, entities, null, situation, flg, country, league);
				}, executor);
				futures.add(future);
			}
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
	 * @param connectScore 連結スコア
	 * @param situation 状況
	 * @param flg 設定フラグ
	 * @param country 国
	 * @param league リーグ
	 * @return
	 */
	private void basedEntities(ConcurrentHashMap<String, ScoreBasedFeatureStatsEntity> insertMap,
			List<BookDataEntity> entities, String connectScore, String situation, String flg,
			String country, String league) {
		// 既存のリスト
		List<BookDataEntity> filteredList = null;
		if (AverageStatisticsSituationConst.EACH_SCORE.equals(flg)) {
			filteredList = entities.stream()
					.filter(entity -> connectScore.equals(entity.getHomeScore() + "-"
							+ entity.getAwayScore()))
					.collect(Collectors.toList());
		} else if (AverageStatisticsSituationConst.ALL_DATA.equals(flg)) {
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

		// 取得メソッド
		ScoreBasedFeatureOutputDTO dto = getData(connectScore, situation, country, league);
		List<ScoreBasedFeatureStatsEntity> statList = dto.getList();
		boolean updFlg = dto.isUpdFlg();

		String[] minList = this.bean.getMinList();
		String[] maxList = this.bean.getMaxList();
		String[] aveList = this.bean.getAvgList();
		String[] sigmaList = this.bean.getSigmaList();
		Integer[] minCntList = this.bean.getCntList();
		Integer[] maxCntList = this.bean.getCntList();
		Integer[] aveCntList = this.bean.getCntList();
		Integer[] sigmaCntList = this.bean.getCntList();
		String[] tMinList = this.bean.getTimeMinList();
		String[] tMaxList = this.bean.getTimeMaxList();
		String[] tAveList = this.bean.getTimeAvgList();
		String[] tSigmaList = this.bean.getTimeSigmaList();
		Integer[] tMinCntList = this.bean.getTimeCntList();
		Integer[] tMaxCntList = this.bean.getTimeCntList();
		Integer[] tAveCntList = this.bean.getTimeCntList();
		Integer[] tSigmaCntList = this.bean.getTimeCntList();

		// データが存在した場合の初期化値上書き
		setInitData(minList, minCntList, maxList, maxCntList, aveList, aveCntList, sigmaList, sigmaCntList,
				tMinList, tMinCntList, tMaxList, tMaxCntList,
				tAveList, tAveCntList, tSigmaList, tSigmaCntList,
				statList);

		// 最小値,最大値,平均合計
		for (BookDataEntity filter : filteredList) {
			minList = setMin(filter, minList, minCntList);
			maxList = setMax(filter, maxList, maxCntList);
			aveList = setSumAve(filter, aveList, aveCntList);
			tMinList = setTimeMin(filter, tMinList, tMinCntList);
			tMaxList = setTimeMax(filter, tMaxList, tMaxCntList);
			tAveList = setTimeSumAve(filter, tAveList, tAveCntList);
		}
		// 平均導出
		aveList = commonDivision(aveList, aveCntList);
		tAveList = commonDivision(tAveList, tAveCntList);
		// 標準偏差合計
		for (BookDataEntity filter : filteredList) {
			sigmaList = setSumSigma(filter, aveList, sigmaList, sigmaCntList);
			tSigmaList = setTimeSumSigma(filter, tAveList, tSigmaList, tSigmaCntList);
		}
		// 標準偏差導出
		sigmaList = commonDivision(sigmaList, sigmaCntList);
		tSigmaList = commonDivision(tSigmaList, tSigmaCntList);

		ScoreBasedFeatureStatsEntity entity = new ScoreBasedFeatureStatsEntity();
		// 文字連結
		StringBuilder stringBuilder = new StringBuilder();
		for (int i = this.bean.getStartInsertIdx(); i < this.bean.getEndInsertIdx(); i++) {
			int idx = i - this.bean.getStartInsertIdx();
			String min = minList[idx] != null ? minList[idx] : "0.0";
			String max = maxList[idx] != null ? maxList[idx] : "0.0";
			String ave = aveList[idx] != null ? aveList[idx] : "0.0";
			String sigma = sigmaList[idx] != null ? sigmaList[idx] : "0.0";
			String tMin = tMinList[idx] != null ? tMinList[idx] : "0.0";
			String tMax = tMaxList[idx] != null ? tMaxList[idx] : "0.0";
			String tAve = tAveList[idx] != null ? tAveList[idx] : "0.0";
			String tSigma = tSigmaList[idx] != null ? tSigmaList[idx] : "0.0";

			// 1行分をカンマ区切りで連結
			stringBuilder.append(min).append(",")
					.append(minCntList[idx]).append(",")
					.append(max).append(",")
					.append(maxCntList[idx]).append(",")
					.append(ave).append(",")
					.append(aveCntList[idx]).append(",")
					.append(sigma).append(",")
					.append(sigmaCntList[idx]).append(",")
					.append(tMin).append(",")
					.append(tMinCntList[idx]).append(",")
					.append(tMax).append(",")
					.append(tMaxCntList[idx]).append(",")
					.append(tAve).append(",")
					.append(tAveCntList[idx]).append(",")
					.append(tSigma).append(",")
					.append(tSigmaCntList[idx]);
			entity = setStatValuesToEntity(entity, stringBuilder.toString(), i);
		}
		// その他情報を格納する
		entity = setOtherEntity(connectScore, situation, country, league, updFlg, entity);
		// スレッドセーフな格納
		insertMap.put(flg, entity);
	}

	/**
	 * 取得メソッド
	 * @param score スコア
	 * @param situation 状況
	 * @param country 国
	 * @param league リーグ
	 * @return
	 */
	private ScoreBasedFeatureOutputDTO getData(String score, String situation,
			String country, String league) {
		ScoreBasedFeatureOutputDTO scoreBasedFeatureOutputDTO = new ScoreBasedFeatureOutputDTO();
		List<ScoreBasedFeatureStatsEntity> datas = this.scoreBasedFeatureStatsRepository
				.findStatData(score, situation, country, league);
		if (!datas.isEmpty()) {
			scoreBasedFeatureOutputDTO.setUpdFlg(true);
			scoreBasedFeatureOutputDTO.setId(datas.get(0).getId());
			scoreBasedFeatureOutputDTO.setList(datas);
		} else {
			scoreBasedFeatureOutputDTO.setUpdFlg(false);
		}
		return scoreBasedFeatureOutputDTO;
	}

	/**
	 * 登録メソッド
	 * @param entity エンティティ
	 */
	private synchronized void insert(ScoreBasedFeatureStatsEntity entity) {
		final String METHOD_NAME = "insert";
		String fillChar = setLoggerFillChar(
				entity.getSituation(),
				entity.getScore(),
				entity.getCountry(),
				entity.getLeague());
		int result = this.scoreBasedFeatureStatsRepository.insert(entity);
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
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar, "BM_M023 登録件数: 1件");
	}

	/**
	 * 登録メソッド
	 * @param entity エンティティ
	 */
	private void update(ScoreBasedFeatureStatsEntity entity) {
		final String METHOD_NAME = "update";
		String fillChar = setLoggerFillChar(
				entity.getSituation(),
				entity.getScore(),
				entity.getCountry(),
				entity.getLeague());
		int result = this.scoreBasedFeatureStatsRepository.insert(entity);
		if (result != 1) {
			String messageCd = "更新エラー";
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
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar, "BM_M023 更新件数: 1件");
	}

	/**
	 * 初期値設定リスト
	 * @param minList
	 * @param minCntList
	 * @param maxList
	 * @param maxCntList
	 * @param aveList
	 * @param aveCntList
	 * @param sigmaList
	 * @param sigmaCntList
	 * @param tMinList
	 * @param tMinCntList
	 * @param tMaxList
	 * @param tMaxCntList
	 * @param tAveList
	 * @param tAveCntList
	 * @param tSigmaList
	 * @param tSigmaCntList
	 */
	private void setInitData(String[] minList, Integer[] minCntList, String[] maxList, Integer[] maxCntList,
			String[] aveList, Integer[] aveCntList, String[] sigmaList, Integer[] sigmaCntList,
			String[] tMinList, Integer[] tMinCntList, String[] tMaxList, Integer[] tMaxCntList,
			String[] tAveList, Integer[] tAveCntList, String[] tSigmaList, Integer[] tSigmaCntList,
			List<ScoreBasedFeatureStatsEntity> list) {
		final String METHOD_NAME = "setInitData";
		if (list != null && !list.isEmpty()) {
			ScoreBasedFeatureStatsEntity statEntity = list.get(0); // 最新データのみ使用
			Field[] fields = ScoreBasedFeatureStatsEntity.class.getDeclaredFields();
			for (int i = this.bean.getStartInsertIdx(); i < this.bean.getEndInsertIdx(); i++) {
				int idx = i - this.bean.getStartInsertIdx();
				Field field = fields[i];
				field.setAccessible(true);
				try {
					String statValue = (String) field.get(statEntity); // 例: "12.5, 33.8, 25.1, 5.3"
					if (statValue == null || statValue.isBlank())
						continue;
					String[] values = statValue.split(",");
					if (values.length >= 4) {
						minList[idx] = values[0].trim();
						minCntList[idx] = Integer.parseInt(values[1]);
						maxList[idx] = values[2].trim();
						maxCntList[idx] = Integer.parseInt(values[3]);
						aveList[idx] = values[4].trim();
						aveCntList[idx] = Integer.parseInt(values[5]);
						sigmaList[idx] = values[6].trim();
						sigmaCntList[idx] = Integer.parseInt(values[7]);
						tMinList[idx] = values[8].trim();
						tMinCntList[idx] = Integer.parseInt(values[9]);
						tMaxList[idx] = values[10].trim();
						tMaxCntList[idx] = Integer.parseInt(values[11]);
						tAveList[idx] = values[12].trim();
						tAveCntList[idx] = Integer.parseInt(values[13]);
						tSigmaList[idx] = values[14].trim();
						tSigmaCntList[idx] = Integer.parseInt(values[15]);
					}
				} catch (Exception e) {
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							"リフレクションエラー", e,
							"対象フィールド: " + field.getName());
				}
			}
		}
	}

	/**
	 * 最小値比較設定
	 * @param filteredList
	 * @param minList
	 * @param cntList
	 */
	private String[] setMin(BookDataEntity filter, String[] minList, Integer[] cntList) {
		final String METHOD_NAME = "setMin";
		// 形式設定
		initFormat(filter, minList);
		// BookDataEntityの全フィールドを取得
		Field[] allFields = BookDataEntity.class.getDeclaredFields();
		String fillChar = "";
		for (int i = this.bean.getStartIdx(); i < this.bean.getEndIdx(); i++) {
			int idx = i - this.bean.getStartIdx();
			Field field = allFields[i];
			field.setAccessible(true);
			fillChar = "フィールド名: " + field.getName() + ", 連番No: " + filter.getSeq();
			try {
				String currentValue = (String) field.get(filter);
				if (currentValue == null || currentValue.isBlank())
					continue;

				// 現在の最小値の形式に合わせて、同じ形式のみ比較
				String minValue = minList[idx];
				if (!isSameFormat(minValue, currentValue))
					continue;

				String currentCompNumeric = parseStatValue(currentValue);
				String minCompNumeric = parseStatValue(minValue);
				if (currentCompNumeric != null && minCompNumeric != null &&
						Double.parseDouble(currentCompNumeric) < Double.parseDouble(minCompNumeric)) {
					minList[idx] = currentValue; // 文字列ごと差し替え（形式維持）
				}
				// 件数カウント
				cntList[idx]++;
			} catch (Exception e) {
				String messageCd = "リフレクションエラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
				this.manageLoggerComponent.createSystemException(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						messageCd,
						e);
			}
		}
		return minList;
	}

	/**
	 * 最小値時間比較設定
	 * @param filteredList
	 * @param minList
	 * @param cntList
	 */
	private String[] setTimeMin(BookDataEntity filter, String[] minList, Integer[] cntList) {
		final String METHOD_NAME = "setTimeMin";
		// BookDataEntityの全フィールドを取得
		String fillChar = "";
		for (int i = this.bean.getStartIdx(); i < this.bean.getEndIdx(); i++) {
			int idx = i - this.bean.getStartIdx();
			fillChar = "連番No: " + filter.getSeq();
			try {
				String minTimeValue = minList[idx];
				String minTimeTmpValue = minTimeValue.replace("'", "");
				double minTimeTmpsValue = Double.parseDouble(minTimeTmpValue);
				double currentTimeValue = ExecuteMainUtil.convertToMinutes(filter.getTime());
				String currentTimeTmpValue = String.valueOf(currentTimeValue);
				if (currentTimeValue < minTimeTmpsValue) {
					minList[idx] = String.valueOf(currentTimeTmpValue) + "'";
				}
			} catch (Exception e) {
				String messageCd = "リフレクションエラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
				this.manageLoggerComponent.createSystemException(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						messageCd,
						e);
			}
		}
		return minList;
	}

	/**
	 * 最大値比較設定
	 * @param filteredList
	 * @param maxList
	 * @param cntList
	 */
	private String[] setMax(BookDataEntity filter, String[] maxList, Integer[] cntList) {
		final String METHOD_NAME = "setMax";
		// 形式設定
		initFormat(filter, maxList);
		// BookDataEntityの全フィールドを取得
		Field[] allFields = BookDataEntity.class.getDeclaredFields();
		String fillChar = "";
		for (int i = this.bean.getStartIdx(); i < this.bean.getEndIdx(); i++) {
			int idx = i - this.bean.getStartIdx();
			Field field = allFields[i];
			field.setAccessible(true);
			fillChar = "フィールド名: " + field.getName() + ", 連番No: " + filter.getSeq();
			try {
				String currentValue = (String) field.get(filter);
				if (currentValue == null || currentValue.isBlank())
					continue;

				// 現在の最小値の形式に合わせて、同じ形式のみ比較
				String maxValue = maxList[idx];
				if (!isSameFormat(maxValue, currentValue))
					continue;

				String currentCompNumeric = parseStatValue(currentValue);
				String maxCompNumeric = parseStatValue(maxValue);
				if (currentCompNumeric != null && maxCompNumeric != null &&
						Double.parseDouble(currentCompNumeric) > Double.parseDouble(maxCompNumeric)) {
					maxList[idx] = currentValue; // 文字列ごと差し替え（形式維持）
				}
				// 件数カウント
				cntList[idx]++;
			} catch (Exception e) {
				String messageCd = "リフレクションエラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
				this.manageLoggerComponent.createSystemException(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						messageCd,
						e);
			}
		}
		return maxList;
	}

	/**
	 * 最大値時間比較設定
	 * @param filteredList
	 * @param minList
	 * @param cntList
	 */
	private String[] setTimeMax(BookDataEntity filter, String[] maxList, Integer[] cntList) {
		final String METHOD_NAME = "setTimeMax";
		// BookDataEntityの全フィールドを取得
		String fillChar = "";
		for (int i = this.bean.getStartIdx(); i < this.bean.getEndIdx(); i++) {
			int idx = i - this.bean.getStartIdx();
			fillChar = "連番No: " + filter.getSeq();
			try {
				String maxTimeValue = maxList[idx];
				String maxTimeTmpValue = maxTimeValue.replace("'", "");
				double maxTimeTmpsValue = Double.parseDouble(maxTimeTmpValue);
				double currentTimeValue = ExecuteMainUtil.convertToMinutes(filter.getTime());
				String currentTimeTmpValue = String.valueOf(currentTimeValue);
				if (currentTimeValue > maxTimeTmpsValue) {
					maxList[idx] = String.valueOf(currentTimeTmpValue) + "'";
				}
			} catch (Exception e) {
				String messageCd = "リフレクションエラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
				this.manageLoggerComponent.createSystemException(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						messageCd,
						e);
			}
		}
		return maxList;
	}

	/**
	 * 平均値計算のための加算処理（値を加算し、件数もインクリメント）
	 * @param filter BookDataEntity（1レコード分）
	 * @param aveList 平均値用の一時加算リスト（String型）
	 * @param cntList 件数カウント（Integer型）
	 * @return 加算後のaveList
	 */
	private String[] setSumAve(BookDataEntity filter, String[] aveList, Integer[] cntList) {
		final String METHOD_NAME = "setAve";
		Field[] allFields = BookDataEntity.class.getDeclaredFields();
		String fillChar = "";
		for (int i = this.bean.getStartIdx(); i < this.bean.getEndIdx(); i++) {
			int idx = i - this.bean.getStartIdx();
			Field field = allFields[i];
			field.setAccessible(true);
			fillChar = "フィールド名: " + field.getName() + ", 連番No: " + filter.getSeq();
			try {
				String currentValue = (String) field.get(filter);
				if (currentValue == null || currentValue.isBlank())
					continue;

				// 数値化（成功数・もしくは%・通常値）
				String numericStr = parseStatValue(currentValue);
				if (numericStr == null || numericStr.isBlank() || isPercentAndFractionFormat(currentValue))
					continue;

				// 文字列 → double → 加算
				double numeric = Double.parseDouble(numericStr);
				double prev = 0.0;
				if (aveList[idx] != null && !aveList[idx].isBlank()) {
					prev = Double.parseDouble(aveList[idx]);
				}
				double sum = prev + numeric;
				aveList[idx] = String.valueOf(sum);
				// 件数カウント
				cntList[idx]++;
			} catch (NumberFormatException e) {
				// 数値変換失敗時はスキップ（加算しない）
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						"NumberFormatException: " + field.getName(), e, fillChar);
			} catch (Exception e) {
				// リフレクション等の例外
				String messageCd = "リフレクションエラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
				this.manageLoggerComponent.createSystemException(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e);
			}
		}
		return aveList;
	}

	/**
	 * 平均値時間合計設定
	 * @param filteredList
	 * @param aveList
	 * @param cntList
	 */
	private String[] setTimeSumAve(BookDataEntity filter, String[] aveList, Integer[] cntList) {
		final String METHOD_NAME = "setTimeSumAve";
		// BookDataEntityの全フィールドを取得
		String fillChar = "";
		for (int i = this.bean.getStartIdx(); i < this.bean.getEndIdx(); i++) {
			int idx = i - this.bean.getStartIdx();
			fillChar = "連番No: " + filter.getSeq();
			try {
				String aveTimeValue = aveList[idx];
				String aveTimeTmpValue = aveTimeValue.replace("'", "");
				double aveTimeTmpsValue = Double.parseDouble(aveTimeTmpValue);
				double currentTimeValue = ExecuteMainUtil.convertToMinutes(filter.getTime());
				aveList[idx] = String.valueOf(aveTimeTmpsValue + currentTimeValue) + "'";
				cntList[idx]++;
			} catch (NumberFormatException e) {
				// 数値変換失敗時はスキップ（加算しない）
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						"NumberFormatException", e, fillChar);
			} catch (Exception e) {
				String messageCd = "リフレクションエラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
				this.manageLoggerComponent.createSystemException(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						messageCd,
						e);
			}
		}
		return aveList;
	}

	/**
	 * 標準偏差用の差分²加算処理（X% (X/X) 形式は除外）
	 * @param filter BookDataEntity（1行分）
	 * @param avgList 平均値リスト（String[]）
	 * @param sigmaList 差分²加算リスト（String[]）
	 * @param cntList 件数リスト（Integer[]）
	 * @return 更新済み sigmaList
	 */
	private String[] setSumSigma(BookDataEntity filter, String[] avgList, String[] sigmaList, Integer[] cntList) {
		final String METHOD_NAME = "setSumSigma";
		Field[] allFields = BookDataEntity.class.getDeclaredFields();
		String fillChar = "";
		for (int i = this.bean.getStartIdx(); i < this.bean.getEndIdx(); i++) {
			int idx = i - this.bean.getStartIdx();
			Field field = allFields[i];
			field.setAccessible(true);
			fillChar = "フィールド名: " + field.getName() + ", 連番No: " + filter.getSeq();
			try {
				String currentValue = (String) field.get(filter);
				String avgStr = avgList[idx];
				// スキップ条件：空 or null or X% (X/X) 形式
				if (currentValue == null || currentValue.isBlank() || isPercentAndFractionFormat(currentValue)) {
					continue;
				}
				if (avgStr == null || avgStr.isBlank())
					continue;
				// 値と平均を double にして差分²を計算
				double value = Double.parseDouble(parseStatValue(currentValue));
				double avg = Double.parseDouble(avgStr);
				double diffSquared = Math.pow(value - avg, 2);

				double prev = 0.0;
				if (sigmaList[idx] != null && !sigmaList[idx].isBlank()) {
					prev = Double.parseDouble(sigmaList[idx]);
				}
				sigmaList[idx] = String.valueOf(prev + diffSquared);
				cntList[idx]++;
			} catch (NumberFormatException e) {
				// 数値変換失敗時はスキップ（加算しない）
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						"NumberFormatException: " + field.getName(), e, fillChar);
			} catch (Exception e) {
				// リフレクション等の例外
				String messageCd = "リフレクションエラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
				this.manageLoggerComponent.createSystemException(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e);
			}
		}
		return sigmaList;
	}

	/**
	 * 時間の標準偏差用の差分²加算処理
	 * @param filter BookDataEntity（1行分）
	 * @param avgList 平均値リスト（String[]）
	 * @param sigmaList 差分²加算リスト（String[]）
	 * @param cntList 件数リスト（Integer[]）
	 * @return 更新済み sigmaList
	 */
	private String[] setTimeSumSigma(BookDataEntity filter, String[] aveList, String[] sigmaList, Integer[] cntList) {
		final String METHOD_NAME = "setTimeSumSigma";
		String fillChar = "連番No: " + filter.getSeq();
		try {
			// 試合時間（文字列）→ 分に変換
			double currentTimeValue = ExecuteMainUtil.convertToMinutes(filter.getTime());

			for (int i = this.bean.getStartIdx(); i < this.bean.getEndIdx(); i++) {
				int idx = i - this.bean.getStartIdx();
				// 平均値とsigmaの取得
				String aveStr = aveList[idx];
				String sigmaStr = sigmaList[idx];
				if (aveStr == null || aveStr.isBlank())
					continue;
				double averageValue = Double.parseDouble(aveStr.replace("'", ""));
				double sigmaValue = 0.0;
				if (sigmaStr != null && !sigmaStr.isBlank()) {
					sigmaValue = Double.parseDouble(sigmaStr.replace("'", ""));
				}
				// 差分²を加算
				double diffSquared = Math.pow(currentTimeValue - averageValue, 2);
				double updated = sigmaValue + diffSquared;
				// 更新
				sigmaList[idx] = String.valueOf(updated) + "'";
				cntList[idx]++;
			}
		} catch (NumberFormatException e) {
			// 数値変換失敗時はスキップ（加算しない）
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					"NumberFormatException", e, fillChar);
		} catch (Exception e) {
			String messageCd = "リフレクションエラー";
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			this.manageLoggerComponent.createSystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					e);
		}
		return sigmaList;
	}

	/**
	 * 形式を揃える
	 * @param entity BookDataEntity
	 * @param list
	 */
	private void initFormat(BookDataEntity entity,
			String[] list) {
		final String METHOD_NAME = "initFormat";
		final int FEATURE_START = 10;
		String feature_name = "";
		try {
			Field[] allFields = BookDataEntity.class.getDeclaredFields();
			for (int i = FEATURE_START; i < FEATURE_START + AverageStatisticsSituationConst.COUNTER; i++) {
				feature_name = allFields[i].getName();
				String feature_value = (String) allFields[i].get(entity);
				list[FEATURE_START - 10] = getInitialValueByFormat(feature_value);
			}
		} catch (Exception ex) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					"形式更新中に例外発生", ex, feature_name);
		}
	}

	/**
	 * 初期化時の値を、元の文字列形式に基づいて適切に戻す
	 * 例:
	 * - "65%" → "0.0%"
	 * - "65% (13/20)" → "0.0% (0/0)"
	 */
	private String getInitialValueByFormat(String valueStr) {
		if (valueStr == null || valueStr.isBlank()) {
			return "0.0";
		}
		// % (x/y) が含まれる場合
		if (valueStr.matches(".*%\\s*\\(\\s*\\d+\\s*/\\s*\\d+\\s*\\).*")) {
			return "0.0% (0/0)";
		}
		// % だけの場合
		if (valueStr.contains("%")) {
			return "0.0%";
		}
		// 通常の数値
		return "0.0";
	}

	/**
	 * 同じフォーマットのみ比較
	 * @param a
	 * @param b
	 * @return
	 */
	private boolean isSameFormat(String a, String b) {
		if (a == null || b == null)
			return false;
		// "0.0%" vs "65%" → OK, "0.0% (0/0)" vs "70% (13/20)" → OK
		if (a.contains("% (") && b.contains("% ("))
			return true;
		if (a.contains("%") && b.contains("%") && !a.contains("(") && !b.contains("("))
			return true;
		if (a.contains("/") && b.contains("/") && !a.contains("%"))
			return true;
		if (!a.contains("%") && !a.contains("/") && !b.contains("%") && !b.contains("/"))
			return true;
		return false;
	}

	/**
	 * データのパース
	 * @param valueStr
	 * @return
	 */
	private String parseStatValue(String valueStr) {
		if (valueStr == null || valueStr.isBlank())
			return null;
		try {
			if (valueStr.contains("%") && valueStr.contains("(")) {
				// 形式: 65% (13/20)
				List<String> list = ExecuteMainUtil.splitFlgGroup(valueStr);
				if (list.size() >= 2 && !list.get(1).isBlank()) {
					return String.valueOf(Double.parseDouble(list.get(1).trim())); // 分子（成功数）を優先
				}
			} else if (valueStr.contains("%")) {
				// 形式: 65%
				int idx = valueStr.indexOf('%');
				String percent = valueStr.substring(0, idx).trim();
				return String.valueOf(Double.parseDouble(percent));
			} else {
				// 通常の数値
				return String.valueOf(Double.parseDouble(valueStr.trim()));
			}
		} catch (NumberFormatException e) {
			return null;
		}
		return valueStr;
	}

	/**
	 * 値が "X% (X/X)" 形式かを判定
	 */
	private boolean isPercentAndFractionFormat(String valueStr) {
		return valueStr != null
				&& valueStr.contains("%")
				&& valueStr.contains("(")
				&& valueStr.contains("/")
				&& valueStr.contains(")");
	}

	/**
	 * 共通割り算リスト
	 * @param list
	 * @param cntList
	 * @return
	 */
	private String[] commonDivision(String[] list, Integer[] cntList) {
		// 平均導出
		for (int i = 0; i < this.bean.getEndIdx() - this.bean.getStartIdx(); i++) {
			if (isPercentAndFractionFormat(list[i])) {
				list[i] = "";
			} else {
				list[i] = String.valueOf(Double.parseDouble(list[i]) / cntList[i]);
			}
		}
		return list;
	}

	/**
	 * insertStr の値を ScoreBasedFeatureStatsEntity に反映する
	 * @param entity 対象の ScoreBasedFeatureStatsEntity
	 * @param insertStr カンマ区切りの統計値（min,max,avg,sigma,...） ※順序は BookDataEntity の homeExp ～ awayInterceptCount に対応
	 * @param ind インデックス
	 */
	private ScoreBasedFeatureStatsEntity setStatValuesToEntity(ScoreBasedFeatureStatsEntity entity,
			String insertStr, int ind) {
		final String METHOD_NAME = "setStatValuesToEntity";
		try {
			// エンティティの全フィールド取得
			Field[] allFields = ScoreBasedFeatureStatsEntity.class.getDeclaredFields();
			Field field = allFields[ind];
			field.setAccessible(true);
			// String 型のフィールドに値を代入
			field.set(entity, insertStr);
		} catch (Exception e) {
			String messageCd = "リフレクションエラー";
			String fillChar = "ScoreBasedFeatureStatsEntity への値設定エラー";
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			this.manageLoggerComponent.createSystemException(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e);
		}
		return entity;
	}

	/**
	 * 残りの値をエンティティに格納する
	 * @param score スコア
	 * @param situation 状況
	 * @param country 国
	 * @param league リーグ
	 * @param updFlg 更新フラグ
	 * @return
	 */
	private ScoreBasedFeatureStatsEntity setOtherEntity(String score, String situation,
			String country, String league, Boolean updFlg, ScoreBasedFeatureStatsEntity entity) {
		entity.setUpd(updFlg);
		entity.setScore(score);
		entity.setSituation(situation);
		entity.setCountry(country);
		entity.setLeague(league);
		return entity;
	}

	/**
	 * スコアパターン補助メソッド
	 * @param entities
	 * @return
	 */
	private List<String> extractExistingScorePatterns(List<BookDataEntity> entities) {
		return entities.stream()
				.map(e -> e.getHomeScore() + "-" + e.getAwayScore())
				.distinct()
				.collect(Collectors.toList());
	}

	/**
	 * 埋め字設定
	 * @param situation 状況
	 * @param score スコア
	 * @param country 国
	 * @param league リーグ
	 * @return
	 */
	private String setLoggerFillChar(String situation, String score, String country, String league) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("状況: " + situation + ", ");
		stringBuilder.append("スコア: " + score + ", ");
		stringBuilder.append("国: " + country + ", ");
		stringBuilder.append("リーグ: " + league);
		return stringBuilder.toString();
	}
}
