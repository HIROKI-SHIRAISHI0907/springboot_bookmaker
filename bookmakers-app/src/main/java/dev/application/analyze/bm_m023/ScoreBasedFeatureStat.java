package dev.application.analyze.bm_m023;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.bm_m030.BmM030StatEncryptionBean;
import dev.application.analyze.bm_m030.StatEncryptionEntity;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.bm.ScoreBasedFeatureStatsRepository;
import dev.application.domain.repository.bm.StatEncryptionRepository;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M023統計分析ロジック
 * @author shiraishitoshio
 */
@Component
public class ScoreBasedFeatureStat extends StatFormatResolver implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ScoreBasedFeatureStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ScoreBasedFeatureStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M023_SCORE_BASED_FEATURE";

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M023";

	/** ★ lockMap（BM_M026移植） */
	private final ConcurrentHashMap<String, Object> lockMap = new ConcurrentHashMap<>();

	/** Beanクラス */
	@Autowired
	private BmM023M024M026InitBean bmM023M024M026InitBean;

	/** Beanクラス */
	@Autowired
	private BmM030StatEncryptionBean bmM030StatEncryptionBean;

	/** ScoreBasedFeatureStatsRepositoryレポジトリクラス */
	@Autowired
	private ScoreBasedFeatureStatsRepository scoreBasedFeatureStatsRepository;

	/** StatEncryptionRepositoryレポジトリクラス */
	@Autowired
	private StatEncryptionRepository statEncryptionRepository;

	/** ログ管理ラッパー*/
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) throws Exception {
		final String METHOD_NAME = "calcStat";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		bmM030StatEncryptionBean.init();

		// 保存データを取得
		ConcurrentHashMap<String, StatEncryptionEntity> bmM30Map = this.bmM030StatEncryptionBean.getEncMap();

		// 全リーグ・国を走査
		for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
			ConcurrentHashMap<String, List<ScoreBasedFeatureStatsEntity>> map = new ConcurrentHashMap<>();

			String[] data_category = ExecuteMainUtil.splitLeagueInfo(entry.getKey());
			String country = data_category[0];
			String league = data_category[1];

			Map<String, List<BookDataEntity>> entrySub = entry.getValue();
			for (List<BookDataEntity> entityList : entrySub.values()) {
				// null や空リストはスキップ
				if (entityList == null || entityList.isEmpty()) {
					continue;
				}

				// decideBasedMain を呼び出して集計マップを取得
				map = decideBasedMain(entityList, country, league, bmM30Map);
				if (map == null) {
					continue;
				}

				// 登録・更新
				ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, map.size()));
				List<CompletableFuture<Void>> futures = new ArrayList<>();

				for (Map.Entry<String, List<ScoreBasedFeatureStatsEntity>> entrys : map.entrySet()) {
					CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
						for (ScoreBasedFeatureStatsEntity subSubEntity : entrys.getValue()) {
							if (subSubEntity.isUpd()) {
								update(subSubEntity);
							} else {
								insert(subSubEntity);
							}
						}
					}, executor);
					futures.add(future);
				}

				CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
				executor.shutdown();
			}
		}

		// 保存マップ登録（★ BM_M026と同じく update/insert を分ける）
		for (Map.Entry<String, StatEncryptionEntity> entry : bmM30Map.entrySet()) {
			StatEncryptionEntity e = entry.getValue();

			// key: home-away-chkBody
			String[] split = entry.getKey().split("-");
			if (split.length >= 2) {
				e.setHome(split[0]);
				e.setAway(split[1]);
			}

			StatEncryptionEntity newEntrys = encryption(e);

			if (newEntrys.isUpdFlg()) {
				int result = this.statEncryptionRepository.updateEncValues(newEntrys);
				if (result != 1) {
					String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
					this.rootCauseWrapper.throwUnexpectedRowCount(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							messageCd,
							1, result,
							String.format("id=%s, home=%s, away=%s, chkBody=%s",
									newEntrys.getId(), e.getHome(), e.getAway(), e.getChkBody()));
				}
				String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, BM_NUMBER + " 更新件数: " + result + "件");
			} else {
				int result = this.statEncryptionRepository.insert(newEntrys);
				if (result != 1) {
					String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
					this.rootCauseWrapper.throwUnexpectedRowCount(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							messageCd,
							1, result,
							null);
				}
				String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, BM_NUMBER + " 登録件数: " + result + "件");
			}
		}

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
	 * @param bmM30Map 保存データマップ
	 */
	private ConcurrentHashMap<String, List<ScoreBasedFeatureStatsEntity>> decideBasedMain(
			List<BookDataEntity> entities,
			String country, String league,
			ConcurrentHashMap<String, StatEncryptionEntity> bmM30Map) {

		BookDataEntity returnMaxEntity = ExecuteMainUtil.getMaxSeqEntities(entities);
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, null, null, returnMaxEntity.getFilePath());

		if (!BookMakersCommonConst.FIN.equals(returnMaxEntity.getTime())) {
			return null;
		}

		String home = returnMaxEntity.getHomeTeamName();
		String away = returnMaxEntity.getAwayTeamName();

		// situation決定
		String situation = (Integer.parseInt(returnMaxEntity.getHomeScore()) == 0
				&& Integer.parseInt(returnMaxEntity.getAwayScore()) == 0)
						? AverageStatisticsSituationConst.NOSCORE
						: AverageStatisticsSituationConst.SCORE;

		// 各種flg + connectScoreの組み合わせ
		List<String> flgs = List.of(
				AverageStatisticsSituationConst.ALL_DATA,
				AverageStatisticsSituationConst.FIRST_DATA,
				AverageStatisticsSituationConst.SECOND_DATA,
				AverageStatisticsSituationConst.EACH_SCORE);

		// 各スコアの組み合わせ
		List<String> allScores = extractExistingScorePatterns(entities);

		ExecutorService executor = Executors.newFixedThreadPool(6);
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		ConcurrentHashMap<String, List<ScoreBasedFeatureStatsEntity>> allMap = new ConcurrentHashMap<>();

		for (String flg : flgs) {
			if (AverageStatisticsSituationConst.EACH_SCORE.equals(flg)) {
				if (!AverageStatisticsSituationConst.NOSCORE.equals(situation)) {
					for (String score : allScores) {
						if ("0-0".equals(score)) {
							continue;
						}
						CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
							basedEntities(allMap, entities, score, situation, flg, country, league, home, away, bmM30Map);
						}, executor);
						futures.add(future);
					}
				}
			} else {
				CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
					basedEntities(allMap, entities, null, situation, flg, country, league, home, away, bmM30Map);
				}, executor);
				futures.add(future);
			}
		}

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		executor.shutdown();
		return allMap;
	}

	/**
	 * 基準エンティティ指定
	 */
	private void basedEntities(
			ConcurrentHashMap<String, List<ScoreBasedFeatureStatsEntity>> insertMap,
			List<BookDataEntity> entities,
			String connectScore,
			String situation,
			String flg,
			String country,
			String league,
			String home,
			String away,
			ConcurrentHashMap<String, StatEncryptionEntity> bmM30Map) {

		// 既存のリスト
		List<BookDataEntity> filteredList = null;
		if (AverageStatisticsSituationConst.EACH_SCORE.equals(flg)) {
			filteredList = entities.stream()
					.filter(entity -> connectScore.equals(entity.getHomeScore() + "-" + entity.getAwayScore()))
					.collect(Collectors.toList());
		} else if (AverageStatisticsSituationConst.ALL_DATA.equals(flg)) {
			filteredList = entities;
		} else {
			// ハーフタイムの通番を特定
			String halfTimeSeq = ExecuteMainUtil.getHalfEntities(entities).getSeq();
			if (AverageStatisticsSituationConst.FIRST_DATA.equals(flg)) {
				filteredList = entities.stream()
						.filter(entity -> entity.getSeq().compareTo(halfTimeSeq) <= 0)
						.collect(Collectors.toList());
			} else if (AverageStatisticsSituationConst.SECOND_DATA.equals(flg)) {
				filteredList = entities.stream()
						.filter(entity -> entity.getSeq().compareTo(halfTimeSeq) > 0)
						.collect(Collectors.toList());
			}
		}

		if (filteredList == null || filteredList.isEmpty()) {
			return;
		}

		String chkBody;

		// flgがALL,1st,2ndの時は,それをsituation扱いとする
		boolean updFlg;
		String id;
		List<ScoreBasedFeatureStatsEntity> statList;

		if (AverageStatisticsSituationConst.ALL_DATA.equals(flg)
				|| AverageStatisticsSituationConst.FIRST_DATA.equals(flg)
				|| AverageStatisticsSituationConst.SECOND_DATA.equals(flg)) {

			String score = flg;
			chkBody = flg;

			ScoreBasedFeatureOutputDTO dto = getData(score, situation, country, league);
			statList = dto.getList();
			updFlg = dto.isUpdFlg();
			id = dto.getId();

		} else {
			chkBody = connectScore;

			ScoreBasedFeatureOutputDTO dto = getData(connectScore, situation, country, league);
			statList = dto.getList();
			updFlg = dto.isUpdFlg();
			id = dto.getId();
		}

		Map<String, Function<BookDataEntity, String>> fieldMap = this.bmM030StatEncryptionBean.getFieldMap();
		final List<BookDataEntity> filteredFinalList = filteredList;
		final String chkFinalBody = chkBody;

		// ★ key固定（home-away-chkBody）
		final String key = home + "-" + away + "-" + chkFinalBody;

		// ★ lock + merge（BM_M026移植）
		StatEncryptionEntity decidedEntity;
		synchronized (getLock(key)) {
			StatEncryptionEntity exist = bmM30Map.get(key);
			if (exist != null) {
				StatEncryptionEntity addPart = buildBmM30Form(filteredFinalList, country, league, chkFinalBody, fieldMap);
				StatEncryptionEntity merged = mergeStatEncryptionEntity(exist, addPart);
				merged.setId(exist.getId());
				merged.setUpdFlg(true);
				bmM30Map.put(key, merged);
			} else {
				StatEncryptionEntity fresh = buildBmM30Form(filteredFinalList, country, league, chkFinalBody, fieldMap);
				fresh.setId(null);
				fresh.setUpdFlg(false);
				bmM30Map.put(key, fresh);
			}
			decidedEntity = bmM30Map.get(key);
		}

		if (decidedEntity == null) {
			return;
		}

		String[] minList = this.bmM023M024M026InitBean.getMinList().clone();
		String[] maxList = this.bmM023M024M026InitBean.getMaxList().clone();
		String[] aveList = this.bmM023M024M026InitBean.getAvgList().clone();
		String[] sigmaList = this.bmM023M024M026InitBean.getSigmaList().clone();
		Integer[] minCntList = this.bmM023M024M026InitBean.getCntList().clone();
		Integer[] maxCntList = this.bmM023M024M026InitBean.getCntList().clone();
		Integer[] aveCntList = this.bmM023M024M026InitBean.getCntList().clone();
		Integer[] sigmaCntList = this.bmM023M024M026InitBean.getCntList().clone();
		String[] tMinList = this.bmM023M024M026InitBean.getTimeMinList().clone();
		String[] tMaxList = this.bmM023M024M026InitBean.getTimeMaxList().clone();
		String[] tAveList = this.bmM023M024M026InitBean.getTimeAvgList().clone();
		String[] tSigmaList = this.bmM023M024M026InitBean.getTimeSigmaList().clone();
		Integer[] tMinCntList = this.bmM023M024M026InitBean.getTimeCntList().clone();
		Integer[] tMaxCntList = this.bmM023M024M026InitBean.getTimeCntList().clone();
		Integer[] tAveCntList = this.bmM023M024M026InitBean.getTimeCntList().clone();
		Integer[] tSigmaCntList = this.bmM023M024M026InitBean.getTimeCntList().clone();

		// データが存在した場合の初期化値上書き
		setInitData(minList, minCntList, maxList, maxCntList, aveList, aveCntList, sigmaList, sigmaCntList,
				tMinList, tMinCntList, tMaxList, tMaxCntList,
				tAveList, tAveCntList, tSigmaList, tSigmaCntList, statList);

		// 最小値,最大値,平均合計
		BookDataEntity returnDataEntity = ExecuteMainUtil.getMaxSeqEntities(entities);
		initFormat(returnDataEntity, minList, "Min");
		initFormat(returnDataEntity, maxList, "Max");

		for (BookDataEntity filter : filteredList) {
			minList = setMin(filter, minList, minCntList);
			maxList = setMax(filter, maxList, maxCntList);
			aveList = setSumAve(filter, aveList, aveCntList);
			tMinList = setTimeMin(filter, tMinList, tMinCntList);
			tMaxList = setTimeMax(filter, tMaxList, tMaxCntList);
			tAveList = setTimeSumAve(filter, tAveList, tAveCntList);
		}

		// 平均導出
		aveList = commonDivision(aveList, aveCntList, "");
		tAveList = commonDivision(tAveList, tAveCntList, "'");

		// 標準偏差合計
		for (BookDataEntity filter : filteredList) {
			sigmaList = setSumSigma(filter, aveList, sigmaList, sigmaCntList);
			tSigmaList = setTimeSumSigma(filter, tAveList, tSigmaList, tSigmaCntList);
		}

		// 標準偏差導出
		sigmaList = commonDivision(sigmaList, sigmaCntList, "");
		tSigmaList = commonDivision(tSigmaList, tSigmaCntList, "'");

		for (int i = 0; i < sigmaList.length; i++) {
			sigmaList[i] = String.format("%.2f", Math.sqrt(Double.parseDouble(sigmaList[i])));
			tSigmaList[i] = String.format("%.2f", Math.sqrt(Double.parseDouble(tSigmaList[i].replace("'", ""))));
		}

		// 歪度,尖度導出（StatEncryptionから取得）
		String[] aveSkewKurtList = this.bmM023M024M026InitBean.getAvgList().clone();
		String[] sigmaSkewKurtList = this.bmM023M024M026InitBean.getSigmaList().clone();
		String[] skewnessList = this.bmM023M024M026InitBean.getSkewnessList().clone();
		String[] kurtosisList = this.bmM023M024M026InitBean.getKurtosisList().clone();
		Integer[] kurtosisCntList = this.bmM023M024M026InitBean.getSkewnessCntList().clone();

		skewnessList = setSkewness(decidedEntity, skewnessList, aveSkewKurtList, sigmaSkewKurtList, kurtosisCntList);
		kurtosisList = setKurtosis(decidedEntity, kurtosisList, aveSkewKurtList, sigmaSkewKurtList, kurtosisCntList);

		ScoreBasedFeatureStatsEntity entity = new ScoreBasedFeatureStatsEntity();

		// 文字連結
		StringBuilder stringBuilder = new StringBuilder();
		for (int i = this.bmM023M024M026InitBean.getStartInsertIdx();
				i <= this.bmM023M024M026InitBean.getEndInsertIdx(); i++) {

			int idx = i - this.bmM023M024M026InitBean.getStartInsertIdx();

			String min = formatDecimal(minList[idx]);
			String max = formatDecimal(maxList[idx]);
			String ave = formatDecimal(aveList[idx]);
			String sigma = formatDecimal(sigmaList[idx]);
			String tMin = formatDecimal(tMinList[idx]);
			String tMax = formatDecimal(tMaxList[idx]);
			String tAve = formatDecimal(tAveList[idx]);
			String tSigma = formatDecimal(tSigmaList[idx]);
			String skewness = skewnessList[idx];
			String kurtosis = kurtosisList[idx];

			stringBuilder.append(min).append(",")
					.append(minCntList[idx]).append(",")
					.append(max).append(",")
					.append(maxCntList[idx]).append(",")
					.append(ave).append(",")
					.append(aveCntList[idx]).append(",")
					.append(sigma).append(",")
					.append(sigmaCntList[idx]).append(",")
					.append(tMin).append("'").append(",")
					.append(tMinCntList[idx]).append(",")
					.append(tMax).append("'").append(",")
					.append(tMaxCntList[idx]).append(",")
					.append(tAve).append("'").append(",")
					.append(tAveCntList[idx]).append(",")
					.append(tSigma).append("'").append(",")
					.append(tSigmaCntList[idx]).append(",")
					.append(skewness).append(",")
					.append(kurtosis);

			entity = setStatValuesToEntity(entity, stringBuilder.toString(), i);
			stringBuilder.setLength(0);
		}

		// その他情報を格納する
		if (AverageStatisticsSituationConst.ALL_DATA.equals(flg)
				|| AverageStatisticsSituationConst.FIRST_DATA.equals(flg)
				|| AverageStatisticsSituationConst.SECOND_DATA.equals(flg)) {
			entity = setOtherEntity(flg, situation, country, league, updFlg, id, entity);
		} else {
			entity = setOtherEntity(connectScore, situation, country, league, updFlg, id, entity);
		}

		// スレッドセーフな格納
		insertMap.computeIfAbsent(flg, k -> new ArrayList<>()).add(entity);
	}

	/**
	 * 取得メソッド
	 */
	private ScoreBasedFeatureOutputDTO getData(String score, String situation, String country, String league) {
		ScoreBasedFeatureOutputDTO dto = new ScoreBasedFeatureOutputDTO();

		List<ScoreBasedFeatureStatsEntity> data =
				this.scoreBasedFeatureStatsRepository.findStatData(score, situation, country, league);

		if (data != null && !data.isEmpty()) {
			dto.setUpdFlg(true);
			dto.setId(data.get(0).getId());
			dto.setList(data);
		} else {
			dto.setUpdFlg(false);
		}
		return dto;
	}

	/**
	 * 登録メソッド
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
			String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, fillChar);
			this.manageLoggerComponent.createSystemException(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, null);
		}

		String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 登録件数: " + result + "件 (" + fillChar + ")");
	}

	/**
	 * 更新メソッド
	 */
	private void update(ScoreBasedFeatureStatsEntity entity) {
		final String METHOD_NAME = "update";
		String fillChar = setLoggerFillChar(
				entity.getSituation(),
				entity.getScore(),
				entity.getCountry(),
				entity.getLeague());

		int result = this.scoreBasedFeatureStatsRepository.updateStatValues(entity);
		if (result != 1) {
			String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, fillChar);
			this.manageLoggerComponent.createSystemException(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, null);
		}

		String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 更新件数: " + result + "件 (" + fillChar + ")");
	}

	/**
	 * 初期値設定リスト
	 */
	private void setInitData(
			String[] minList, Integer[] minCntList,
			String[] maxList, Integer[] maxCntList,
			String[] aveList, Integer[] aveCntList,
			String[] sigmaList, Integer[] sigmaCntList,
			String[] tMinList, Integer[] tMinCntList,
			String[] tMaxList, Integer[] tMaxCntList,
			String[] tAveList, Integer[] tAveCntList,
			String[] tSigmaList, Integer[] tSigmaCntList,
			List<ScoreBasedFeatureStatsEntity> list) {

		final String METHOD_NAME = "setInitData";

		if (list != null && !list.isEmpty()) {
			ScoreBasedFeatureStatsEntity statEntity = list.get(0);
			Field[] fields = ScoreBasedFeatureStatsEntity.class.getDeclaredFields();

			for (int i = this.bmM023M024M026InitBean.getStartInsertIdx();
					i <= this.bmM023M024M026InitBean.getEndInsertIdx(); i++) {

				int idx = i - this.bmM023M024M026InitBean.getStartInsertIdx();
				Field field = fields[i];
				field.setAccessible(true);

				try {
					String statValue = (String) field.get(statEntity);
					if (statValue == null || statValue.isBlank()) {
						continue;
					}
					String[] values = statValue.split(",");
					if (values.length >= 16) {
						minList[idx] = values[0].trim();
						minCntList[idx] = Integer.parseInt(values[1].trim());
						maxList[idx] = values[2].trim();
						maxCntList[idx] = Integer.parseInt(values[3].trim());
						aveList[idx] = values[4].trim();
						aveCntList[idx] = Integer.parseInt(values[5].trim());
						sigmaList[idx] = values[6].trim();
						sigmaCntList[idx] = Integer.parseInt(values[7].trim());
						tMinList[idx] = values[8].trim();
						tMinCntList[idx] = Integer.parseInt(values[9].trim());
						tMaxList[idx] = values[10].trim();
						tMaxCntList[idx] = Integer.parseInt(values[11].trim());
						tAveList[idx] = values[12].trim();
						tAveCntList[idx] = Integer.parseInt(values[13].trim());
						tSigmaList[idx] = values[14].trim();
						tSigmaCntList[idx] = Integer.parseInt(values[15].trim());
					}
				} catch (Exception e) {
					String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
							"対象フィールド: " + field.getName());
				}
			}
		}
	}

	/**
	 * 最小値比較設定
	 */
	private String[] setMin(BookDataEntity filter, String[] minList, Integer[] cntList) {
		final String METHOD_NAME = "setMin";
		Field[] allFields = BookDataEntity.class.getDeclaredFields();
		String fillChar = "";

		for (int i = this.bmM023M024M026InitBean.getStartIdx();
				i <= this.bmM023M024M026InitBean.getEndIdx(); i++) {

			int idx = i - this.bmM023M024M026InitBean.getStartIdx();
			Field field = allFields[i];
			field.setAccessible(true);

			fillChar = "フィールド名: " + field.getName() + ", 連番No: " + filter.getSeq();
			try {
				String currentValue = (String) field.get(filter);
				fillChar += " , 値: " + currentValue;

				if (currentValue == null || currentValue.isBlank()) {
					continue;
				}

				String minValue = minList[idx];
				if (!isSameFormat(minValue, currentValue)) {
					continue;
				}

				String currentCompNumeric = parseStatValue(currentValue);
				String minCompNumeric = parseStatValue(minValue);

				if (currentCompNumeric != null && minCompNumeric != null
						&& Double.parseDouble(currentCompNumeric) < Double.parseDouble(minCompNumeric)) {
					minList[idx] = currentValue;
				}
				cntList[idx]++;
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			}
		}
		return minList;
	}

	/**
	 * 最小値時間比較設定
	 */
	private String[] setTimeMin(BookDataEntity filter, String[] minList, Integer[] cntList) {
		final String METHOD_NAME = "setTimeMin";
		String fillChar = "";

		for (int i = this.bmM023M024M026InitBean.getStartInsertIdx();
				i <= this.bmM023M024M026InitBean.getEndInsertIdx(); i++) {

			int idx = i - this.bmM023M024M026InitBean.getStartInsertIdx();
			fillChar = "連番No: " + filter.getSeq();
			try {
				String minTimeValue = minList[idx];
				String minTimeTmpValue = minTimeValue.replace("'", "");
				double minTimeTmpsValue = Double.parseDouble(minTimeTmpValue);

				double currentTimeValue = ExecuteMainUtil.convertToMinutes(filter.getTime());
				if (currentTimeValue < minTimeTmpsValue) {
					minList[idx] = String.valueOf(currentTimeValue) + "'";
				}
				cntList[idx]++;
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			}
		}
		return minList;
	}

	/**
	 * 最大値比較設定
	 */
	private String[] setMax(BookDataEntity filter, String[] maxList, Integer[] cntList) {
		final String METHOD_NAME = "setMax";
		Field[] allFields = BookDataEntity.class.getDeclaredFields();
		String fillChar = "";

		for (int i = this.bmM023M024M026InitBean.getStartIdx();
				i <= this.bmM023M024M026InitBean.getEndIdx(); i++) {

			int idx = i - this.bmM023M024M026InitBean.getStartIdx();
			Field field = allFields[i];
			field.setAccessible(true);

			fillChar = "フィールド名: " + field.getName() + ", 連番No: " + filter.getSeq();
			try {
				String currentValue = (String) field.get(filter);
				fillChar += " , 値: " + currentValue;

				if (currentValue == null || currentValue.isBlank()) {
					continue;
				}

				String maxValue = maxList[idx];
				if (!isSameFormat(maxValue, currentValue)) {
					continue;
				}

				String currentCompNumeric = parseStatValue(currentValue);
				String maxCompNumeric = parseStatValue(maxValue);

				if (currentCompNumeric != null && maxCompNumeric != null
						&& Double.parseDouble(currentCompNumeric) > Double.parseDouble(maxCompNumeric)) {
					maxList[idx] = currentValue;
				}
				cntList[idx]++;
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			}
		}
		return maxList;
	}

	/**
	 * 最大値時間比較設定
	 */
	private String[] setTimeMax(BookDataEntity filter, String[] maxList, Integer[] cntList) {
		final String METHOD_NAME = "setTimeMax";
		String fillChar = "";

		for (int i = this.bmM023M024M026InitBean.getStartInsertIdx();
				i <= this.bmM023M024M026InitBean.getEndInsertIdx(); i++) {

			int idx = i - this.bmM023M024M026InitBean.getStartInsertIdx();
			fillChar = "連番No: " + filter.getSeq();
			try {
				String maxTimeValue = maxList[idx];
				String maxTimeTmpValue = maxTimeValue.replace("'", "");
				double maxTimeTmpsValue = Double.parseDouble(maxTimeTmpValue);

				double currentTimeValue = ExecuteMainUtil.convertToMinutes(filter.getTime());
				if (currentTimeValue > maxTimeTmpsValue) {
					maxList[idx] = String.valueOf(currentTimeValue) + "'";
				}
				cntList[idx]++;
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			}
		}
		return maxList;
	}

	/**
	 * 平均値計算のための加算処理
	 */
	private String[] setSumAve(BookDataEntity filter, String[] aveList, Integer[] cntList) {
		final String METHOD_NAME = "setSumAve";
		Field[] allFields = BookDataEntity.class.getDeclaredFields();
		String fillChar = "";

		for (int i = this.bmM023M024M026InitBean.getStartIdx();
				i <= this.bmM023M024M026InitBean.getEndIdx(); i++) {

			int idx = i - this.bmM023M024M026InitBean.getStartIdx();
			Field field = allFields[i];
			field.setAccessible(true);

			fillChar = "フィールド名: " + field.getName() + ", 連番No: " + filter.getSeq();
			try {
				String currentValue = (String) field.get(filter);
				fillChar += " , 値: " + currentValue;

				if (currentValue == null || currentValue.isBlank()) {
					continue;
				}

				String numericStr = parseStatValue(currentValue);
				if (numericStr == null || numericStr.isBlank()) {
					continue;
				}

				double numeric = Double.parseDouble(numericStr);
				double prev = (aveList[idx] != null && !aveList[idx].isBlank()) ? Double.parseDouble(aveList[idx]) : 0.0;

				aveList[idx] = String.valueOf(prev + numeric);
				cntList[idx]++;
			} catch (NumberFormatException e) {
				String messageCd = MessageCdConst.MCD00015E_NUMBERFORMAT_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			}
		}
		return aveList;
	}

	/**
	 * 平均値時間合計設定
	 */
	private String[] setTimeSumAve(BookDataEntity filter, String[] aveList, Integer[] cntList) {
		final String METHOD_NAME = "setTimeSumAve";
		String fillChar = "";

		for (int i = this.bmM023M024M026InitBean.getStartInsertIdx();
				i <= this.bmM023M024M026InitBean.getEndInsertIdx(); i++) {

			int idx = i - this.bmM023M024M026InitBean.getStartInsertIdx();
			fillChar = "連番No: " + filter.getSeq();
			try {
				String aveTimeValue = aveList[idx];
				String aveTimeTmpValue = aveTimeValue.replace("'", "");
				double aveTimeTmpsValue = Double.parseDouble(aveTimeTmpValue);

				double currentTimeValue = ExecuteMainUtil.convertToMinutes(filter.getTime());

				aveList[idx] = String.valueOf(aveTimeTmpsValue + currentTimeValue) + "'";
				cntList[idx]++;
			} catch (NumberFormatException e) {
				String messageCd = MessageCdConst.MCD00015E_NUMBERFORMAT_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			}
		}
		return aveList;
	}

	/**
	 * 標準偏差用の差分²加算処理
	 */
	private String[] setSumSigma(BookDataEntity filter, String[] avgList, String[] sigmaList, Integer[] cntList) {
		final String METHOD_NAME = "setSumSigma";
		Field[] allFields = BookDataEntity.class.getDeclaredFields();
		String fillChar = "";

		for (int i = this.bmM023M024M026InitBean.getStartIdx();
				i <= this.bmM023M024M026InitBean.getEndIdx(); i++) {

			int idx = i - this.bmM023M024M026InitBean.getStartIdx();
			Field field = allFields[i];
			field.setAccessible(true);

			fillChar = "フィールド名: " + field.getName() + ", 連番No: " + filter.getSeq();
			try {
				String currentValue = (String) field.get(filter);
				fillChar += " , 値: " + currentValue;

				String avgStr = avgList[idx];
				if (currentValue == null || currentValue.isBlank()) {
					continue;
				}
				if (avgStr == null || avgStr.isBlank()) {
					continue;
				}

				double value = Double.parseDouble(parseStatValue(currentValue));
				double avg = Double.parseDouble(avgStr);
				double diffSquared = Math.pow(value - avg, 2);

				double prev = (sigmaList[idx] != null && !sigmaList[idx].isBlank()) ? Double.parseDouble(sigmaList[idx])
						: 0.0;

				sigmaList[idx] = String.valueOf(prev + diffSquared);
				cntList[idx]++;
			} catch (NumberFormatException e) {
				String messageCd = MessageCdConst.MCD00015E_NUMBERFORMAT_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			}
		}
		return sigmaList;
	}

	/**
	 * 時間の標準偏差用の差分²加算処理
	 */
	private String[] setTimeSumSigma(BookDataEntity filter, String[] aveList, String[] sigmaList, Integer[] cntList) {
		final String METHOD_NAME = "setTimeSumSigma";
		String fillChar = "連番No: " + filter.getSeq();
		try {
			double currentTimeValue = ExecuteMainUtil.convertToMinutes(filter.getTime());

			for (int i = this.bmM023M024M026InitBean.getStartInsertIdx();
					i <= this.bmM023M024M026InitBean.getEndInsertIdx(); i++) {

				int idx = i - this.bmM023M024M026InitBean.getStartInsertIdx();

				String aveStr = aveList[idx];
				String sigmaStr = sigmaList[idx];
				if (aveStr == null || aveStr.isBlank()) {
					continue;
				}

				double averageValue = Double.parseDouble(aveStr.replace("'", ""));
				double sigmaValue = (sigmaStr != null && !sigmaStr.isBlank())
						? Double.parseDouble(sigmaStr.replace("'", ""))
						: 0.0;

				double diffSquared = Math.pow(currentTimeValue - averageValue, 2);
				double updated = sigmaValue + diffSquared;

				sigmaList[idx] = String.valueOf(updated) + "'";
				cntList[idx]++;
			}
		} catch (NumberFormatException e) {
			String messageCd = MessageCdConst.MCD00015E_NUMBERFORMAT_ERROR;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
		}
		return sigmaList;
	}

	/**
	 * 歪度設定
	 */
	private String[] setSkewness(
			StatEncryptionEntity entity,
			String[] skewnessList,
			String[] aveList,
			String[] sigmaList,
			Integer[] cntList) {

		final String METHOD_NAME = "setSkewness";

		Double[] skewness = new Double[AverageStatisticsSituationConst.COUNTER];
		for (int i = 0; i < skewness.length; i++) {
			skewness[i] = 0.0;
		}

		Field[] allFields = StatEncryptionEntity.class.getDeclaredFields();
		String fillChar = "";

		for (int i = this.bmM030StatEncryptionBean.getStartEncryptionIdx();
				i <= this.bmM030StatEncryptionBean.getEndEncryptionIdx(); i++) {

			int idx = i - this.bmM030StatEncryptionBean.getStartEncryptionIdx();
			Field field = allFields[i];
			field.setAccessible(true);

			fillChar = "フィールド名: " + field.getName();
			try {
				String currentValue = (String) field.get(entity);
				fillChar += " , 値: " + currentValue;

				if (currentValue == null || currentValue.isBlank() || isPercentAndFractionFormat(currentValue)) {
					continue;
				}

				String[] skewList = currentValue.split(",");

				int cnt = 0;
				ScoreBasedFeatureOutputDTO dto1 = setSkewnessOrKurtosisSumAve(skewList, cnt);
				String skewSumAve = dto1.getAve();
				cnt = Integer.parseInt(dto1.getCnt());

				String skewAve = (cnt == 0) ? "" : String.valueOf(Double.parseDouble(skewSumAve) / cnt);

				cnt = 0;
				ScoreBasedFeatureOutputDTO dto2 = setSkewnessOrKurtosisSumSigma(skewList, skewAve, cnt);
				String skewSumSigma = dto2.getSigma();
				cnt = Integer.parseInt(dto2.getCnt());

				String skewSigma = (cnt == 1) ? ""
						: String.valueOf(Math.sqrt(Double.parseDouble(skewSumSigma) / (cnt - 1)));

				if ("".equals(skewAve) || "".equals(skewSigma)) {
					continue;
				}

				for (String skew : skewList) {
					String currentSkewnessNumeric = parseStatValue(skew);
					if (currentSkewnessNumeric == null) {
						continue;
					}
					skewness[idx] += Math.pow(
							(Double.parseDouble(currentSkewnessNumeric) - Double.parseDouble(skewAve))
									/ Double.parseDouble(skewSigma),
							3);
				}

				cntList[idx] = cnt;
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			}
		}

		for (int i = 0; i < skewness.length; i++) {
			int cnt = cntList[i];
			double skew = skewness[i];
			double result = (cnt / ((cnt - 1.0) * (cnt - 2.0))) * skew;
			skewnessList[i] = String.format("%.3f", result);
		}

		return skewnessList;
	}

	/**
	 * 尖度設定
	 */
	private String[] setKurtosis(
			StatEncryptionEntity entity,
			String[] kurtosisList,
			String[] aveList,
			String[] sigmaList,
			Integer[] cntList) {

		final String METHOD_NAME = "setKurtosis";

		Double[] kurtosis = new Double[AverageStatisticsSituationConst.COUNTER];
		for (int i = 0; i < kurtosis.length; i++) {
			kurtosis[i] = 0.0;
		}

		Field[] allFields = StatEncryptionEntity.class.getDeclaredFields();
		String fillChar = "";

		for (int i = this.bmM030StatEncryptionBean.getStartEncryptionIdx();
				i <= this.bmM030StatEncryptionBean.getEndEncryptionIdx(); i++) {

			int idx = i - this.bmM030StatEncryptionBean.getStartEncryptionIdx();
			Field field = allFields[i];
			field.setAccessible(true);

			fillChar = "フィールド名: " + field.getName();
			try {
				String currentValue = (String) field.get(entity);
				fillChar += " , 値: " + currentValue;

				if (currentValue == null || currentValue.isBlank() || isPercentAndFractionFormat(currentValue)) {
					continue;
				}

				String[] kurtList = currentValue.split(",");

				int cnt = 0;
				ScoreBasedFeatureOutputDTO dto1 = setSkewnessOrKurtosisSumAve(kurtList, cnt);
				String kurtSumAve = dto1.getAve();
				cnt = Integer.parseInt(dto1.getCnt());

				String kurtAve = (cnt == 0) ? "" : String.valueOf(Double.parseDouble(kurtSumAve) / cnt);

				cnt = 0;
				ScoreBasedFeatureOutputDTO dto2 = setSkewnessOrKurtosisSumSigma(kurtList, kurtAve, cnt);
				String kurtSumSigma = dto2.getSigma();
				cnt = Integer.parseInt(dto2.getCnt());

				String kurtSigma = (cnt == 1) ? ""
						: String.valueOf(Math.sqrt(Double.parseDouble(kurtSumSigma) / (cnt - 1)));

				if ("".equals(kurtAve) || "".equals(kurtSigma)) {
					continue;
				}

				for (String kurt : kurtList) {
					String currentKurtosisNumeric = parseStatValue(kurt);
					if (currentKurtosisNumeric == null) {
						continue;
					}
					kurtosis[idx] += (Math.pow((Double.parseDouble(currentKurtosisNumeric)
							- Double.parseDouble(kurtAve)), 4)
							/ Math.pow(Double.parseDouble(kurtSigma), 4));
				}

				cntList[idx] = cnt;
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			}
		}

		for (int i = 0; i < kurtosis.length; i++) {
			int cnt = cntList[i];
			double kurt = kurtosis[i];
			double result;

			if (cnt >= 4 && Double.isFinite(kurt)) {
				double a = (cnt * (cnt + 1.0)) / ((cnt - 1.0) * (cnt - 2.0) * (cnt - 3.0));
				double b = (3.0 * Math.pow(cnt - 1.0, 2.0)) / ((cnt - 2.0) * (cnt - 3.0));
				result = a * kurt - b;
			} else if (cnt > 0 && Double.isFinite(kurt)) {
				double moment = kurt / cnt;
				result = moment - 3.0;
			} else {
				result = Double.NaN;
			}

			kurtosisList[i] = String.format("%.3f", result);
		}

		return kurtosisList;
	}

	/**
	 * 形式を揃える
	 */
	private void initFormat(BookDataEntity entity, String[] list, String listStr) {
		final String METHOD_NAME = "initFormat";
		final int FEATURE_START = 11;
		String feature_name = "";
		try {
			Field[] allFields = BookDataEntity.class.getDeclaredFields();
			for (int i = FEATURE_START; i < FEATURE_START + AverageStatisticsSituationConst.COUNTER; i++) {
				feature_name = allFields[i].getName();
				allFields[i].setAccessible(true);
				String feature_value = (String) allFields[i].get(entity);
				String format = getInitialValueByFormat(feature_value);
				if (listStr.contains("Min")) {
					format = format.replace("0.0", "10000.0");
					format = format.replace("0/0", "10000/10000");
				}
				list[i - FEATURE_START] = format;
			}
		} catch (Exception ex) {
			String messageCd = MessageCdConst.MCD00016E_FORMAT_ERROR;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, ex, feature_name);
		}
	}

	/**
	 * 共通割り算リスト
	 */
	private String[] commonDivision(String[] list, Integer[] cntList, String suffix) {
		for (int i = 0; i < this.bmM023M024M026InitBean.getEndInsertIdx()
				- this.bmM023M024M026InitBean.getStartInsertIdx(); i++) {

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
	 * insertStr の値を ScoreBasedFeatureStatsEntity に反映する
	 */
	private ScoreBasedFeatureStatsEntity setStatValuesToEntity(ScoreBasedFeatureStatsEntity entity, String insertStr, int ind) {
		final String METHOD_NAME = "setStatValuesToEntity";
		try {
			Field[] allFields = ScoreBasedFeatureStatsEntity.class.getDeclaredFields();
			Field field = allFields[ind];
			field.setAccessible(true);
			field.set(entity, insertStr);
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
			String fillChar = "ScoreBasedFeatureEntity への値設定エラー";
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			this.manageLoggerComponent.createSystemException(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, null);
		}
		return entity;
	}

	/**
	 * 残りの値をエンティティに格納する
	 */
	private ScoreBasedFeatureStatsEntity setOtherEntity(
			String score, String situation,
			String country, String league,
			Boolean updFlg, String id,
			ScoreBasedFeatureStatsEntity entity) {

		entity.setId(id);
		entity.setUpd(updFlg);
		entity.setScore(score);
		entity.setSituation(situation);
		entity.setCountry(country);
		entity.setLeague(league);
		return entity;
	}

	/**
	 * 埋め字設定
	 */
	private String setLoggerFillChar(String situation, String score, String country, String league) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("状況: ").append(situation).append(", ");
		stringBuilder.append("スコア: ").append(score).append(", ");
		stringBuilder.append("国: ").append(country).append(", ");
		stringBuilder.append("リーグ: ").append(league);
		return stringBuilder.toString();
	}

	/**
	 * ★ build（BM_M026と同様に StringJoiner 方式）
	 */
	private synchronized StatEncryptionEntity buildBmM30Form(
			final List<BookDataEntity> entities,
			String country,
			String league,
			String chkBody,
			Map<String, Function<BookDataEntity, String>> fieldMap) {

		final String METHOD_NAME = "buildBmM30Form";
		StatEncryptionEntity result = new StatEncryptionEntity();

		for (Map.Entry<String, Function<BookDataEntity, String>> entry : fieldMap.entrySet()) {
			String fieldName = entry.getKey();
			Function<BookDataEntity, String> getter = entry.getValue();

			// ★ joining をやめて逐次追加（巨大な中間Stringを作りにくくする）
			StringJoiner joiner = new StringJoiner(",");
			for (BookDataEntity e : entities) {
				String v;
				try {
					v = getter.apply(e);
				} catch (Exception ex) {
					v = "";
				}
				joiner.add(v == null ? "" : v);
			}

			try {
				Field field = StatEncryptionEntity.class.getDeclaredField(fieldName);
				field.setAccessible(true);
				field.set(result, joiner.toString());
			} catch (NoSuchFieldException | IllegalAccessException e) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fieldName);
			}
		}

		result.setCountry(country);
		result.setLeague(league);
		result.setChkBody(chkBody);
		return result;
	}

	/**
	 * ★ merge（BM_M026移植：暗号化対象領域 i>=9 をカンマ追記）
	 */
	private StatEncryptionEntity mergeStatEncryptionEntity(StatEncryptionEntity target, StatEncryptionEntity source) {
		final String METHOD_NAME = "mergeStatEncryptionEntity";
		Field[] fields = StatEncryptionEntity.class.getDeclaredFields();

		int i = 0;
		for (Field field : fields) {
			try {
				field.setAccessible(true);

				if (!field.getType().equals(String.class) || i < 9) {
					i++;
					continue;
				}

				String targetValue = (String) field.get(target);
				String sourceValue = (String) field.get(source);

				if (sourceValue == null || sourceValue.isEmpty()) {
					i++;
					continue;
				}

				if (targetValue == null || targetValue.isEmpty()) {
					field.set(target, sourceValue);
				} else {
					StringBuilder sb = new StringBuilder(targetValue.length() + 1 + sourceValue.length());
					sb.append(targetValue).append(',').append(sourceValue);
					field.set(target, sb.toString());
				}
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00018E_MERGE_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, field.getName());
			}
			i++;
		}
		return target;
	}

	/**
	 * 暗号化（★ id/updFlg も引き継ぐ）
	 */
	private StatEncryptionEntity encryption(StatEncryptionEntity entity) {
		final String METHOD_NAME = "encryption";
		StatEncryptionEntity encryptedEntity = new StatEncryptionEntity();
		encryptedEntity.setId(entity.getId());
		encryptedEntity.setUpdFlg(entity.isUpdFlg());

		try {
			int i = 0;
			Field[] fields = StatEncryptionEntity.class.getDeclaredFields();
			for (Field field : fields) {
				field.setAccessible(true);

				if (field.getType().equals(String.class)) {
					String originalValue = (String) field.get(entity);
					if (originalValue != null && !originalValue.isBlank() && i >= 9) {
						String encryptedValue = this.bmM030StatEncryptionBean.encrypto(originalValue);
						field.set(encryptedEntity, encryptedValue);
					} else {
						field.set(encryptedEntity, originalValue);
					}
				}
				i++;
			}
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00017E_ENCRYPTION_ERROR;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
					"StatEncryptionEntityの暗号化に失敗しました");
			this.manageLoggerComponent.createSystemException(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, null);
		}
		return encryptedEntity;
	}

	/**
	 * ★ lock（BM_M026移植）
	 */
	private Object getLock(String key) {
		return lockMap.computeIfAbsent(key, k -> new Object());
	}

	/**
	 * 歪度or尖度平均値計算のための加算処理
	 */
	private ScoreBasedFeatureOutputDTO setSkewnessOrKurtosisSumAve(String[] skewOrKurtList, Integer cnt) {
		ScoreBasedFeatureOutputDTO scoreBasedFeatureOutputDTO = new ScoreBasedFeatureOutputDTO();
		final String METHOD_NAME = "setSkewnessOrKurtosisSumAve";
		double sum = 0.0;

		for (int i = 0; i < skewOrKurtList.length; i++) {
			String currentValue = skewOrKurtList[i];
			String numericStr = parseStatValue(currentValue);
			if (numericStr == null || numericStr.isBlank() || isPercentAndFractionFormat(currentValue)) {
				continue;
			}

			try {
				double numeric = Double.parseDouble(numericStr);
				sum += numeric;
				cnt++;
			} catch (NumberFormatException e) {
				String messageCd = MessageCdConst.MCD00015E_NUMBERFORMAT_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, numericStr);
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, numericStr);
			}
		}

		scoreBasedFeatureOutputDTO.setCnt(String.valueOf(cnt));
		scoreBasedFeatureOutputDTO.setAve(String.valueOf(sum));
		return scoreBasedFeatureOutputDTO;
	}

	/**
	 * 歪度or尖度標準偏差導出のための加算処理
	 */
	private ScoreBasedFeatureOutputDTO setSkewnessOrKurtosisSumSigma(
			String[] skewOrKurtList, String skewOrKurtAve, Integer cnt) {

		ScoreBasedFeatureOutputDTO scoreBasedFeatureOutputDTO = new ScoreBasedFeatureOutputDTO();
		final String METHOD_NAME = "setSkewnessOrKurtosisSumSigma";
		double sum = 0.0;

		for (int i = 0; i < skewOrKurtList.length; i++) {
			String currentValue = skewOrKurtList[i];
			String numericStr = parseStatValue(currentValue);
			if (numericStr == null || numericStr.isBlank() || isPercentAndFractionFormat(currentValue)) {
				continue;
			}

			try {
				double numeric = Double.parseDouble(numericStr);
				double ave = Double.parseDouble(skewOrKurtAve);
				double diff2 = Math.pow((numeric - ave), 2);

				sum += diff2;
				cnt++;
			} catch (NumberFormatException e) {
				String messageCd = MessageCdConst.MCD00015E_NUMBERFORMAT_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, numericStr);
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, numericStr);
			}
		}

		scoreBasedFeatureOutputDTO.setCnt(String.valueOf(cnt));
		scoreBasedFeatureOutputDTO.setSigma(String.valueOf(sum));
		return scoreBasedFeatureOutputDTO;
	}
}
