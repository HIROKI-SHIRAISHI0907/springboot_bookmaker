package dev.application.analyze.bm_m023;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import dev.application.domain.repository.ScoreBasedFeatureStatsRepository;
import dev.application.domain.repository.StatEncryptionRepository;
import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.BookDataEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M023統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
public class ScoreBasedFeatureStat extends StatFormatResolver implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ScoreBasedFeatureStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ScoreBasedFeatureStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M023_SCORE_BASED_FEATURE";

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
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

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
				if (entityList == null || entityList.isEmpty())
					continue;
				// decideBasedMain を呼び出して集計マップを取得
				map = decideBasedMain(entityList, country, league, bmM30Map);
				if (map == null) {
					continue;
				}
				// 登録・更新
				ExecutorService executor = Executors.newFixedThreadPool(map.size());
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

		// 保存マップ登録
		for (Map.Entry<String, StatEncryptionEntity> entry : bmM30Map.entrySet()) {
			String[] split = entry.getKey().split("-");
			String home = split[0];
			String away = split[1];
			StatEncryptionEntity entrys = entry.getValue();
			entrys.setHome(home);
			entrys.setAway(away);
			StatEncryptionEntity newEntrys = encryption(entrys);
			int result = this.statEncryptionRepository.insert(newEntrys);
			if (result != 1) {
				String messageCd = "新規登録エラー";
				this.rootCauseWrapper.throwUnexpectedRowCount(
				        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
				        messageCd,
				        1, result,
				        null
				    );
			}
			String messageCd = "登録件数";
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, "BM_M030 登録件数: 1件");
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
	 * @return
	 */
	private ConcurrentHashMap<String, List<ScoreBasedFeatureStatsEntity>> decideBasedMain(List<BookDataEntity> entities,
			String country, String league, ConcurrentHashMap<String, StatEncryptionEntity> bmM30Map) {
		BookDataEntity returnMaxEntity = ExecuteMainUtil.getMaxSeqEntities(entities);
		if (!BookMakersCommonConst.FIN.equals(returnMaxEntity.getTime())) {
			return null;
		}
		String home = returnMaxEntity.getHomeTeamName();
		String away = returnMaxEntity.getAwayTeamName();

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
		ConcurrentHashMap<String, List<ScoreBasedFeatureStatsEntity>> allMap = new ConcurrentHashMap<>();
		for (String flg : flgs) {
			if (AverageStatisticsSituationConst.EACH_SCORE.equals(flg)) {
				if (!AverageStatisticsSituationConst.NOSCORE.equals(situation)) {
					for (String score : allScores) {
						if ("0-0".equals(score))
							continue; // 0-0 スコアはEACH_SCOREから除外
						CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
							basedEntities(allMap, entities, score, situation, flg, country, league, home, away,
									bmM30Map);
						}, executor);
						futures.add(future);
					}
				} else {
					if (!AverageStatisticsSituationConst.EACH_SCORE.equals(flg)) {
						CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
							basedEntities(allMap, entities, null, situation, flg, country, league, home, away,
									bmM30Map);
						}, executor);
						futures.add(future);
					}
				}
			} else {
				// ALL_DATA / FIRST_DATA / SECOND_DATA → スコア単位でなく全体処理なので null を渡す
				CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
					basedEntities(allMap, entities, null, situation, flg, country, league, home, away, bmM30Map);
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
	 * @param home ホーム
	 * @param away アウェー
	 * @param bmM30Map 保存データマップ
	 * @return
	 */
	private void basedEntities(ConcurrentHashMap<String, List<ScoreBasedFeatureStatsEntity>> insertMap,
			List<BookDataEntity> entities, String connectScore, String situation, String flg,
			String country, String league, String home, String away,
			ConcurrentHashMap<String, StatEncryptionEntity> bmM30Map) {
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

		String chkBody = "";

		// flgがALL,1st,2ndの時は,それをsituation扱いとする
		boolean updFlg = false;
		String id = null;
		List<ScoreBasedFeatureStatsEntity> statList = null;
		if (AverageStatisticsSituationConst.ALL_DATA.equals(flg) ||
				AverageStatisticsSituationConst.FIRST_DATA.equals(flg) ||
				AverageStatisticsSituationConst.SECOND_DATA.equals(flg)) {
			String score = flg;
			chkBody = flg;
			// 取得メソッド
			ScoreBasedFeatureOutputDTO dto = getData(score, situation, country, league);
			statList = dto.getList();
			updFlg = dto.isUpdFlg();
			id = dto.getId();
		} else {
			chkBody = connectScore;
			// 取得メソッド
			ScoreBasedFeatureOutputDTO dto = getData(connectScore, situation, country, league);
			statList = dto.getList();
			updFlg = dto.isUpdFlg();
			id = dto.getId();
		}

		Map<String, Function<BookDataEntity, String>> fieldMap = this.bmM030StatEncryptionBean.getFieldMap();
		// 保存データに保存
		String key = home + "-" + away + "-" + chkBody;
		final List<BookDataEntity> filteredFinalList = filteredList;
		final String chkFinalBody = chkBody;
		bmM30Map.computeIfAbsent(key, k -> {
			StatEncryptionEntity returnEntities = buildBmM30Form(filteredFinalList, country, league,
					chkFinalBody, fieldMap);
			return returnEntities;
		});

		StatEncryptionEntity decidedEntity = bmM30Map.get(key);

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
		// 形式設定
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
			sigmaList[i] = String.format("%.2f",
					Math.sqrt(Double.parseDouble(sigmaList[i])));
			tSigmaList[i] = String.format("%.2f",
					Math.sqrt(Double.parseDouble(tSigmaList[i].replace("'", ""))));
		}

		// 歪度,尖度導出
		// 歪度,尖度用の初期設定(StatEncryptionから取得)
		String[] aveSkewKurtList = this.bmM023M024M026InitBean.getAvgList().clone();
		String[] sigmaSkewKurtList = this.bmM023M024M026InitBean.getSigmaList().clone();
		String[] skewnessList = this.bmM023M024M026InitBean.getSkewnessList().clone();
		String[] kurtosisList = this.bmM023M024M026InitBean.getKurtosisList().clone();
		//String[] tSkewnessList = this.bmM023M024M026InitBean.getTimeSkewnessList().clone();
		//String[] tKurtosisList = this.bmM023M024M026InitBean.getTimeKurtosisList().clone();
		Integer[] kurtosisCntList = this.bmM023M024M026InitBean.getSkewnessCntList().clone();
		skewnessList = setSkewness(decidedEntity, skewnessList, aveSkewKurtList, sigmaSkewKurtList, kurtosisCntList);
		kurtosisList = setKurtosis(decidedEntity, kurtosisList, aveSkewKurtList, sigmaSkewKurtList, kurtosisCntList);

		ScoreBasedFeatureStatsEntity entity = new ScoreBasedFeatureStatsEntity();
		// 文字連結
		StringBuilder stringBuilder = new StringBuilder();
		for (int i = this.bmM023M024M026InitBean.getStartInsertIdx(); i <= this.bmM023M024M026InitBean
				.getEndInsertIdx(); i++) {
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
					.append(tSigmaCntList[idx]).append(",")
					.append(skewness).append(",")
					.append(kurtosis);
			entity = setStatValuesToEntity(entity, stringBuilder.toString(), i);
			stringBuilder.setLength(0);
		}
		// その他情報を格納する
		if (AverageStatisticsSituationConst.ALL_DATA.equals(flg) ||
				AverageStatisticsSituationConst.FIRST_DATA.equals(flg) ||
				AverageStatisticsSituationConst.SECOND_DATA.equals(flg)) {
			String score = flg;
			entity = setOtherEntity(score, situation, country, league, updFlg, id, entity);
		} else {
			entity = setOtherEntity(connectScore, situation, country, league, updFlg, id, entity);
		}
		// スレッドセーフな格納
		insertMap.computeIfAbsent(flg, k -> new ArrayList<ScoreBasedFeatureStatsEntity>())
				.add(entity);
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
			this.rootCauseWrapper.throwUnexpectedRowCount(
			        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
			        messageCd,
			        1, result,
			        null
			    );
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
		int result = this.scoreBasedFeatureStatsRepository.updateStatValues(entity);
		if (result != 1) {
			String messageCd = "更新エラー";
			this.rootCauseWrapper.throwUnexpectedRowCount(
			        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
			        messageCd,
			        1, result,
			        String.format("id=%s, count=%s, remarks=%s", entity.getId(), null, null)
			    );
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
	 * @param skewnessList
	 * @param kurtosisList
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
			for (int i = this.bmM023M024M026InitBean.getStartInsertIdx(); i <= this.bmM023M024M026InitBean
					.getEndInsertIdx(); i++) {
				int idx = i - this.bmM023M024M026InitBean.getStartInsertIdx();
				Field field = fields[i];
				field.setAccessible(true);
				try {
					String statValue = (String) field.get(statEntity); // 例: "12.5, 33.8, 25.1, 5.3"
					if (statValue == null || statValue.isBlank())
						continue;
					String[] values = statValue.split(",");
					if (values.length >= 16) {
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
		// BookDataEntityの全フィールドを取得
		Field[] allFields = BookDataEntity.class.getDeclaredFields();
		String fillChar = "";
		for (int i = this.bmM023M024M026InitBean.getStartIdx(); i <= this.bmM023M024M026InitBean.getEndIdx(); i++) {
			int idx = i - this.bmM023M024M026InitBean.getStartIdx();
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
		for (int i = this.bmM023M024M026InitBean.getStartInsertIdx(); i <= this.bmM023M024M026InitBean
				.getEndInsertIdx(); i++) {
			int idx = i - this.bmM023M024M026InitBean.getStartInsertIdx();
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
		// BookDataEntityの全フィールドを取得
		Field[] allFields = BookDataEntity.class.getDeclaredFields();
		String fillChar = "";
		for (int i = this.bmM023M024M026InitBean.getStartIdx(); i <= this.bmM023M024M026InitBean.getEndIdx(); i++) {
			int idx = i - this.bmM023M024M026InitBean.getStartIdx();
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
		for (int i = this.bmM023M024M026InitBean.getStartInsertIdx(); i <= this.bmM023M024M026InitBean
				.getEndInsertIdx(); i++) {
			int idx = i - this.bmM023M024M026InitBean.getStartInsertIdx();
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
		final String METHOD_NAME = "setSumAve";
		Field[] allFields = BookDataEntity.class.getDeclaredFields();
		String fillChar = "";
		for (int i = this.bmM023M024M026InitBean.getStartIdx(); i <= this.bmM023M024M026InitBean.getEndIdx(); i++) {
			int idx = i - this.bmM023M024M026InitBean.getStartIdx();
			Field field = allFields[i];
			field.setAccessible(true);
			fillChar = "フィールド名: " + field.getName() + ", 連番No: " + filter.getSeq();
			try {
				String currentValue = (String) field.get(filter);
				if (currentValue == null || currentValue.isBlank())
					continue;

				// 数値化（成功数・もしくは%・通常値）XX% (XX/XX)の形式は分子の数を足す
				String numericStr = parseStatValue(currentValue);
				if (numericStr == null || numericStr.isBlank())
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
		for (int i = this.bmM023M024M026InitBean.getStartInsertIdx(); i <= this.bmM023M024M026InitBean
				.getEndInsertIdx(); i++) {
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
				// 数値変換失敗時はスキップ（加算しない）
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						"NumberFormatException", e, fillChar);
			} catch (Exception e) {
				String messageCd = "リフレクションエラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
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
		for (int i = this.bmM023M024M026InitBean.getStartIdx(); i <= this.bmM023M024M026InitBean.getEndIdx(); i++) {
			int idx = i - this.bmM023M024M026InitBean.getStartIdx();
			Field field = allFields[i];
			field.setAccessible(true);
			fillChar = "フィールド名: " + field.getName() + ", 連番No: " + filter.getSeq();
			try {
				String currentValue = (String) field.get(filter);
				String avgStr = avgList[idx];
				// スキップ条件：空 or null or X% (X/X) 形式
				if (currentValue == null || currentValue.isBlank()) {
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

			for (int i = this.bmM023M024M026InitBean.getStartInsertIdx(); i <= this.bmM023M024M026InitBean
					.getEndInsertIdx(); i++) {
				int idx = i - this.bmM023M024M026InitBean.getStartInsertIdx();
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
		}
		return sigmaList;
	}

	/**
	 * 歪度設定(n/(n-1)(n-2)*sum(i=1->n((xi - xver) / sigma)^3)
	 * @param entity
	 * @param skewnessList
	 * @param aveList
	 * @param sigmaList
	 * @param cntList
	 * @param ha homeaway
	 */
	private String[] setSkewness(StatEncryptionEntity entity,
			String[] skewnessList, String[] aveList,
			String[] sigmaList, Integer[] cntList) {
		final String METHOD_NAME = "setSkewness";
		Double[] skewness = new Double[AverageStatisticsSituationConst.COUNTER];
		for (int i = 0; i < skewness.length; i++) {
			skewness[i] = 0.0;
		}
		// StatEncryptionEntityの全フィールドを取得
		Field[] allFields = StatEncryptionEntity.class.getDeclaredFields();
		String fillChar = "";
		for (int i = this.bmM030StatEncryptionBean.getStartEncryptionIdx(); i <= this.bmM030StatEncryptionBean
				.getEndEncryptionIdx(); i++) {
			int idx = i - this.bmM030StatEncryptionBean.getStartEncryptionIdx();
			Field field = allFields[i];
			field.setAccessible(true);
			fillChar = "フィールド名: " + field.getName();
			try {
				String currentValue = (String) field.get(entity);

				if (currentValue == null || currentValue.isBlank() || isPercentAndFractionFormat(currentValue))
					continue;

				// カンマ分割
				String[] skewList = currentValue.split(",");
				// 平均値,標準偏差導出
				int cnt = 0;
				ScoreBasedFeatureOutputDTO dto1 = setSkewnessOrKurtosisSumAve(skewList, cnt);
				String skewSumAve = dto1.getAve();
				cnt = Integer.parseInt(dto1.getCnt());
				String skewAve = (cnt == 0) ? ""
						: String.valueOf(
								Double.parseDouble(skewSumAve) / cnt);
				cnt = 0;
				ScoreBasedFeatureOutputDTO dto2 = setSkewnessOrKurtosisSumSigma(skewList, skewAve, cnt);
				String skewSumSigma = dto2.getSigma();
				cnt = Integer.parseInt(dto2.getCnt());
				// 不偏分散のルート
				String skewSigma = (cnt == 1) ? ""
						: String.valueOf(
								Math.sqrt(Double.parseDouble(skewSumSigma) / (cnt - 1)));
				System.out.println("skewAve, skewSigma, cnt: " + skewAve + ", " + skewSigma + ", " + cnt);
				// 導出できなければskip
				if ("".equals(skewAve) || "".equals(skewSigma))
					continue;

				// 現在の歪度情報を加算
				for (String skew : skewList) {
					String currentSkewnessNumeric = parseStatValue(skew);
					if (currentSkewnessNumeric == null)
						continue;
					skewness[idx] += Math.pow((Double.parseDouble(currentSkewnessNumeric)
							- Double.parseDouble(skewAve)) / Double.parseDouble(skewSigma), 3);
				}
				System.out.println("setSkewness, fillChar: " + fillChar + ", currentValue: " + currentValue
						+ ", skewness[idx]: " + skewness[idx]);
				// 件数カウント
				cntList[idx] = cnt;
			} catch (Exception e) {
				String messageCd = "リフレクションエラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			}
		}
		// n/(n-1)(n-2)との積をとる
		for (int i = 0; i < skewness.length; i++) {
			int cnt = cntList[i];
			double skew = skewness[i];
			double result = (cnt / ((cnt - 1.0) * (cnt - 2.0))) * skew;
			skewnessList[i] = String.format("%.3f", result);
			System.out
					.println("setSkewness/division, skewness[idx]: " + skew + ", cnt: " + cnt + ", result: " + result);
		}
		return skewnessList;
	}

	/**
	 * 尖度設定((n(n+1))/(n-1)(n-2)(n-3)*sum(i=1->n((xi - xver)^4 / sigma^3 - 3*(n-1)^2 / (n-2)(n-3))
	 * @param entity
	 * @param kurtosisList
	 * @param aveList
	 * @param sigmaList
	 * @param cntList(歪度で求めた件数を設定)
	 */
	private String[] setKurtosis(StatEncryptionEntity entity,
			String[] kurtosisList, String[] aveList,
			String[] sigmaList, Integer[] cntList) {
		final String METHOD_NAME = "setKurtosis";
		Double[] kurtosis = new Double[AverageStatisticsSituationConst.COUNTER];
		for (int i = 0; i < kurtosis.length; i++) {
			kurtosis[i] = 0.0;
		}
		// StatEncryptionEntityの全フィールドを取得
		Field[] allFields = StatEncryptionEntity.class.getDeclaredFields();
		String fillChar = "";
		for (int i = this.bmM030StatEncryptionBean.getStartEncryptionIdx(); i <= this.bmM030StatEncryptionBean
				.getEndEncryptionIdx(); i++) {
			int idx = i - this.bmM030StatEncryptionBean.getStartEncryptionIdx();
			Field field = allFields[i];
			field.setAccessible(true);
			fillChar = "フィールド名: " + field.getName();
			try {
				String currentValue = (String) field.get(entity);
				if (currentValue == null || currentValue.isBlank() || isPercentAndFractionFormat(currentValue))
					continue;

				// カンマ分割
				String[] kurtList = currentValue.split(",");
				// 平均値,標準偏差導出
				int cnt = 0;
				ScoreBasedFeatureOutputDTO dto1 = setSkewnessOrKurtosisSumAve(kurtList, cnt);
				String kurtSumAve = dto1.getAve();
				cnt = Integer.parseInt(dto1.getCnt());
				String kurtAve = (cnt == 0) ? ""
						: String.valueOf(
								Double.parseDouble(kurtSumAve) / cnt);
				cnt = 0;
				ScoreBasedFeatureOutputDTO dto2 = setSkewnessOrKurtosisSumSigma(kurtList, kurtAve, cnt);
				String kurtSumSigma = dto2.getSigma();
				cnt = Integer.parseInt(dto2.getCnt());
				// 不偏分散のルート
				String kurtSigma = (cnt == 1) ? ""
						: String.valueOf(
								Math.sqrt(Double.parseDouble(kurtSumSigma) / (cnt - 1)));
				// 導出できなければskip
				if ("".equals(kurtAve) || "".equals(kurtSigma))
					continue;

				// 現在の尖度情報を加算
				for (String kurt : kurtList) {
					String currentKurtosisNumeric = parseStatValue(kurt);
					if (currentKurtosisNumeric == null)
						continue;
					kurtosis[idx] += (Math.pow((Double.parseDouble(currentKurtosisNumeric)
							- Double.parseDouble(kurtAve)), 4) / Math.pow(
									Double.parseDouble(kurtSigma), 4));
				}
				System.out.println("setKurtosis, fillChar: " + fillChar + ", currentValue: " + currentValue
						+ ", kurtosis[idx]: " + kurtosis[idx]);
				// 件数カウント
				cntList[idx] = cnt;
			} catch (Exception e) {
				String messageCd = "リフレクションエラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			}
		}
		// (n(n+1))/(n-1)(n-2)(n-3)との積をとり,3(n-1)^2 / (n-2)(n-3)を引く
		for (int i = 0; i < kurtosis.length; i++) {
			int cnt = cntList[i];
			double kurt = kurtosis[i];
			double result;
			if (cnt >= 4 && Double.isFinite(kurt)) {
				double a = (cnt * (cnt + 1.0)) / ((cnt - 1.0) * (cnt - 2.0) * (cnt - 3.0));
				double b = (3.0 * Math.pow(cnt - 1.0, 2.0)) / ((cnt - 2.0) * (cnt - 3.0)); // ← 括弧で積に！
				result = a * kurt - b; // ← 補正項は “1回だけ” 引く
			} else if (cnt > 0 && Double.isFinite(kurt)) {
				// n<4 は補正式未定義。フォールバック（例：過剰尖度 = moment - 3）
				double moment = kurt / cnt; // 標準化4次モーメント
				result = moment - 3.0; // 過剰尖度が欲しい場合
			} else {
				result = Double.NaN; // cnt==0 や std==0 など
			}
			kurtosisList[i] = String.format("%.3f", result);
			System.out
					.println("setKurtosis/division, kurtosis[idx]: " + kurt + ", cnt: " + cnt + ", result: " + result);
		}
		return kurtosisList;
	}

	/**
	 * 形式を揃える
	 * @param entity BookDataEntity
	 * @param list
	 */
	private void initFormat(BookDataEntity entity,
			String[] list, String listStr) {
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
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					"形式更新中に例外発生", ex, feature_name);
		}
	}

	/**
	 * 共通割り算リスト
	 * @param list
	 * @param cntList
	 * @param suffix
	 * @return
	 */
	private String[] commonDivision(String[] list, Integer[] cntList, String suffix) {
		// 平均導出
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
	 * @param id ID
	 * @return
	 */
	private ScoreBasedFeatureStatsEntity setOtherEntity(String score, String situation,
			String country, String league, Boolean updFlg, String id, ScoreBasedFeatureStatsEntity entity) {
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

	/**
	 * ビルドメソッド
	 * @param entities
	 * @param country
	 * @param league
	 * @param chkBody
	 * @param fieldMap
	 * @return
	 */
	private synchronized StatEncryptionEntity buildBmM30Form(final List<BookDataEntity> entities,
			String country, String league, String chkBody,
			Map<String, Function<BookDataEntity, String>> fieldMap) {

		StatEncryptionEntity result = new StatEncryptionEntity();
		for (Map.Entry<String, Function<BookDataEntity, String>> entry : fieldMap.entrySet()) {
			String fieldName = entry.getKey();
			Function<BookDataEntity, String> getter = entry.getValue();
			// BookDataEntityリストから値を抽出してカンマ区切り文字列を作成
			String joinedValue = entities.stream()
					.map(e -> {
						try {
							return getter.apply(e);
						} catch (Exception ex) {
							System.err.println("getter失敗: " + fieldName);
							return "";
						}
					})
					.collect(Collectors.joining(","));
			// StatEncryptionEntityのフィールドにリフレクションでセット
			try {
				Field field = StatEncryptionEntity.class.getDeclaredField(fieldName);
				field.setAccessible(true);
				field.set(result, joinedValue);
			} catch (NoSuchFieldException | IllegalAccessException e) {
				System.err.println("フィールド代入失敗: " + fieldName);
				e.printStackTrace();
			}
		}
		// setterでセット
		System.out.println("country, league, chkBody: " + country + "," + league + "," + chkBody);
		result.setCountry(country);
		result.setLeague(league);
		result.setChkBody(chkBody);
		return result;
	}

	/**
	 * 暗号化
	 * @param entity
	 * @return
	 */
	private StatEncryptionEntity encryption(StatEncryptionEntity entity) {
		final String METHOD_NAME = "encryption";
		StatEncryptionEntity encryptedEntity = new StatEncryptionEntity();
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
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					null,
					e,
					"StatEncryptionEntityの暗号化に失敗しました");
			this.manageLoggerComponent.createSystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					null,
					null);
		}
		return encryptedEntity;
	}

	/**
	 * 歪度or尖度平均値計算のための加算処理（値を加算し、件数もインクリメント）
	 * @param skewOrKurtList 加算リスト（String型）
	 * @param cnt 件数カウント（Integer型）
	 * @return 加算後のaveList
	 */
	private ScoreBasedFeatureOutputDTO setSkewnessOrKurtosisSumAve(String[] skewOrKurtList, Integer cnt) {
		ScoreBasedFeatureOutputDTO scoreBasedFeatureOutputDTO = new ScoreBasedFeatureOutputDTO();
		final String METHOD_NAME = "setSkewnessOrKurtosisSumAve";
		double sum = 0.0;
		for (int i = 0; i < skewOrKurtList.length; i++) {
			String currentValue = skewOrKurtList[i];
			// 数値化（成功数・もしくは%・通常値）XX% (XX/XX)の形式は分子の数を足す
			String numericStr = parseStatValue(currentValue);
			if (numericStr == null || numericStr.isBlank() || isPercentAndFractionFormat(currentValue))
				continue;

			try {
				// 文字列 → double → 加算
				double numeric = Double.parseDouble(numericStr);
				sum += numeric;
				// 件数カウント
				cnt++;
			} catch (NumberFormatException e) {
				// 数値変換失敗時はスキップ（加算しない）
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						null, e);
			} catch (Exception e) {
				// リフレクション等の例外
				String messageCd = "リフレクションエラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e);
				this.manageLoggerComponent.createSystemException(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e);
			}
		}
		String skewOrKurtSumAve = String.valueOf(sum);
		scoreBasedFeatureOutputDTO.setCnt(String.valueOf(cnt));
		scoreBasedFeatureOutputDTO.setAve(skewOrKurtSumAve);
		return scoreBasedFeatureOutputDTO;
	}

	/**
	 * 歪度or尖度標準偏差導出のための加算処理（値を加算し、件数もインクリメント）
	 * @param skewOrKurtList 加算リスト（String型）
	 * @param skewOrKurtAve
	 * @param cnt 件数カウント（Integer型）
	 * @return 加算後のaveList
	 */
	private ScoreBasedFeatureOutputDTO setSkewnessOrKurtosisSumSigma(String[] skewOrKurtList, String skewOrKurtAve,
			Integer cnt) {
		ScoreBasedFeatureOutputDTO scoreBasedFeatureOutputDTO = new ScoreBasedFeatureOutputDTO();
		final String METHOD_NAME = "setSkewnessOrKurtosisSumSigma";
		double sum = 0.0;
		for (int i = 0; i < skewOrKurtList.length; i++) {
			String currentValue = skewOrKurtList[i];
			// 数値化（成功数・もしくは%・通常値）XX% (XX/XX)の形式は分子の数を足す
			String numericStr = parseStatValue(currentValue);
			if (numericStr == null || numericStr.isBlank() || isPercentAndFractionFormat(currentValue))
				continue;

			try {
				// 文字列 → double → 加算
				double numeric = Double.parseDouble(numericStr);
				double ave = Double.parseDouble(skewOrKurtAve);
				numeric = Math.pow((numeric - ave), 2);
				sum += numeric;
				// 件数カウント
				cnt++;
			} catch (NumberFormatException e) {
				// 数値変換失敗時はスキップ（加算しない）
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						null, e);
			} catch (Exception e) {
				// リフレクション等の例外
				String messageCd = "リフレクションエラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e);
				this.manageLoggerComponent.createSystemException(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e);
			}
		}
		String skewOrKurtSumSigma = String.valueOf(sum);
		scoreBasedFeatureOutputDTO.setCnt(String.valueOf(cnt));
		scoreBasedFeatureOutputDTO.setSigma(skewOrKurtSumSigma);
		return scoreBasedFeatureOutputDTO;
	}
}
