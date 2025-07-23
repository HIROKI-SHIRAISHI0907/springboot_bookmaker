package dev.application.analyze.bm_m026;

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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.bm_m023.AverageStatisticsSituationConst;
import dev.application.analyze.bm_m023.BmM023M024M026InitBean;
import dev.application.analyze.bm_m023.ScoreBasedFeatureStatsEntity;
import dev.application.analyze.bm_m023.StatFormatResolver;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.EachTeamScoreBasedFeatureStatsRepository;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M026統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
@Transactional
public class EachTeamScoreBasedFeatureStat extends StatFormatResolver implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = EachTeamScoreBasedFeatureStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = EachTeamScoreBasedFeatureStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M026_EACH_TEAM_SCORE_BASED_FEATURE";

	// クラススコープに以下を追加
	private final ConcurrentHashMap<String, Object> lockMap = new ConcurrentHashMap<>();

	private Object getLock(String key) {
		return lockMap.computeIfAbsent(key, k -> new Object());
	}

	/** Beanクラス */
	@Autowired
	private BmM023M024M026InitBean bean;

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

		// 全リーグ・国を走査
		ConcurrentHashMap<String, EachTeamScoreBasedFeatureEntity> resultMap = new ConcurrentHashMap<>();
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
				ConcurrentHashMap<String, EachTeamScoreBasedFeatureEntity> partialMap =
                        decideBasedMain(entityList, country, league);
                resultMap.putAll(partialMap);
			}
		}

		// 登録・更新
		ExecutorService executor = Executors.newFixedThreadPool(resultMap.size());
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (Map.Entry<String, EachTeamScoreBasedFeatureEntity> entry : resultMap.entrySet()) {
			CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
				EachTeamScoreBasedFeatureEntity entity = entry.getValue();
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
	private ConcurrentHashMap<String, EachTeamScoreBasedFeatureEntity> decideBasedMain(List<BookDataEntity> entities,
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
		ConcurrentHashMap<String, EachTeamScoreBasedFeatureEntity> allMap = new ConcurrentHashMap<>();
		for (int i = 1; i <= 2; i++) {
			String team = (i == 1) ? returnMaxEntity.getHomeTeamName() : returnMaxEntity.getAwayTeamName();
			String ha = (i == 1) ? "H" : "A";
			for (String flg : flgs) {
				if (AverageStatisticsSituationConst.EACH_SCORE.equals(flg)) {
					if (!AverageStatisticsSituationConst.NOSCORE.equals(situation)) {
						for (String score : allScores) {
							if ("0-0".equals(score))
								continue; // 0-0 スコアはEACH_SCOREから除外
							CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
								basedEntities(allMap, entities, score, situation, flg, country, league, team, ha);
							}, executor);
							futures.add(future);
						}
					} else {
						if (!AverageStatisticsSituationConst.EACH_SCORE.equals(flg)) {
							CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
								basedEntities(allMap, entities, null, situation, flg, country, league, team, ha);
							}, executor);
							futures.add(future);
						}
					}
				} else {
					// ALL_DATA / FIRST_DATA / SECOND_DATA → スコア単位でなく全体処理なので null を渡す
					CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
						basedEntities(allMap, entities, null, situation, flg, country, league, team, ha);
					}, executor);
					futures.add(future);
				}
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
	 * @param team チーム
	 * @param ha home or away
	 * @return
	 */
	private void basedEntities(ConcurrentHashMap<String, EachTeamScoreBasedFeatureEntity> insertMap,
			List<BookDataEntity> entities, String connectScore, String situation, String flg,
			String country, String league, String team, String ha) {
		System.err.println("connectScore: " + connectScore + ", situation: " + situation
				+ ", flg: " + flg + ", team: " + team + ", ha: " + ha);
		String mapKey = flg + "_" + team + "_" + ha + (connectScore != null ? "_" + connectScore : "");
		String lockKey = team + "_" + (connectScore != null ? connectScore : "ALL") + "_" + situation;

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

		// 非同期
		synchronized (getLock(lockKey)) {
			// 取得メソッド
			EachTeamScoreBasedFeatureOutputDTO dto = getData(connectScore, situation, country, league, team);
			List<EachTeamScoreBasedFeatureEntity> statList = dto.getList();
			boolean updFlg = dto.isUpdFlg();

			String[] minList = this.bean.getMinList().clone();
			String[] maxList = this.bean.getMaxList().clone();
			String[] aveList = this.bean.getAvgList().clone();
			String[] sigmaList = this.bean.getSigmaList().clone();
			Integer[] minCntList = this.bean.getCntList().clone();
			Integer[] maxCntList = this.bean.getCntList().clone();
			Integer[] aveCntList = this.bean.getCntList().clone();
			Integer[] sigmaCntList = this.bean.getCntList().clone();
			String[] tMinList = this.bean.getTimeMinList().clone();
			String[] tMaxList = this.bean.getTimeMaxList().clone();
			String[] tAveList = this.bean.getTimeAvgList().clone();
			String[] tSigmaList = this.bean.getTimeSigmaList().clone();
			Integer[] tMinCntList = this.bean.getTimeCntList().clone();
			Integer[] tMaxCntList = this.bean.getTimeCntList().clone();
			Integer[] tAveCntList = this.bean.getTimeCntList().clone();
			Integer[] tSigmaCntList = this.bean.getTimeCntList().clone();

			// データが存在した場合の初期化値上書き
			setInitData(minList, minCntList, maxList, maxCntList, aveList, aveCntList, sigmaList, sigmaCntList,
					tMinList, tMinCntList, tMaxList, tMaxCntList,
					tAveList, tAveCntList, tSigmaList, tSigmaCntList,
					statList, ha);

			// 最小値,最大値,平均合計
			for (BookDataEntity filter : filteredList) {
				minList = setMin(filter, minList, minCntList, ha);
				maxList = setMax(filter, maxList, maxCntList, ha);
				aveList = setSumAve(filter, aveList, aveCntList, ha);
				tMinList = setTimeMin(filter, tMinList, tMinCntList, ha);
				tMaxList = setTimeMax(filter, tMaxList, tMaxCntList, ha);
				tAveList = setTimeSumAve(filter, tAveList, tAveCntList, ha);
			}
			// 平均導出
			aveList = commonDivision(aveList, aveCntList, "", ha);
			tAveList = commonDivision(tAveList, tAveCntList, "'", ha);
			// 標準偏差合計
			for (BookDataEntity filter : filteredList) {
				sigmaList = setSumSigma(filter, aveList, sigmaList, sigmaCntList, ha);
				tSigmaList = setTimeSumSigma(filter, tAveList, tSigmaList, tSigmaCntList, ha);
			}
			// 標準偏差導出
			sigmaList = commonDivision(sigmaList, sigmaCntList, "", ha);
			tSigmaList = commonDivision(tSigmaList, tSigmaCntList, "'", ha);
			for (int i = 0; i < sigmaList.length; i++) {
				// 等間隔でskip
				if (("H".equals(ha) && i % 2 == 1) || ("A".equals(ha) && i % 2 == 0)) {
					continue;
				}
				sigmaList[i] = String.format("%.2f",
						Math.sqrt(Double.parseDouble(sigmaList[i])));
				tSigmaList[i] = String.format("%.2f",
						Math.sqrt(Double.parseDouble(tSigmaList[i].replace("'", ""))));
			}

			EachTeamScoreBasedFeatureEntity entity = new EachTeamScoreBasedFeatureEntity();
			// 文字連結
			StringBuilder stringBuilder = new StringBuilder();
			for (int i = this.bean.getStartInsertIdx(); i < this.bean.getEndInsertIdx(); i++) {
				int idx = i - this.bean.getStartInsertIdx();
				String min = formatDecimal(minList[idx]);
				String max = formatDecimal(maxList[idx]);
				String ave = formatDecimal(aveList[idx]);
				String sigma = formatDecimal(sigmaList[idx]);
				String tMin = formatDecimal(tMinList[idx]);
				String tMax = formatDecimal(tMaxList[idx]);
				String tAve = formatDecimal(tAveList[idx]);
				String tSigma = formatDecimal(tSigmaList[idx]);

				// 1行分をカンマ区切りで連結
				stringBuilder.append(min).append(",")
						.append(minCntList[idx]).append(",")
						.append(max).append(",")
						.append(maxCntList[idx]).append(",")
						.append(ave).append(",")
						.append(aveCntList[idx]).append(",")
						.append(sigma).append(",")
						.append(sigmaCntList[idx]).append(",")
						.append(tMin + "'").append(",")
						.append(tMinCntList[idx]).append(",")
						.append(tMax + "'").append(",")
						.append(tMaxCntList[idx]).append(",")
						.append(tAve + "'").append(",")
						.append(tAveCntList[idx]).append(",")
						.append(tSigma + "'").append(",")
						.append(tSigmaCntList[idx]);
				entity = setStatValuesToEntity(entity, stringBuilder.toString(), i);
				stringBuilder.setLength(0);
			}
			// その他情報を格納する
			entity = setOtherEntity(connectScore, situation, country, league, team, updFlg, entity);
			// スレッドセーフな格納
			insertMap.put(mapKey, entity);
		}
	}

	/**
	 * 取得メソッド
	 * @param score スコア
	 * @param situation 状況
	 * @param country 国
	 * @param league リーグ
	 * @param team チーム
	 * @return
	 */
	private EachTeamScoreBasedFeatureOutputDTO getData(String score, String situation,
			String country, String league, String team) {
		EachTeamScoreBasedFeatureOutputDTO eachTeamScoreBasedFeatureOutputDTO = new EachTeamScoreBasedFeatureOutputDTO();
		List<EachTeamScoreBasedFeatureEntity> datas = this.eachTeamScoreBasedFeatureStatsRepository
				.findStatData(score, situation, country, league, team);
		if (!datas.isEmpty()) {
			eachTeamScoreBasedFeatureOutputDTO.setUpdFlg(true);
			eachTeamScoreBasedFeatureOutputDTO.setId(datas.get(0).getId());
			eachTeamScoreBasedFeatureOutputDTO.setList(datas);
		} else {
			eachTeamScoreBasedFeatureOutputDTO.setUpdFlg(false);
		}
		return eachTeamScoreBasedFeatureOutputDTO;
	}

	/**
	 * 登録メソッド
	 * @param entity エンティティ
	 */
	private synchronized void insert(EachTeamScoreBasedFeatureEntity entity) {
		final String METHOD_NAME = "insert";
		String fillChar = setLoggerFillChar(
				entity.getSituation(),
				entity.getScore(),
				entity.getCountry(),
				entity.getLeague(),
				entity.getTeam());
		int result = this.eachTeamScoreBasedFeatureStatsRepository.insert(entity);
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
	private void update(EachTeamScoreBasedFeatureEntity entity) {
		final String METHOD_NAME = "update";
		String fillChar = setLoggerFillChar(
				entity.getSituation(),
				entity.getScore(),
				entity.getCountry(),
				entity.getLeague(),
				entity.getTeam());
		int result = this.eachTeamScoreBasedFeatureStatsRepository.updateStatValues(entity);
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
	 * @param ha
	 */
	private void setInitData(String[] minList, Integer[] minCntList, String[] maxList, Integer[] maxCntList,
			String[] aveList, Integer[] aveCntList, String[] sigmaList, Integer[] sigmaCntList,
			String[] tMinList, Integer[] tMinCntList, String[] tMaxList, Integer[] tMaxCntList,
			String[] tAveList, Integer[] tAveCntList, String[] tSigmaList, Integer[] tSigmaCntList,
			List<EachTeamScoreBasedFeatureEntity> list, String ha) {
		final String METHOD_NAME = "setInitData";
		if (list != null && !list.isEmpty()) {
			EachTeamScoreBasedFeatureEntity statEntity = list.get(0); // 最新データのみ使用
			Field[] fields = ScoreBasedFeatureStatsEntity.class.getDeclaredFields();
			for (int i = this.bean.getStartInsertIdx(); i < this.bean.getEndInsertIdx(); i++) {
				int idx = i - this.bean.getStartInsertIdx();
				if (("H".equals(ha) && idx % 2 == 1) || ("A".equals(ha) && idx % 2 == 0)) {
					continue;
				}
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
	 * @param ha
	 */
	private String[] setMin(BookDataEntity filter, String[] minList, Integer[] cntList, String ha) {
		final String METHOD_NAME = "setMin";
		// 形式設定
		initFormat(filter, minList);
		// BookDataEntityの全フィールドを取得
		Field[] allFields = BookDataEntity.class.getDeclaredFields();
		String fillChar = "";
		for (int i = this.bean.getStartScoreInsertIdx(); i < this.bean.getEndScoreInsertIdx(); i++) {
			int idx = i - this.bean.getStartScoreInsertIdx();
			if (("H".equals(ha) && idx % 2 == 1) || ("A".equals(ha) && idx % 2 == 0)) {
				continue;
			}
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
	 * @param ha
	 */
	private String[] setTimeMin(BookDataEntity filter, String[] minList, Integer[] cntList, String ha) {
		final String METHOD_NAME = "setTimeMin";
		// BookDataEntityの全フィールドを取得
		String fillChar = "";
		for (int i = this.bean.getStartScoreInsertIdx(); i < this.bean.getEndScoreInsertIdx(); i++) {
			int idx = i - this.bean.getStartScoreInsertIdx();
			if (("H".equals(ha) && idx % 2 == 1) || ("A".equals(ha) && idx % 2 == 0)) {
				continue;
			}
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
	 * 最大値比較設定
	 * @param filteredList
	 * @param maxList
	 * @param cntList
	 * @param ha
	 */
	private String[] setMax(BookDataEntity filter, String[] maxList, Integer[] cntList, String ha) {
		final String METHOD_NAME = "setMax";
		// 形式設定
		initFormat(filter, maxList);
		// BookDataEntityの全フィールドを取得
		Field[] allFields = BookDataEntity.class.getDeclaredFields();
		String fillChar = "";
		for (int i = this.bean.getStartScoreInsertIdx(); i < this.bean.getEndScoreInsertIdx(); i++) {
			int idx = i - this.bean.getStartScoreInsertIdx();
			if (("H".equals(ha) && idx % 2 == 1) || ("A".equals(ha) && idx % 2 == 0)) {
				continue;
			}
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
	 * @param ha
	 */
	private String[] setTimeMax(BookDataEntity filter, String[] maxList, Integer[] cntList, String ha) {
		final String METHOD_NAME = "setTimeMax";
		// BookDataEntityの全フィールドを取得
		String fillChar = "";
		for (int i = this.bean.getStartScoreInsertIdx(); i < this.bean.getEndScoreInsertIdx(); i++) {
			int idx = i - this.bean.getStartScoreInsertIdx();
			if (("H".equals(ha) && idx % 2 == 1) || ("A".equals(ha) && idx % 2 == 0)) {
				continue;
			}
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
	 * 平均値計算のための加算処理（値を加算し、件数もインクリメント）
	 * @param filter BookDataEntity（1レコード分）
	 * @param aveList 平均値用の一時加算リスト（String型）
	 * @param cntList 件数カウント（Integer型）
	 * @param ha homeaway
	 * @return 加算後のaveList
	 */
	private String[] setSumAve(BookDataEntity filter, String[] aveList, Integer[] cntList, String ha) {
		final String METHOD_NAME = "setSumAve";
		Field[] allFields = BookDataEntity.class.getDeclaredFields();
		String fillChar = "";
		for (int i = this.bean.getStartScoreInsertIdx(); i < this.bean.getEndScoreInsertIdx(); i++) {
			int idx = i - this.bean.getStartScoreInsertIdx();
			if (("H".equals(ha) && idx % 2 == 1) || ("A".equals(ha) && idx % 2 == 0)) {
				continue;
			}
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
	 * @param ha
	 */
	private String[] setTimeSumAve(BookDataEntity filter, String[] aveList, Integer[] cntList, String ha) {
		final String METHOD_NAME = "setTimeSumAve";
		// BookDataEntityの全フィールドを取得
		String fillChar = "";
		for (int i = this.bean.getStartScoreInsertIdx(); i < this.bean.getEndScoreInsertIdx(); i++) {
			int idx = i - this.bean.getStartScoreInsertIdx();
			if (("H".equals(ha) && idx % 2 == 1) || ("A".equals(ha) && idx % 2 == 0)) {
				continue;
			}
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
	 * @param ha homeaway
	 * @return 更新済み sigmaList
	 */
	private String[] setSumSigma(BookDataEntity filter, String[] avgList, String[] sigmaList, Integer[] cntList,
			String ha) {
		final String METHOD_NAME = "setSumSigma";
		Field[] allFields = BookDataEntity.class.getDeclaredFields();
		String fillChar = "";
		for (int i = this.bean.getStartScoreInsertIdx(); i < this.bean.getEndScoreInsertIdx(); i++) {
			int idx = i - this.bean.getStartScoreInsertIdx();
			if (("H".equals(ha) && idx % 2 == 1) || ("A".equals(ha) && idx % 2 == 0)) {
				continue;
			}
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
	 * @param ha homeaway
	 * @return 更新済み sigmaList
	 */
	private String[] setTimeSumSigma(BookDataEntity filter, String[] aveList, String[] sigmaList, Integer[] cntList,
			String ha) {
		final String METHOD_NAME = "setTimeSumSigma";
		String fillChar = "連番No: " + filter.getSeq();
		try {
			// 試合時間（文字列）→ 分に変換
			double currentTimeValue = ExecuteMainUtil.convertToMinutes(filter.getTime());

			for (int i = this.bean.getStartScoreInsertIdx(); i < this.bean.getEndScoreInsertIdx(); i++) {
				int idx = i - this.bean.getStartScoreInsertIdx();
				if (("H".equals(ha) && idx % 2 == 1) || ("A".equals(ha) && idx % 2 == 0)) {
					continue;
				}
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
	 * 共通割り算リスト
	 * @param list
	 * @param cntList
	 * @param suffix
	 * @param ha
	 * @return
	 */
	private String[] commonDivision(String[] list, Integer[] cntList, String suffix, String ha) {
		// 平均導出
		for (int i = 0; i < this.bean.getEndScoreInsertIdx() - this.bean.getStartScoreInsertIdx(); i++) {
			if (isPercentAndFractionFormat(list[i])) {
				list[i] = "";
			} else {
				if (cntList[i] == 0) {
					list[i] = "0" + suffix;
				} else {
					list[i] = String.valueOf(Double.parseDouble(list[i].replace(suffix, "")) / cntList[i]) + suffix;
				}
			}
		}
		return list;
	}

	/**
	 * 形式を揃える
	 * @param entity BookDataEntity
	 * @param list
	 */
	private void initFormat(BookDataEntity entity,
			String[] list) {
		final String METHOD_NAME = "initFormat";
		final int FEATURE_START = 11;
		String feature_name = "";
		try {
			Field[] allFields = BookDataEntity.class.getDeclaredFields();
			for (int i = FEATURE_START; i < FEATURE_START + AverageStatisticsSituationConst.COUNTER; i++) {
				feature_name = allFields[i].getName();
				allFields[i].setAccessible(true);
				String feature_value = (String) allFields[i].get(entity);
				list[i - FEATURE_START] = getInitialValueByFormat(feature_value);
			}
		} catch (Exception ex) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					"形式更新中に例外発生", ex, feature_name);
		}
	}

	/**
	 * insertStr の値を ScoreBasedFeatureStatsEntity に反映する
	 * @param entity 対象の ScoreBasedFeatureStatsEntity
	 * @param insertStr カンマ区切りの統計値（min,max,avg,sigma,...） ※順序は BookDataEntity の homeExp ～ awayInterceptCount に対応
	 * @param ind インデックス
	 */
	private EachTeamScoreBasedFeatureEntity setStatValuesToEntity(EachTeamScoreBasedFeatureEntity entity,
			String insertStr, int ind) {
		final String METHOD_NAME = "setStatValuesToEntity";
		try {
			// エンティティの全フィールド取得
			Field[] allFields = EachTeamScoreBasedFeatureEntity.class.getDeclaredFields();
			Field field = allFields[ind];
			field.setAccessible(true);
			// String 型のフィールドに値を代入
			field.set(entity, insertStr);
		} catch (Exception e) {
			String messageCd = "リフレクションエラー";
			String fillChar = "EachTeamScoreBasedFeatureEntity への値設定エラー";
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
	 * @param team チーム
	 * @param updFlg 更新フラグ
	 * @return
	 */
	private EachTeamScoreBasedFeatureEntity setOtherEntity(String score, String situation,
			String country, String league, String team, Boolean updFlg, EachTeamScoreBasedFeatureEntity entity) {
		entity.setUpd(updFlg);
		entity.setScore(score);
		entity.setSituation(situation);
		entity.setCountry(country);
		entity.setLeague(league);
		entity.setTeam(team);
		return entity;
	}

	/**
	 * 埋め字設定
	 * @param situation 状況
	 * @param score スコア
	 * @param country 国
	 * @param league リーグ
	 * @return
	 */
	private String setLoggerFillChar(String situation, String score,
			String country, String league, String team) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("状況: " + situation + ", ");
		stringBuilder.append("スコア: " + score + ", ");
		stringBuilder.append("国: " + country + ", ");
		stringBuilder.append("リーグ: " + league + ", ");
		stringBuilder.append("チーム: " + team);
		return stringBuilder.toString();
	}

}
