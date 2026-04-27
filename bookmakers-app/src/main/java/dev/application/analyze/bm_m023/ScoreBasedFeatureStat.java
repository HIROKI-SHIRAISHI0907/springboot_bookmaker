package dev.application.analyze.bm_m023;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

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
 * BM_M023統計分析ロジック（手動データ投入の場合は適用対象外）
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

	/** 暗号アルゴリズム */
	private static final String AES = "AES";

	/** 暗号アルゴリズム */
	private static final String AES_CBC_PKCS5Padding = "AES/CBC/PKCS5Padding";

	/** 固定IV（BmM030StatEncryptionBean と同じ仕様） */
	private static final byte[] FIXED_IV = new byte[16];

	/** ロック */
	private final ConcurrentHashMap<String, Object> lockMap = new ConcurrentHashMap<>();

	/** Beanクラス */
	@Autowired
	private BmM023M024M026InitBean bmM023M024M026InitBean;

	/** Beanクラス */
	@Autowired
	private BmM030StatEncryptionBean bmM030StatEncryptionBean;

	/** ScoreBasedFeatureStatsRepository */
	@Autowired
	private ScoreBasedFeatureStatsRepository scoreBasedFeatureStatsRepository;

	/** StatEncryptionRepository */
	@Autowired
	private StatEncryptionRepository statEncryptionRepository;

	/** ログ管理ラッパー */
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

		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		try {
			// 実行単位の一時Map（Beanスコープに持たない）
			ConcurrentHashMap<String, StatEncryptionEntity> bmM30Map = new ConcurrentHashMap<>();

			for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {

				String[] dataCategory = safeLeague(entry.getKey());
				String country = dataCategory[0];
				String league = dataCategory[1];

				if (country.isBlank() || league.isBlank()) {
					manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							"skip: invalid league key", null, "key=" + entry.getKey());
					continue;
				}

				Map<String, List<BookDataEntity>> entrySub = entry.getValue();
				if (entrySub == null || entrySub.isEmpty()) {
					continue;
				}

				for (List<BookDataEntity> entityList : entrySub.values()) {
					if (entityList == null || entityList.isEmpty()) {
						continue;
					}

					List<ScoreBasedFeatureStatsEntity> stats =
							decideBasedMain(entityList, country, league, bmM30Map);

					if (stats == null || stats.isEmpty()) {
						continue;
					}

					// メモリを増やさないよう逐次保存
					for (ScoreBasedFeatureStatsEntity stat : stats) {
						if (stat.isUpd()) {
							update(stat);
						} else {
							insert(stat);
						}
					}
				}
			}

			// BM_M030保存
			saveStatEncryptionEntities(bmM30Map);

			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		} finally {
			this.manageLoggerComponent.clear();
		}
	}

	/**
	 * 処理メインロジック
	 */
	private List<ScoreBasedFeatureStatsEntity> decideBasedMain(
			List<BookDataEntity> entities,
			String country,
			String league,
			ConcurrentHashMap<String, StatEncryptionEntity> bmM30Map) throws Exception {

		BookDataEntity returnMaxEntity = ExecuteMainUtil.getMaxSeqEntities(entities);
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, null, null, returnMaxEntity.getFilePath());

		if (!BookMakersCommonConst.FIN.equals(returnMaxEntity.getTime())) {
			return List.of();
		}

		String home = returnMaxEntity.getHomeTeamName();
		String away = returnMaxEntity.getAwayTeamName();

		String situation = (Integer.parseInt(returnMaxEntity.getHomeScore()) == 0
				&& Integer.parseInt(returnMaxEntity.getAwayScore()) == 0)
						? AverageStatisticsSituationConst.NOSCORE
						: AverageStatisticsSituationConst.SCORE;

		List<ScoreBasedFeatureStatsEntity> results = new ArrayList<>();

		List<String> flgs = List.of(
				AverageStatisticsSituationConst.ALL_DATA,
				AverageStatisticsSituationConst.FIRST_DATA,
				AverageStatisticsSituationConst.SECOND_DATA,
				AverageStatisticsSituationConst.EACH_SCORE);

		List<String> allScores = extractExistingScorePatterns(entities);

		for (String flg : flgs) {
			if (AverageStatisticsSituationConst.EACH_SCORE.equals(flg)) {
				if (!AverageStatisticsSituationConst.NOSCORE.equals(situation)) {
					for (String score : allScores) {
						if ("0-0".equals(score)) {
							continue;
						}
						ScoreBasedFeatureStatsEntity stat = basedEntities(
								entities, score, situation, flg, country, league, home, away, bmM30Map);
						if (stat != null) {
							results.add(stat);
						}
					}
				}
			} else {
				ScoreBasedFeatureStatsEntity stat = basedEntities(
						entities, null, situation, flg, country, league, home, away, bmM30Map);
				if (stat != null) {
					results.add(stat);
				}
			}
		}

		return results;
	}

	/**
	 * 基準エンティティ指定
	 */
	private ScoreBasedFeatureStatsEntity basedEntities(
			List<BookDataEntity> entities,
			String connectScore,
			String situation,
			String flg,
			String country,
			String league,
			String home,
			String away,
			ConcurrentHashMap<String, StatEncryptionEntity> bmM30Map) throws Exception {

		final String METHOD_NAME = "basedEntities";

		List<BookDataEntity> filteredList = null;

		if (AverageStatisticsSituationConst.EACH_SCORE.equals(flg)) {
			filteredList = entities.stream()
					.filter(entity -> connectScore.equals(entity.getHomeScore() + "-" + entity.getAwayScore()))
					.collect(Collectors.toList());
		} else if (AverageStatisticsSituationConst.ALL_DATA.equals(flg)) {
			filteredList = entities;
		} else {
			BookDataEntity half = ExecuteMainUtil.getHalfEntities(entities);
			if (half == null || half.getSeq() == null) {
				manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, null,
						"half not found -> skip FIRST/SECOND. file=" + entities.get(0).getFilePath()
								+ ", size=" + entities.size()
								+ ", country=" + country + ", league=" + league
								+ ", home=" + home + ", away=" + away);
				return null;
			}

			String halfTimeSeq = half.getSeq();
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
			return null;
		}

		String chkBody;
		boolean updFlg;
		String id;
		List<ScoreBasedFeatureStatsEntity> statList;

		if (AverageStatisticsSituationConst.ALL_DATA.equals(flg)
				|| AverageStatisticsSituationConst.FIRST_DATA.equals(flg)
				|| AverageStatisticsSituationConst.SECOND_DATA.equals(flg)) {

			chkBody = flg;
			ScoreBasedFeatureOutputDTO dto = getData(flg, situation, country, league);
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
		String key = buildMatchKey(country, league, home, away, chkBody);

		StatEncryptionEntity decidedEntity;
		synchronized (getLock(key)) {
			StatEncryptionEntity exist = bmM30Map.get(key);

			// 実行中Mapになければ、そのキー1件だけDBから読む
			if (exist == null) {
				exist = findAndDecryptExistingStatEncryption(country, league, home, away, chkBody);
			}

			if (exist != null) {
				StatEncryptionEntity addPart = buildBmM30Form(
						filteredList, country, league, home, away, chkBody, fieldMap);

				StatEncryptionEntity merged = mergeStatEncryptionEntity(exist, addPart, fieldMap.keySet());
				merged.setId(exist.getId());
				merged.setUpdFlg(true);
				bmM30Map.put(key, merged);
			} else {
				StatEncryptionEntity fresh = buildBmM30Form(
						filteredList, country, league, home, away, chkBody, fieldMap);
				fresh.setId(null);
				fresh.setUpdFlg(false);
				bmM30Map.put(key, fresh);
			}

			decidedEntity = bmM30Map.get(key);
		}

		if (decidedEntity == null) {
			return null;
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

		setInitData(minList, minCntList, maxList, maxCntList, aveList, aveCntList, sigmaList, sigmaCntList,
				tMinList, tMinCntList, tMaxList, tMaxCntList,
				tAveList, tAveCntList, tSigmaList, tSigmaCntList,
				statList);

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

		aveList = commonDivision(aveList, aveCntList, "");
		tAveList = commonDivision(tAveList, tAveCntList, "'");

		for (BookDataEntity filter : filteredList) {
			sigmaList = setSumSigma(filter, aveList, sigmaList, sigmaCntList);
			tSigmaList = setTimeSumSigma(filter, tAveList, tSigmaList, tSigmaCntList);
		}

		sigmaList = commonDivision(sigmaList, sigmaCntList, "");
		tSigmaList = commonDivision(tSigmaList, tSigmaCntList, "'");

		for (int i = 0; i < sigmaList.length; i++) {
			double sigma = safeParseDouble(sigmaList[i], 0.0);
			double tSigma = safeParseDouble(removeQuote(tSigmaList[i]), 0.0);
			sigmaList[i] = String.format("%.2f", Math.sqrt(sigma));
			tSigmaList[i] = String.format("%.2f", Math.sqrt(tSigma));
		}

		String[] aveSkewKurtList = this.bmM023M024M026InitBean.getAvgList().clone();
		String[] sigmaSkewKurtList = this.bmM023M024M026InitBean.getSigmaList().clone();
		String[] skewnessList = this.bmM023M024M026InitBean.getSkewnessList().clone();
		String[] kurtosisList = this.bmM023M024M026InitBean.getKurtosisList().clone();
		Integer[] kurtosisCntList = this.bmM023M024M026InitBean.getSkewnessCntList().clone();

		skewnessList = setSkewness(decidedEntity, skewnessList, aveSkewKurtList, sigmaSkewKurtList, kurtosisCntList);
		kurtosisList = setKurtosis(decidedEntity, kurtosisList, aveSkewKurtList, sigmaSkewKurtList, kurtosisCntList);

		ScoreBasedFeatureStatsEntity entity = new ScoreBasedFeatureStatsEntity();
		StringBuilder stringBuilder = new StringBuilder();

		for (int i = this.bmM023M024M026InitBean.getStartInsertIdx();
				i <= this.bmM023M024M026InitBean.getEndInsertIdx();
				i++) {

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

		if (AverageStatisticsSituationConst.ALL_DATA.equals(flg)
				|| AverageStatisticsSituationConst.FIRST_DATA.equals(flg)
				|| AverageStatisticsSituationConst.SECOND_DATA.equals(flg)) {
			entity = setOtherEntity(flg, situation, country, league, updFlg, id, entity);
		} else {
			entity = setOtherEntity(connectScore, situation, country, league, updFlg, id, entity);
		}

		return entity;
	}

	/**
	 * 既存score_based_feature_stats取得
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
			dto.setList(new ArrayList<>());
		}
		return dto;
	}

	/**
	 * BM_M030保存
	 */
	private void saveStatEncryptionEntities(
			ConcurrentHashMap<String, StatEncryptionEntity> bmM30Map) throws Exception {

		final String METHOD_NAME = "saveStatEncryptionEntities";

		for (StatEncryptionEntity entity : bmM30Map.values()) {
			if (entity == null) {
				continue;
			}

			StatEncryptionEntity encrypted = encryptStatEncryptionEntity(entity);

			int result;
			if (entity.isUpdFlg()) {
				result = this.statEncryptionRepository.updateEncValues(encrypted);
				if (result != 1) {
					String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
					this.rootCauseWrapper.throwUnexpectedRowCount(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							messageCd,
							1, result,
							null);
				}

				String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
						BM_NUMBER + " 更新件数: " + result + "件 (BM_M030)");
			} else {
				result = this.statEncryptionRepository.insert(encrypted);
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
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
						BM_NUMBER + " 登録件数: " + result + "件 (BM_M030)");
			}
		}
	}

	/**
	 * 登録
	 */
	private synchronized void insert(ScoreBasedFeatureStatsEntity entity) {
		final String METHOD_NAME = "insert";
		String fillChar = setLoggerFillChar(
				entity.getSituation(), entity.getScore(), entity.getCountry(), entity.getLeague());

		int result = this.scoreBasedFeatureStatsRepository.insert(entity);
		if (result != 1) {
			String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, fillChar);
			this.manageLoggerComponent.createSystemException(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, null);
		}

		String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 登録件数: " + result + "件 (" + fillChar + ")");
	}

	/**
	 * 更新
	 */
	private void update(ScoreBasedFeatureStatsEntity entity) {
		final String METHOD_NAME = "update";
		String fillChar = setLoggerFillChar(
				entity.getSituation(), entity.getScore(), entity.getCountry(), entity.getLeague());

		int result = this.scoreBasedFeatureStatsRepository.updateStatValues(entity);
		if (result != 1) {
			String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, fillChar);
			this.manageLoggerComponent.createSystemException(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, null);
		}

		String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 更新件数: " + result + "件 (" + fillChar + ")");
	}

	/**
	 * 初期値設定
	 */
	private void setInitData(String[] minList, Integer[] minCntList, String[] maxList, Integer[] maxCntList,
			String[] aveList, Integer[] aveCntList, String[] sigmaList, Integer[] sigmaCntList,
			String[] tMinList, Integer[] tMinCntList, String[] tMaxList, Integer[] tMaxCntList,
			String[] tAveList, Integer[] tAveCntList, String[] tSigmaList, Integer[] tSigmaCntList,
			List<ScoreBasedFeatureStatsEntity> list) {

		final String METHOD_NAME = "setInitData";

		if (list != null && !list.isEmpty()) {
			ScoreBasedFeatureStatsEntity statEntity = list.get(0);
			Field[] fields = ScoreBasedFeatureStatsEntity.class.getDeclaredFields();

			for (int i = this.bmM023M024M026InitBean.getStartInsertIdx();
					i <= this.bmM023M024M026InitBean.getEndInsertIdx();
					i++) {

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
				i <= this.bmM023M024M026InitBean.getEndIdx();
				i++) {

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
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
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
				i <= this.bmM023M024M026InitBean.getEndInsertIdx();
				i++) {

			int idx = i - this.bmM023M024M026InitBean.getStartInsertIdx();
			fillChar = "連番No: " + filter.getSeq();

			try {
				String minTimeValue = minList[idx];
				double minTimeTmpsValue = Double.parseDouble(removeQuote(minTimeValue));
				double currentTimeValue = ExecuteMainUtil.convertToMinutes(filter.getTime());

				if (currentTimeValue < minTimeTmpsValue) {
					minList[idx] = String.valueOf(currentTimeValue) + "'";
				}
				cntList[idx]++;
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
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
				i <= this.bmM023M024M026InitBean.getEndIdx();
				i++) {

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
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
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
				i <= this.bmM023M024M026InitBean.getEndInsertIdx();
				i++) {

			int idx = i - this.bmM023M024M026InitBean.getStartInsertIdx();
			fillChar = "連番No: " + filter.getSeq();

			try {
				String maxTimeValue = maxList[idx];
				double maxTimeTmpsValue = Double.parseDouble(removeQuote(maxTimeValue));
				double currentTimeValue = ExecuteMainUtil.convertToMinutes(filter.getTime());

				if (currentTimeValue > maxTimeTmpsValue) {
					maxList[idx] = String.valueOf(currentTimeValue) + "'";
				}
				cntList[idx]++;
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
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
				i <= this.bmM023M024M026InitBean.getEndIdx();
				i++) {

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
				double prev = (aveList[idx] != null && !aveList[idx].isBlank())
						? Double.parseDouble(aveList[idx]) : 0.0;

				aveList[idx] = String.valueOf(prev + numeric);
				cntList[idx]++;
			} catch (NumberFormatException e) {
				String messageCd = MessageCdConst.MCD00015E_NUMBERFORMAT_ERROR;
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
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
				i <= this.bmM023M024M026InitBean.getEndInsertIdx();
				i++) {

			int idx = i - this.bmM023M024M026InitBean.getStartInsertIdx();
			fillChar = "連番No: " + filter.getSeq();

			try {
				double aveTime = Double.parseDouble(removeQuote(aveList[idx]));
				double currentTimeValue = ExecuteMainUtil.convertToMinutes(filter.getTime());
				aveList[idx] = String.valueOf(aveTime + currentTimeValue) + "'";
				cntList[idx]++;
			} catch (NumberFormatException e) {
				String messageCd = MessageCdConst.MCD00015E_NUMBERFORMAT_ERROR;
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
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
				i <= this.bmM023M024M026InitBean.getEndIdx();
				i++) {

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

				String currentNumeric = parseStatValue(currentValue);
				if (currentNumeric == null || currentNumeric.isBlank()) {
					continue;
				}

				double value = Double.parseDouble(currentNumeric);
				double avg = Double.parseDouble(avgStr);
				double diffSquared = Math.pow(value - avg, 2);

				double prev = (sigmaList[idx] != null && !sigmaList[idx].isBlank())
						? Double.parseDouble(sigmaList[idx]) : 0.0;
				sigmaList[idx] = String.valueOf(prev + diffSquared);
				cntList[idx]++;
			} catch (NumberFormatException e) {
				String messageCd = MessageCdConst.MCD00015E_NUMBERFORMAT_ERROR;
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
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
					i <= this.bmM023M024M026InitBean.getEndInsertIdx();
					i++) {

				int idx = i - this.bmM023M024M026InitBean.getStartInsertIdx();

				String aveStr = aveList[idx];
				if (aveStr == null || aveStr.isBlank()) {
					continue;
				}

				double averageValue = Double.parseDouble(removeQuote(aveStr));
				double sigmaValue = (sigmaList[idx] != null && !sigmaList[idx].isBlank())
						? Double.parseDouble(removeQuote(sigmaList[idx]))
						: 0.0;

				double diffSquared = Math.pow(currentTimeValue - averageValue, 2);
				sigmaList[idx] = String.valueOf(sigmaValue + diffSquared) + "'";
				cntList[idx]++;
			}
		} catch (NumberFormatException e) {
			String messageCd = MessageCdConst.MCD00015E_NUMBERFORMAT_ERROR;
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
		}
		return sigmaList;
	}

	/**
	 * 歪度
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

		List<String> orderedFieldNames = new ArrayList<>(this.bmM030StatEncryptionBean.getFieldMap().keySet());

		for (int idx = 0; idx < orderedFieldNames.size() && idx < skewness.length; idx++) {
			String fieldName = orderedFieldNames.get(idx);
			String fillChar = "フィールド名: " + fieldName;

			try {
				Field field = StatEncryptionEntity.class.getDeclaredField(fieldName);
				field.setAccessible(true);

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
				String skewSigma = (cnt <= 1) ? ""
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
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			}
		}

		for (int i = 0; i < skewness.length; i++) {
			int cnt = cntList[i];
			double skew = skewness[i];

			if (cnt < 3 || !Double.isFinite(skew)) {
				skewnessList[i] = "0.000";
				continue;
			}

			double result = (cnt / ((cnt - 1.0) * (cnt - 2.0))) * skew;
			skewnessList[i] = Double.isFinite(result) ? String.format("%.3f", result) : "0.000";
		}

		return skewnessList;
	}

	/**
	 * 尖度
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

		List<String> orderedFieldNames = new ArrayList<>(this.bmM030StatEncryptionBean.getFieldMap().keySet());

		for (int idx = 0; idx < orderedFieldNames.size() && idx < kurtosis.length; idx++) {
			String fieldName = orderedFieldNames.get(idx);
			String fillChar = "フィールド名: " + fieldName;

			try {
				Field field = StatEncryptionEntity.class.getDeclaredField(fieldName);
				field.setAccessible(true);

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
				String kurtSigma = (cnt <= 1) ? ""
						: String.valueOf(Math.sqrt(Double.parseDouble(kurtSumSigma) / (cnt - 1)));

				if ("".equals(kurtAve) || "".equals(kurtSigma)) {
					continue;
				}

				for (String kurt : kurtList) {
					String currentKurtosisNumeric = parseStatValue(kurt);
					if (currentKurtosisNumeric == null) {
						continue;
					}
					kurtosis[idx] += Math.pow(
							(Double.parseDouble(currentKurtosisNumeric) - Double.parseDouble(kurtAve))
									/ Double.parseDouble(kurtSigma),
							4);
				}
				cntList[idx] = cnt;
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			}
		}

		for (int i = 0; i < kurtosis.length; i++) {
			int cnt = cntList[i];
			double kurt = kurtosis[i];

			if (cnt < 4 || !Double.isFinite(kurt)) {
				kurtosisList[i] = "0.000";
				continue;
			}

			double a = (cnt * (cnt + 1.0)) / ((cnt - 1.0) * (cnt - 2.0) * (cnt - 3.0));
			double b = (3.0 * Math.pow(cnt - 1.0, 2.0)) / ((cnt - 2.0) * (cnt - 3.0));
			double result = a * kurt - b;

			kurtosisList[i] = Double.isFinite(result) ? String.format("%.3f", result) : "0.000";
		}

		return kurtosisList;
	}

	/**
	 * 初期フォーマット
	 */
	private void initFormat(BookDataEntity entity, String[] list, String listStr) {
		final String METHOD_NAME = "initFormat";
		final int FEATURE_START = 11;
		String featureName = "";

		try {
			Field[] allFields = BookDataEntity.class.getDeclaredFields();
			for (int i = FEATURE_START; i < FEATURE_START + AverageStatisticsSituationConst.COUNTER; i++) {
				featureName = allFields[i].getName();
				allFields[i].setAccessible(true);
				String featureValue = (String) allFields[i].get(entity);
				String format = getInitialValueByFormat(featureValue);

				if (listStr.contains("Min")) {
					format = format.replace("0.0", "10000.0");
					format = format.replace("0/0", "10000/10000");
				}
				list[i - FEATURE_START] = format;
			}
		} catch (Exception ex) {
			String messageCd = MessageCdConst.MCD00016E_FORMAT_ERROR;
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, ex, featureName);
		}
	}

	/**
	 * 共通除算
	 */
	private String[] commonDivision(String[] list, Integer[] cntList, String suffix) {
		for (int i = 0; i < list.length; i++) {
			if (list[i] == null) {
				list[i] = "0" + suffix;
				continue;
			}

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
	 * Entityへ設定
	 */
	private ScoreBasedFeatureStatsEntity setStatValuesToEntity(
			ScoreBasedFeatureStatsEntity entity, String insertStr, int ind) {

		final String METHOD_NAME = "setStatValuesToEntity";

		try {
			Field[] allFields = ScoreBasedFeatureStatsEntity.class.getDeclaredFields();
			Field field = allFields[ind];
			field.setAccessible(true);
			field.set(entity, insertStr);
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
			String fillChar = "ScoreBasedFeatureEntity への値設定エラー";
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			this.manageLoggerComponent.createSystemException(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, null);
		}
		return entity;
	}

	/**
	 * その他情報格納
	 */
	private ScoreBasedFeatureStatsEntity setOtherEntity(
			String score, String situation, String country, String league,
			Boolean updFlg, String id, ScoreBasedFeatureStatsEntity entity) {

		entity.setId(id);
		entity.setUpd(updFlg);
		entity.setScore(score);
		entity.setSituation(situation);
		entity.setCountry(country);
		entity.setLeague(league);
		return entity;
	}

	/**
	 * ログ用
	 */
	private String setLoggerFillChar(String situation, String score, String country, String league) {
		StringBuilder sb = new StringBuilder();
		sb.append("状況: ").append(situation).append(", ");
		sb.append("スコア: ").append(score).append(", ");
		sb.append("国: ").append(country).append(", ");
		sb.append("リーグ: ").append(league);
		return sb.toString();
	}

	/**
	 * BM_M030組み立て
	 */
	private StatEncryptionEntity buildBmM30Form(
			final List<BookDataEntity> entities,
			String country,
			String league,
			String home,
			String away,
			String chkBody,
			Map<String, Function<BookDataEntity, String>> fieldMap) {

		final String METHOD_NAME = "buildBmM30Form";
		StatEncryptionEntity result = new StatEncryptionEntity();

		for (Map.Entry<String, Function<BookDataEntity, String>> entry : fieldMap.entrySet()) {
			String fieldName = entry.getKey();
			Function<BookDataEntity, String> getter = entry.getValue();

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
			} catch (NoSuchFieldException | IllegalAccessException ex) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, ex, fieldName);
			}
		}

		result.setCountry(country);
		result.setLeague(league);
		result.setHome(home);
		result.setAway(away);
		result.setChkBody(chkBody);
		result.setUpdFlg(false);
		return result;
	}

	/**
	 * BM_M030マージ
	 */
	private StatEncryptionEntity mergeStatEncryptionEntity(
			StatEncryptionEntity target,
			StatEncryptionEntity source,
			Set<String> mergeFieldNames) {

		final String METHOD_NAME = "mergeStatEncryptionEntity";

		for (String fieldName : mergeFieldNames) {
			try {
				Field field = StatEncryptionEntity.class.getDeclaredField(fieldName);
				field.setAccessible(true);

				String targetValue = (String) field.get(target);
				String sourceValue = (String) field.get(source);

				if (sourceValue == null || sourceValue.isEmpty()) {
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
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fieldName);
			}
		}

		return target;
	}

	/**
	 * 既存1件取得＋復号
	 */
	private StatEncryptionEntity findAndDecryptExistingStatEncryption(
			String country,
			String league,
			String home,
			String away,
			String chkBody) {

		final String METHOD_NAME = "findAndDecryptExistingStatEncryption";

		try {
			List<StatEncryptionEntity> list = this.statEncryptionRepository.findEncDataByCondition(
					country, league, home, away, null, chkBody);

			if (list == null || list.isEmpty()) {
				return null;
			}

			// 重複がある場合は最大IDを採用
			StatEncryptionEntity latest = list.stream()
					.filter(e -> e != null)
					.max(Comparator.comparingInt(e -> safeParseInt(e.getId(), Integer.MIN_VALUE)))
					.orElse(null);

			if (latest == null) {
				return null;
			}

			return decryptStatEncryptionEntity(latest);
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00017E_ENCRYPTION_ERROR;
			String fillChar = "既存stat_encryption取得/復号に失敗: "
					+ "country=" + country + ", league=" + league
					+ ", home=" + home + ", away=" + away + ", chkBody=" + chkBody;
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			return null;
		}
	}

	/**
	 * 復号
	 */
	private StatEncryptionEntity decryptStatEncryptionEntity(StatEncryptionEntity entity) throws Exception {
		StatEncryptionEntity decrypted = shallowCopyStatEncryptionEntity(entity);

		Set<String> dataFieldNames = new LinkedHashSet<>(this.bmM030StatEncryptionBean.getFieldMap().keySet());
		SecretKeySpec secretKey = createSecretKey();
		IvParameterSpec iv = new IvParameterSpec(FIXED_IV);

		for (String fieldName : dataFieldNames) {
			Field field = StatEncryptionEntity.class.getDeclaredField(fieldName);
			field.setAccessible(true);

			String value = (String) field.get(entity);
			if (value == null || value.isBlank()) {
				continue;
			}

			String plain = decrypto(value, secretKey, iv);
			field.set(decrypted, plain);
		}

		decrypted.setUpdFlg(true);
		return decrypted;
	}

	/**
	 * 暗号化
	 */
	private StatEncryptionEntity encryptStatEncryptionEntity(StatEncryptionEntity entity) throws Exception {
		StatEncryptionEntity encrypted = shallowCopyStatEncryptionEntity(entity);

		Set<String> dataFieldNames = new LinkedHashSet<>(this.bmM030StatEncryptionBean.getFieldMap().keySet());
		SecretKeySpec secretKey = createSecretKey();
		IvParameterSpec iv = new IvParameterSpec(FIXED_IV);

		for (String fieldName : dataFieldNames) {
			Field field = StatEncryptionEntity.class.getDeclaredField(fieldName);
			field.setAccessible(true);

			String value = (String) field.get(entity);
			if (value == null || value.isBlank()) {
				field.set(encrypted, value);
				continue;
			}

			String cipher = encrypto(value, secretKey, iv);
			field.set(encrypted, cipher);
		}

		return encrypted;
	}

	/**
	 * 浅いコピー
	 */
	private StatEncryptionEntity shallowCopyStatEncryptionEntity(StatEncryptionEntity source) throws Exception {
		StatEncryptionEntity target = new StatEncryptionEntity();

		for (Field field : StatEncryptionEntity.class.getDeclaredFields()) {
			field.setAccessible(true);
			field.set(target, field.get(source));
		}

		return target;
	}

	/**
	 * 暗号キー作成
	 */
	private SecretKeySpec createSecretKey() {
		String raw = this.bmM030StatEncryptionBean.getBmm030Key();
		return new SecretKeySpec(raw.getBytes(StandardCharsets.UTF_8), AES);
	}

	/**
	 * 暗号化
	 */
	private String encrypto(String plainText, SecretKeySpec key, IvParameterSpec iv)
			throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
			InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {

		Cipher encrypter = Cipher.getInstance(AES_CBC_PKCS5Padding);
		encrypter.init(Cipher.ENCRYPT_MODE, key, iv);
		byte[] encByte = encrypter.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
		return Base64.getEncoder().encodeToString(encByte);
	}

	/**
	 * 復号
	 */
	private String decrypto(String encText, SecretKeySpec key, IvParameterSpec iv)
			throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
			InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {

		Cipher decrypter = Cipher.getInstance(AES_CBC_PKCS5Padding);
		decrypter.init(Cipher.DECRYPT_MODE, key, iv);
		byte[] byteText = Base64.getDecoder().decode(encText);
		return new String(decrypter.doFinal(byteText), StandardCharsets.UTF_8);
	}

	/**
	 * キー生成
	 */
	private String buildMatchKey(String country, String league, String home, String away, String chkBody) {
		return nvl(country) + "||" + nvl(league) + "||" + nvl(home) + "||" + nvl(away) + "||" + nvl(chkBody);
	}

	/**
	 * ロック取得
	 */
	private Object getLock(String key) {
		return lockMap.computeIfAbsent(key, k -> new Object());
	}

	/**
	 * null安全
	 */
	private String nvl(String s) {
		return s == null ? "" : s;
	}

	/**
	 * quote除去
	 */
	private String removeQuote(String s) {
		return s == null ? "" : s.replace("'", "");
	}

	/**
	 * 数値変換安全版
	 */
	private double safeParseDouble(String s, double defaultValue) {
		try {
			if (s == null || s.isBlank()) {
				return defaultValue;
			}
			return Double.parseDouble(s);
		} catch (Exception e) {
			return defaultValue;
		}
	}

	/**
	 * int変換安全版
	 */
	private int safeParseInt(String s, int defaultValue) {
		try {
			if (s == null || s.isBlank()) {
				return defaultValue;
			}
			return Integer.parseInt(s);
		} catch (Exception e) {
			return defaultValue;
		}
	}

	/** 区切り文字で2分割 */
	private static String[] split2(String s, String sep) {
		if (s == null) {
			return new String[] { "", "" };
		}
		int i = s.indexOf(sep);
		if (i < 0) {
			return new String[] { s, "" };
		}
		return new String[] { s.substring(0, i), s.substring(i + sep.length()) };
	}

	/** 「国,リーグ」を安全に分割 */
	private static String[] safeLeague(String key) {
		try {
			String[] a = ExecuteMainUtil.splitLeagueInfo(key);
			if (a != null && a.length >= 2) {
				return new String[] { a[0], a[1] };
			}
			if (a != null && a.length == 1) {
				return new String[] { a[0], "" };
			}
			return new String[] { "", "" };
		} catch (Exception ignore) {
			return split2(key, ",");
		}
	}

	/**
	 * skew/kurt補助：平均
	 */
	private ScoreBasedFeatureOutputDTO setSkewnessOrKurtosisSumAve(String[] skewOrKurtList, Integer cnt) {
		ScoreBasedFeatureOutputDTO out = new ScoreBasedFeatureOutputDTO();
		final String METHOD_NAME = "setSkewnessOrKurtosisSumAve";
		double sum = 0.0;

		for (int i = 0; i < skewOrKurtList.length; i++) {
			String currentValue = skewOrKurtList[i];
			String numericStr = parseStatValue(currentValue);
			if (numericStr == null || numericStr.isBlank() || isPercentAndFractionFormat(currentValue)) {
				continue;
			}

			try {
				sum += Double.parseDouble(numericStr);
				cnt++;
			} catch (NumberFormatException e) {
				String messageCd = MessageCdConst.MCD00015E_NUMBERFORMAT_ERROR;
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, numericStr);
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, numericStr);
			}
		}

		out.setCnt(String.valueOf(cnt));
		out.setAve(String.valueOf(sum));
		return out;
	}

	/**
	 * skew/kurt補助：分散和
	 */
	private ScoreBasedFeatureOutputDTO setSkewnessOrKurtosisSumSigma(String[] skewOrKurtList, String skewOrKurtAve, Integer cnt) {
		ScoreBasedFeatureOutputDTO out = new ScoreBasedFeatureOutputDTO();
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
				sum += Math.pow((numeric - ave), 2);
				cnt++;
			} catch (NumberFormatException e) {
				String messageCd = MessageCdConst.MCD00015E_NUMBERFORMAT_ERROR;
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, numericStr);
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, numericStr);
			}
		}

		out.setCnt(String.valueOf(cnt));
		out.setSigma(String.valueOf(sum));
		return out;
	}

	/**
	 * 既存スコアパターン抽出
	 */
	protected List<String> extractExistingScorePatterns(List<BookDataEntity> entities) {
		if (entities == null || entities.isEmpty()) {
			return List.of();
		}

		return entities.stream()
				.filter(e -> e != null)
				.map(e -> nvl(e.getHomeScore()) + "-" + nvl(e.getAwayScore()))
				.filter(s -> !"-".equals(s))
				.distinct()
				.sorted()
				.collect(Collectors.toList());
	}
}