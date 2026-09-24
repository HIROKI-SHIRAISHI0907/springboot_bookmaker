package dev.application.analyze.bm_m026;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.bm_m023.AverageStatisticsSituationConst;
import dev.application.analyze.bm_m023.BmM023M024M026InitBean;
import dev.application.analyze.bm_m023.ScoreBasedFeatureOutputDTO;
import dev.application.analyze.bm_m023.StatFormatResolver;
import dev.application.analyze.bm_m030.BmM030StatEncryptionBean;
import dev.application.analyze.bm_m030.StatEncryptionEntity;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.bm.EachTeamScoreBasedFeatureStatsRepository;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * BM_M026統計分析ロジック（手動データ投入の場合は適用対象外）
 * DB登録・更新は Writer に分離
 *
 * 【修正履歴】
 * ・ループ終端が「< endScoreInsertIdx」になっており、最後の項目(awayInterceptCountStat)が
 *   読み込み・計算・出力されていなかった不具合を修正（「<=」に統一）
 * ・既存統計値の「平均」をそのまま合計として扱っていた不具合を修正
 *   （今回分を単独で集計 → 既存値とマージ。標準偏差は並列分散公式で合成）
 * ・setInitData直後の initFormat で既存の最小値・最大値が上書きされていた不具合を修正
 * ・相手側(H/A逆側)の項目が初期値("10000.00,0,..."や"null")で上書きされていた不具合を修正
 *   （相手側はDBの既存値をそのまま引き継ぐ）
 * ・歪度と尖度で件数配列を共有していたのを分離
 * ・歪度/尖度で標準偏差0の場合の0除算をスキップ
 * ・スコアが空の場合に Integer.parseInt で例外になる箇所を安全化
 */
@Component
@Slf4j
public class EachTeamScoreBasedFeatureStat extends StatFormatResolver implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = EachTeamScoreBasedFeatureStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = EachTeamScoreBasedFeatureStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M026_EACH_TEAM_SCORE_BASED_FEATURE";

	// ===== Striped Lock（固定本数ロック）=====
	private static final int LOCK_STRIPES = 2048;
	private final Object[] locks = new Object[LOCK_STRIPES];

	{
		for (int i = 0; i < LOCK_STRIPES; i++) {
			locks[i] = new Object();
		}
	}

	/**
	 * 【追加】統計値の配列一式（既存値 / 今回分 を分けて持つため）
	 */
	private static final class StatArrays {
		String[] min;
		Integer[] minCnt;
		String[] max;
		Integer[] maxCnt;
		String[] ave;
		Integer[] aveCnt;
		String[] sigma;
		Integer[] sigmaCnt;
		String[] tMin;
		Integer[] tMinCnt;
		String[] tMax;
		Integer[] tMaxCnt;
		String[] tAve;
		Integer[] tAveCnt;
		String[] tSigma;
		Integer[] tSigmaCnt;
	}

	/** BmM023M024M026InitBeanクラス */
	@Autowired
	private BmM023M024M026InitBean bmM023M024M026InitBean;

	/** BmM030StatEncryptionBeanクラス */
	@Autowired
	private BmM030StatEncryptionBean bmM030StatEncryptionBean;

	/** 読み取り専用Repository */
	@Autowired
	private EachTeamScoreBasedFeatureStatsRepository eachTeamScoreBasedFeatureStatsRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/** BM_M026 Writer */
	@Autowired
	private EachTeamScoreBasedFeatureWriter eachTeamScoreBasedFeatureWriter;

	/** BM_M030 Writer */
	@Autowired
	private EachTeamScoreBasedFeatureStatEncryptionWriter eachTeamScoreBasedFeatureStatEncryptionWriter;

	/**
	 * {@inheritDoc}
	 * 集計本体は非トランザクションで実行し、DB更新のみWriterに委譲
	 */
	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) throws Exception {
		final String METHOD_NAME = "calcStat";

		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		long startedAt = System.currentTimeMillis();

		int totalLeagueCount = (entities == null) ? 0 : entities.size();
		int leagueIndex = 0;
		int processedMatchCount = 0;
		int skippedNotFinCount = 0;
		int savedEntityCount = 0;
		int encInsertOrUpdateCount = 0;

		try {
			this.bmM030StatEncryptionBean.resetForRun();

			if (entities == null || entities.isEmpty()) {
				log.info("[BM_M026] calcStat skip. entities empty");
				return;
			}

			log.info("[BM_M026] calcStat start. leagueCount={}", totalLeagueCount);

			for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
				leagueIndex++;

				String[] data = safeLeague(entry.getKey());
				String country = data[0];
				String league = data[1];

				if (country.isBlank() || league.isBlank()) {
					this.manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							"skip: invalid league key", null, "key=" + entry.getKey());
					log.warn("[BM_M026] league skip. invalid key={}", entry.getKey());
					continue;
				}

				log.info("[BM_M026] league start. leagueIndex={}/{}, country={}, league={}",
						leagueIndex, totalLeagueCount, country, league);

				this.bmM030StatEncryptionBean.init(country, league);

				ConcurrentHashMap<String, StatEncryptionEntity> bmM30Map = new ConcurrentHashMap<>();

				Map<String, List<BookDataEntity>> entrySub = entry.getValue();
				if (entrySub == null || entrySub.isEmpty()) {
					log.info("[BM_M026] league skip. inner map empty. country={}, league={}", country, league);
					continue;
				}

				int leagueSavedCount = 0;
				int matchIndex = 0;
				int totalMatchCount = entrySub.size();

				for (List<BookDataEntity> entityList : entrySub.values()) {
					matchIndex++;

					if (entityList == null || entityList.isEmpty()) {
						log.info("[BM_M026] match skip. empty list. leagueIndex={}/{}, matchIndex={}/{} country={}, league={}",
								leagueIndex, totalLeagueCount, matchIndex, totalMatchCount, country, league);
						continue;
					}

					BookDataEntity maxEntity = ExecuteMainUtil.getMaxSeqEntities(entityList);
					if (maxEntity == null) {
						log.warn("[BM_M026] match skip. maxEntity null. leagueIndex={}/{}, matchIndex={}/{} country={}, league={}",
								leagueIndex, totalLeagueCount, matchIndex, totalMatchCount, country, league);
						continue;
					}

					String home = safe(maxEntity.getHomeTeamName());
					String away = safe(maxEntity.getAwayTeamName());
					String score = safe(maxEntity.getHomeScore()) + "-" + safe(maxEntity.getAwayScore());
					String time = safe(maxEntity.getTime());

					log.info("[BM_M026] match start. leagueIndex={}/{}, matchIndex={}/{}, country={}, league={}, home={}, away={}, score={}, time={}, rows={}",
							leagueIndex, totalLeagueCount, matchIndex, totalMatchCount,
							country, league, home, away, score, time, entityList.size());

					if (!BookMakersCommonConst.FIN.equals(time)) {
						skippedNotFinCount++;
						log.info("[BM_M026] match skip non-FIN. country={}, league={}, home={}, away={}, score={}, time={}",
								country, league, home, away, score, time);
						continue;
					}

					ConcurrentHashMap<String, List<EachTeamScoreBasedFeatureEntity>> resultMap = decideBasedMain(
							entityList, country, league, bmM30Map);

					if (resultMap == null || resultMap.isEmpty()) {
						log.info("[BM_M026] match done(no output). country={}, league={}, home={}, away={}, resultMapSize=0, bmM30MapSize={}",
								country, league, home, away, bmM30Map.size());
						continue;
					}

					int savedThisMatch = 0;

					for (Map.Entry<String, List<EachTeamScoreBasedFeatureEntity>> entrys : resultMap.entrySet()) {
						List<EachTeamScoreBasedFeatureEntity> vals = entrys.getValue();
						if (vals == null || vals.isEmpty()) {
							continue;
						}
						for (EachTeamScoreBasedFeatureEntity subSubEntity : vals) {
							if (subSubEntity == null) {
								continue;
							}
							this.eachTeamScoreBasedFeatureWriter.write(subSubEntity);
							savedThisMatch++;
							savedEntityCount++;
							leagueSavedCount++;
						}
					}

					processedMatchCount++;

					log.info("[BM_M026] match done. leagueIndex={}/{}, matchIndex={}/{}, country={}, league={}, home={}, away={}, resultMapSize={}, savedThisMatch={}, bmM30MapSize={}",
							leagueIndex, totalLeagueCount, matchIndex, totalMatchCount,
							country, league, home, away, resultMap.size(), savedThisMatch, bmM30Map.size());
				}

				log.info("[BM_M026] stat_encryption save start. country={}, league={}, mapSize={}",
						country, league, bmM30Map.size());

				int encIndex = 0;
				int encTotal = bmM30Map.size();

				for (Map.Entry<String, StatEncryptionEntity> encEntry : bmM30Map.entrySet()) {
					encIndex++;

					String encKey = encEntry.getKey();
					StatEncryptionEntity e = encEntry.getValue();
					if (e == null) {
						log.warn("[BM_M026] stat_encryption skip null. encIndex={}/{}, key={}", encIndex, encTotal, encKey);
						continue;
					}

					log.info("[BM_M026] before encryption. encIndex={}/{}, key={}, summary={}",
							encIndex, encTotal, encKey, summarizeEnc(e));

					StatEncryptionEntity encryptedEntity = encryption(e);

					log.info("[BM_M026] after encryption. encIndex={}/{}, key={}, updFlg={}, id={}",
							encIndex, encTotal, encKey, encryptedEntity.isUpdFlg(), safe(encryptedEntity.getId()));

					this.eachTeamScoreBasedFeatureStatEncryptionWriter.write(encryptedEntity);
					encInsertOrUpdateCount++;

					log.info("[BM_M026] stat_encryption write done. encIndex={}/{}, key={}",
							encIndex, encTotal, encKey);
				}

				log.info("[BM_M026] league done. leagueIndex={}/{}, country={}, league={}, processedMatchCount={}, skippedNotFinCount={}, savedEntityCount={}, leagueSavedCount={}, encWriteCount={}, bmM30MapSize={}",
						leagueIndex, totalLeagueCount, country, league,
						processedMatchCount, skippedNotFinCount, savedEntityCount, leagueSavedCount,
						encInsertOrUpdateCount, bmM30Map.size());
			}

			log.info("[BM_M026] calcStat finished. leagueCount={}, processedMatchCount={}, skippedNotFinCount={}, savedEntityCount={}, encWriteCount={}, elapsedMs={}",
					totalLeagueCount, processedMatchCount, skippedNotFinCount,
					savedEntityCount, encInsertOrUpdateCount,
					(System.currentTimeMillis() - startedAt));

		} catch (Exception e) {
			log.error("[BM_M026] calcStat failed. processedMatchCount={}, skippedNotFinCount={}, savedEntityCount={}, encWriteCount={}",
					processedMatchCount, skippedNotFinCount, savedEntityCount, encInsertOrUpdateCount, e);
			throw e;
		} finally {
			this.manageLoggerComponent.debugEndInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();
		}
	}

	/**
	 * 処理メインロジック
	 */
	private ConcurrentHashMap<String, List<EachTeamScoreBasedFeatureEntity>> decideBasedMain(
			List<BookDataEntity> entities,
			String country, String league, ConcurrentHashMap<String, StatEncryptionEntity> bmM30Map) {

		final String METHOD_NAME = "decideBasedMain";

		if (entities == null || entities.isEmpty()) {
			log.info("[BM_M026] decideBasedMain skip. empty entities. country={}, league={}", country, league);
			return null;
		}

		BookDataEntity returnMaxEntity = ExecuteMainUtil.getMaxSeqEntities(entities);
		if (returnMaxEntity == null) {
			log.warn("[BM_M026] decideBasedMain skip. maxEntity null. country={}, league={}", country, league);
			return null;
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, safe(returnMaxEntity.getFilePath()));

		if (!BookMakersCommonConst.FIN.equals(returnMaxEntity.getTime())) {
			log.info("[BM_M026] decideBasedMain skip non-FIN. country={}, league={}, home={}, away={}, score={}, time={}",
					country, league,
					safe(returnMaxEntity.getHomeTeamName()),
					safe(returnMaxEntity.getAwayTeamName()),
					safe(returnMaxEntity.getHomeScore()) + "-" + safe(returnMaxEntity.getAwayScore()),
					safe(returnMaxEntity.getTime()));
			return null;
		}

		// 【修正】スコアが空でも例外にならないよう安全に判定
		String situation = (safeParseInt(returnMaxEntity.getHomeScore()) == 0
				&& safeParseInt(returnMaxEntity.getAwayScore()) == 0)
						? AverageStatisticsSituationConst.NOSCORE
						: AverageStatisticsSituationConst.SCORE;

		List<String> flgs = List.of(
				AverageStatisticsSituationConst.ALL_DATA,
				AverageStatisticsSituationConst.FIRST_DATA,
				AverageStatisticsSituationConst.SECOND_DATA,
				AverageStatisticsSituationConst.EACH_SCORE);

		List<String> allScores = extractExistingScorePatterns(entities);

		ConcurrentHashMap<String, List<EachTeamScoreBasedFeatureEntity>> allMap = new ConcurrentHashMap<>();

		log.info("[BM_M026] decideBasedMain start. country={}, league={}, home={}, away={}, finalScore={}, situation={}, rows={}, scorePatternSize={}",
				country, league,
				safe(returnMaxEntity.getHomeTeamName()),
				safe(returnMaxEntity.getAwayTeamName()),
				safe(returnMaxEntity.getHomeScore()) + "-" + safe(returnMaxEntity.getAwayScore()),
				situation, entities.size(), allScores.size());

		for (int i = 1; i <= 2; i++) {
			String team = (i == 1) ? returnMaxEntity.getHomeTeamName() : returnMaxEntity.getAwayTeamName();
			String ha = (i == 1) ? "H" : "A";
			for (String flg : flgs) {
				if (AverageStatisticsSituationConst.EACH_SCORE.equals(flg)) {
					if (!AverageStatisticsSituationConst.NOSCORE.equals(situation)) {
						for (String score : allScores) {
							if ("0-0".equals(score)) {
								continue;
							}
							basedEntities(allMap, entities, score, situation, flg, country, league, team, ha, bmM30Map);
						}
					}
				} else {
					basedEntities(allMap, entities, null, situation, flg, country, league, team, ha, bmM30Map);
				}
			}
		}

		log.info("[BM_M026] decideBasedMain done. country={}, league={}, home={}, away={}, resultMapSize={}, bmM30MapSize={}",
				country, league,
				safe(returnMaxEntity.getHomeTeamName()),
				safe(returnMaxEntity.getAwayTeamName()),
				allMap.size(), bmM30Map.size());

		return allMap;
	}

	/**
	 * 基準エンティティ指定
	 */
	private void basedEntities(
			ConcurrentHashMap<String, List<EachTeamScoreBasedFeatureEntity>> insertMap,
			List<BookDataEntity> entities,
			String connectScore,
			String situation,
			String flg,
			String country,
			String league,
			String team,
			String ha,
			ConcurrentHashMap<String, StatEncryptionEntity> bmM30Map) {

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
								+ ", country=" + country + ", league=" + league);
				log.info("[BM_M026] basedEntities skip. half not found. country={}, league={}, team={}, flg={}, rows={}",
						country, league, team, flg, entities.size());
				return;
			}
			int halfTimeSeq = safeParseInt(half.getSeq());
			if (AverageStatisticsSituationConst.FIRST_DATA.equals(flg)) {
				filteredList = entities.stream()
						.filter(entity -> safeParseInt(entity.getSeq()) <= halfTimeSeq)
						.collect(Collectors.toList());
			} else if (AverageStatisticsSituationConst.SECOND_DATA.equals(flg)) {
				filteredList = entities.stream()
						.filter(entity -> safeParseInt(entity.getSeq()) > halfTimeSeq)
						.collect(Collectors.toList());
			}
		}

		if (filteredList == null || filteredList.isEmpty()) {
			log.info("[BM_M026] basedEntities skip. filtered empty. country={}, league={}, team={}, ha={}, flg={}, connectScore={}",
					country, league, team, ha, flg, safe(connectScore));
			return;
		}

		String chkBody;
		boolean updFlg = false;
		String id = null;
		List<EachTeamScoreBasedFeatureEntity> statList = null;

		if (AverageStatisticsSituationConst.ALL_DATA.equals(flg) ||
				AverageStatisticsSituationConst.FIRST_DATA.equals(flg) ||
				AverageStatisticsSituationConst.SECOND_DATA.equals(flg)) {

			chkBody = flg;

			log.info("[BM_M026] before getData. country={}, league={}, team={}, situation={}, score={}",
					country, league, team, situation, flg);

			EachTeamScoreBasedFeatureOutputDTO dto = getData(flg, situation, country, league, team);
			statList = dto.getList();
			updFlg = dto.isUpdFlg();
			id = dto.getId();

			log.info("[BM_M026] after getData. country={}, league={}, team={}, situation={}, score={}, updFlg={}, id={}, existingSize={}",
					country, league, team, situation, flg, updFlg, safe(id), sizeOf(statList));

		} else {
			chkBody = connectScore;

			log.info("[BM_M026] before getData. country={}, league={}, team={}, situation={}, score={}",
					country, league, team, situation, connectScore);

			EachTeamScoreBasedFeatureOutputDTO dto = getData(connectScore, situation, country, league, team);
			statList = dto.getList();
			updFlg = dto.isUpdFlg();
			id = dto.getId();

			log.info("[BM_M026] after getData. country={}, league={}, team={}, situation={}, score={}, updFlg={}, id={}, existingSize={}",
					country, league, team, situation, connectScore, updFlg, safe(id), sizeOf(statList));
		}

		Map<String, Function<BookDataEntity, String>> fieldMap = this.bmM030StatEncryptionBean.getFieldMap();
		final List<BookDataEntity> filteredFinalList = filteredList;
		final String chkFinalBody = chkBody;

		final String key = country + "-" + league + "-" + team + "-" + chkFinalBody;

		StatEncryptionEntity decidedEntity;
		synchronized (getLock(key)) {
			StatEncryptionEntity exist = bmM30Map.get(key);

			if (exist != null) {
				StatEncryptionEntity addPart = buildBmM30Form(
						filteredFinalList, country, league, ha, chkFinalBody, fieldMap);

				StatEncryptionEntity merged = mergeStatEncryptionEntity(exist, addPart, ha);

				String existId = exist.getId();
				boolean alreadyPersisted = existId != null && !existId.isBlank();

				merged.setId(existId);
				merged.setUpdFlg(alreadyPersisted);
				merged.setTeam(team);

				bmM30Map.put(key, merged);

			} else {
				StatEncryptionEntity fresh = buildBmM30Form(
						filteredFinalList, country, league, ha, chkFinalBody, fieldMap);

				fresh.setId(null);
				fresh.setUpdFlg(false);
				fresh.setTeam(team);

				bmM30Map.put(key, fresh);
			}

			decidedEntity = bmM30Map.get(key);
		}

		if (decidedEntity == null) {
			log.warn("[BM_M026] basedEntities skip. decidedEntity null. key={}", key);
			return;
		}

		log.info("[BM_M026] basedEntities merged. key={}, team={}, ha={}, flg={}, chkBody={}, filteredSize={}, bmM30MapSize={}",
				key, team, ha, flg, chkBody, filteredList.size(), bmM30Map.size());

		// 【修正】既存値(prev)と今回分(cur)を分けて保持する
		StatArrays prev = newStatArrays();
		setInitData(prev, statList, ha);

		StatArrays cur = newStatArrays();
		BookDataEntity returnDataEntity = ExecuteMainUtil.getMaxSeqEntities(entities);
		initFormat(returnDataEntity, cur.min, "Min");
		initFormat(returnDataEntity, cur.max, "Max");

		for (BookDataEntity filter : filteredList) {
			cur.min = setMin(filter, cur.min, cur.minCnt, ha);
			cur.max = setMax(filter, cur.max, cur.maxCnt, ha);
			cur.ave = setSumAve(filter, cur.ave, cur.aveCnt, ha);

			cur.tMin = setTimeMin(filter, cur.tMin, cur.tMinCnt, ha);
			cur.tMax = setTimeMax(filter, cur.tMax, cur.tMaxCnt, ha);
			cur.tAve = setTimeSumAve(filter, cur.tAve, cur.tAveCnt, ha);
		}

		cur.ave = commonDivision(cur.ave, cur.aveCnt, "", ha);
		cur.tAve = commonDivision(cur.tAve, cur.tAveCnt, "'", ha);

		for (BookDataEntity filter : filteredList) {
			cur.sigma = setSumSigma(filter, cur.ave, cur.sigma, cur.sigmaCnt, ha);
			cur.tSigma = setTimeSumSigma(filter, cur.tAve, cur.tSigma, cur.tSigmaCnt, ha);
		}

		cur.sigma = commonDivision(cur.sigma, cur.sigmaCnt, "", ha);
		cur.tSigma = commonDivision(cur.tSigma, cur.tSigmaCnt, "'", ha);

		for (int i = 0; i < cur.sigma.length; i++) {
			if (isOtherSide(ha, i)) {
				continue;
			}
			cur.sigma[i] = String.format("%.2f", Math.sqrt(safeParseDouble(cur.sigma[i], 0.0)));
			cur.tSigma[i] = String.format("%.2f", Math.sqrt(safeParseDouble(cur.tSigma[i], 0.0)));
		}

		// 【修正】既存値と今回分をマージ
		mergePrevious(prev, cur, ha);

		// 【修正】歪度と尖度で件数配列を分離
		String[] aveSkewKurtList = this.bmM023M024M026InitBean.getAvgList().clone();
		String[] sigmaSkewKurtList = this.bmM023M024M026InitBean.getSigmaList().clone();
		String[] skewnessList = this.bmM023M024M026InitBean.getSkewnessList().clone();
		String[] kurtosisList = this.bmM023M024M026InitBean.getKurtosisList().clone();
		Integer[] skewnessCntList = newZeroCntList(AverageStatisticsSituationConst.COUNTER);
		Integer[] kurtosisCntList = newZeroCntList(AverageStatisticsSituationConst.COUNTER);

		skewnessList = setSkewness(decidedEntity, skewnessList, aveSkewKurtList, sigmaSkewKurtList, skewnessCntList,
				ha);
		kurtosisList = setKurtosis(decidedEntity, kurtosisList, aveSkewKurtList, sigmaSkewKurtList, kurtosisCntList,
				ha);

		EachTeamScoreBasedFeatureEntity entity = new EachTeamScoreBasedFeatureEntity();
		EachTeamScoreBasedFeatureEntity existEntity = (statList == null || statList.isEmpty()) ? null : statList.get(0);
		Field[] statFields = EachTeamScoreBasedFeatureEntity.class.getDeclaredFields();
		StringBuilder sb = new StringBuilder();

		// 【修正】終端を「<=」に変更（最後の項目 awayInterceptCountStat を含める）
		for (int i = this.bmM023M024M026InitBean.getStartScoreInsertIdx(); i <= this.bmM023M024M026InitBean
				.getEndScoreInsertIdx(); i++) {

			int idx = i - this.bmM023M024M026InitBean.getStartScoreInsertIdx();

			// 【修正】相手側の項目は既存値をそのまま引き継ぐ（初期値で上書きしない）
			if (isOtherSide(ha, idx)) {
				entity = setStatValuesToEntity(entity, readStatValue(statFields[i], existEntity), i);
				continue;
			}

			String min = formatDecimal(cur.min[idx]);
			String max = formatDecimal(cur.max[idx]);
			String ave = formatDecimal(cur.ave[idx]);
			String sigma = formatDecimal(cur.sigma[idx]);

			String tMin = formatDecimal(cur.tMin[idx]);
			String tMax = formatDecimal(cur.tMax[idx]);
			String tAve = formatDecimal(cur.tAve[idx]);
			String tSigma = formatDecimal(cur.tSigma[idx]);

			String skewness = skewnessList[idx];
			String kurtosis = kurtosisList[idx];

			sb.append(min).append(",")
					.append(cur.minCnt[idx]).append(",")
					.append(max).append(",")
					.append(cur.maxCnt[idx]).append(",")
					.append(ave).append(",")
					.append(cur.aveCnt[idx]).append(",")
					.append(sigma).append(",")
					.append(cur.sigmaCnt[idx]).append(",")
					.append(tMin).append("'").append(",")
					.append(cur.tMinCnt[idx]).append(",")
					.append(tMax).append("'").append(",")
					.append(cur.tMaxCnt[idx]).append(",")
					.append(tAve).append("'").append(",")
					.append(cur.tAveCnt[idx]).append(",")
					.append(tSigma).append("'").append(",")
					.append(cur.tSigmaCnt[idx]).append(",")
					.append(skewness).append(",")
					.append(kurtosis);

			entity = setStatValuesToEntity(entity, sb.toString(), i);
			sb.setLength(0);
		}

		if (AverageStatisticsSituationConst.ALL_DATA.equals(flg) ||
				AverageStatisticsSituationConst.FIRST_DATA.equals(flg) ||
				AverageStatisticsSituationConst.SECOND_DATA.equals(flg)) {
			entity = setOtherEntity(flg, situation, country, league, team, updFlg, id, entity);
		} else {
			entity = setOtherEntity(connectScore, situation, country, league, team, updFlg, id, entity);
		}

		insertMap.computeIfAbsent(flg, k -> new ArrayList<>()).add(entity);

		log.info("[BM_M026] basedEntities done. country={}, league={}, team={}, ha={}, flg={}, chkBody={}, insertMapKeyCount={}, currentListSize={}",
				country, league, team, ha, flg, chkBody, insertMap.size(), insertMap.get(flg).size());
	}

	/**
	 * 【追加】H/Aの相手側の項目か判定（偶数=ホーム項目, 奇数=アウェー項目）
	 */
	private boolean isOtherSide(String ha, int idx) {
		return ("H".equals(ha) && idx % 2 == 1) || ("A".equals(ha) && idx % 2 == 0);
	}

	/**
	 * 【追加】既存エンティティから統計文字列を取得（なければnull）
	 */
	private String readStatValue(Field field, EachTeamScoreBasedFeatureEntity existEntity) {
		if (existEntity == null) {
			return null;
		}
		try {
			field.setAccessible(true);
			return (String) field.get(existEntity);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * 【追加】Beanの初期値から配列一式を生成
	 */
	private StatArrays newStatArrays() {
		StatArrays s = new StatArrays();
		s.min = this.bmM023M024M026InitBean.getMinList().clone();
		s.max = this.bmM023M024M026InitBean.getMaxList().clone();
		s.ave = this.bmM023M024M026InitBean.getAvgList().clone();
		s.sigma = this.bmM023M024M026InitBean.getSigmaList().clone();
		s.minCnt = this.bmM023M024M026InitBean.getCntList().clone();
		s.maxCnt = this.bmM023M024M026InitBean.getCntList().clone();
		s.aveCnt = this.bmM023M024M026InitBean.getCntList().clone();
		s.sigmaCnt = this.bmM023M024M026InitBean.getCntList().clone();
		s.tMin = this.bmM023M024M026InitBean.getTimeMinList().clone();
		s.tMax = this.bmM023M024M026InitBean.getTimeMaxList().clone();
		s.tAve = this.bmM023M024M026InitBean.getTimeAvgList().clone();
		s.tSigma = this.bmM023M024M026InitBean.getTimeSigmaList().clone();
		s.tMinCnt = this.bmM023M024M026InitBean.getTimeCntList().clone();
		s.tMaxCnt = this.bmM023M024M026InitBean.getTimeCntList().clone();
		s.tAveCnt = this.bmM023M024M026InitBean.getTimeCntList().clone();
		s.tSigmaCnt = this.bmM023M024M026InitBean.getTimeCntList().clone();
		return s;
	}

	/**
	 * 【追加】0埋めの件数配列
	 */
	private Integer[] newZeroCntList(int size) {
		Integer[] list = new Integer[size];
		for (int i = 0; i < size; i++) {
			list[i] = 0;
		}
		return list;
	}

	/**
	 * 【追加】既存値(prev)と今回分(cur)をマージして cur に格納する（自チーム側の項目のみ）
	 */
	private void mergePrevious(StatArrays prev, StatArrays cur, String ha) {
		for (int i = 0; i < cur.min.length; i++) {
			if (isOtherSide(ha, i)) {
				continue;
			}
			// 最小・最大（特徴量）
			cur.min[i] = pickExtreme(prev.min[i], toCnt(prev.minCnt[i]), cur.min[i], toCnt(cur.minCnt[i]), true);
			cur.minCnt[i] = toCnt(cur.minCnt[i]) + toCnt(prev.minCnt[i]);
			cur.max[i] = pickExtreme(prev.max[i], toCnt(prev.maxCnt[i]), cur.max[i], toCnt(cur.maxCnt[i]), false);
			cur.maxCnt[i] = toCnt(cur.maxCnt[i]) + toCnt(prev.maxCnt[i]);

			// 最小・最大（時間）
			cur.tMin[i] = pickTimeExtreme(prev.tMin[i], toCnt(prev.tMinCnt[i]), cur.tMin[i], toCnt(cur.tMinCnt[i]), true);
			cur.tMinCnt[i] = toCnt(cur.tMinCnt[i]) + toCnt(prev.tMinCnt[i]);
			cur.tMax[i] = pickTimeExtreme(prev.tMax[i], toCnt(prev.tMaxCnt[i]), cur.tMax[i], toCnt(cur.tMaxCnt[i]), false);
			cur.tMaxCnt[i] = toCnt(cur.tMaxCnt[i]) + toCnt(prev.tMaxCnt[i]);

			// 平均・標準偏差
			mergeMoments(prev.ave, prev.aveCnt, prev.sigma, prev.sigmaCnt,
					cur.ave, cur.aveCnt, cur.sigma, cur.sigmaCnt, i, "");
			mergeMoments(prev.tAve, prev.tAveCnt, prev.tSigma, prev.tSigmaCnt,
					cur.tAve, cur.tAveCnt, cur.tSigma, cur.tSigmaCnt, i, "'");
		}
	}

	/**
	 * 【追加】最小/最大の比較（setMin/setMaxと同じ判定ルール）
	 */
	private String pickExtreme(String prevVal, int prevCnt, String curVal, int curCnt, boolean isMin) {
		if (prevCnt <= 0 || prevVal == null || prevVal.isBlank()) {
			return curVal;
		}
		if (curCnt <= 0) {
			return prevVal;
		}
		if (!isSameFormat(prevVal, curVal)) {
			return curVal;
		}
		String p = parseStatValue(prevVal);
		String c = parseStatValue(curVal);
		if (p == null) {
			return curVal;
		}
		if (c == null) {
			return prevVal;
		}
		double pd = Double.parseDouble(p);
		double cd = Double.parseDouble(c);
		if (isMin) {
			return pd < cd ? prevVal : curVal;
		}
		return pd > cd ? prevVal : curVal;
	}

	/**
	 * 【追加】時間の最小/最大の比較
	 */
	private String pickTimeExtreme(String prevVal, int prevCnt, String curVal, int curCnt, boolean isMin) {
		if (prevCnt <= 0 || prevVal == null || prevVal.isBlank()) {
			return curVal;
		}
		if (curCnt <= 0) {
			return prevVal;
		}
		double pd = safeParseDouble(prevVal, Double.NaN);
		double cd = safeParseDouble(curVal, Double.NaN);
		if (Double.isNaN(pd)) {
			return curVal;
		}
		if (Double.isNaN(cd)) {
			return prevVal;
		}
		if (isMin) {
			return pd < cd ? prevVal : curVal;
		}
		return pd > cd ? prevVal : curVal;
	}

	/**
	 * 【追加】平均・標準偏差（母標準偏差）のマージ
	 * 平均      : (平均a×件数a + 平均b×件数b) / (件数a+件数b)
	 * 偏差平方和 : M2a + M2b + (平均b-平均a)² × 件数a×件数b / (件数a+件数b)
	 */
	private void mergeMoments(
			String[] prevAve, Integer[] prevAveCnt, String[] prevSigma, Integer[] prevSigmaCnt,
			String[] curAve, Integer[] curAveCnt, String[] curSigma, Integer[] curSigmaCnt,
			int i, String suffix) {

		int naAve = toCnt(prevAveCnt[i]);
		int nbAve = toCnt(curAveCnt[i]);
		int naSig = toCnt(prevSigmaCnt[i]);
		int nbSig = toCnt(curSigmaCnt[i]);

		if (naAve <= 0) {
			return; // 既存データなし → 今回分のまま
		}

		double meanA = safeParseDouble(prevAve[i], 0.0);
		double meanB = safeParseDouble(curAve[i], 0.0);

		if (nbAve <= 0) {
			// 今回分なし → 既存値を採用
			curAve[i] = prevAve[i];
			curAveCnt[i] = naAve;
			curSigma[i] = prevSigma[i];
			curSigmaCnt[i] = naSig;
			return;
		}

		// 平均
		double mean = (meanA * naAve + meanB * nbAve) / (naAve + nbAve);
		curAve[i] = String.valueOf(mean) + suffix;
		curAveCnt[i] = naAve + nbAve;

		// 標準偏差
		int n = naSig + nbSig;
		if (n <= 0) {
			return;
		}
		double sigmaA = safeParseDouble(prevSigma[i], 0.0);
		double sigmaB = safeParseDouble(curSigma[i], 0.0);
		double m2a = sigmaA * sigmaA * naSig;
		double m2b = sigmaB * sigmaB * nbSig;
		double delta = meanB - meanA;
		double m2 = m2a + m2b + delta * delta * ((double) naSig * nbSig) / n;
		double sigma = Math.sqrt(m2 / n);

		curSigma[i] = Double.isFinite(sigma) ? String.format("%.2f", sigma) : "0.00";
		curSigmaCnt[i] = n;
	}

	/**
	 * 【追加】null安全な件数
	 */
	private int toCnt(Integer value) {
		return value == null ? 0 : value;
	}

	/**
	 * 取得メソッド
	 */
	private EachTeamScoreBasedFeatureOutputDTO getData(String score, String situation,
			String country, String league, String team) {
		EachTeamScoreBasedFeatureOutputDTO dto = new EachTeamScoreBasedFeatureOutputDTO();

		EachTeamScoreBasedFeatureEntity data = this.eachTeamScoreBasedFeatureStatsRepository
				.findStatData(score, situation, country, league, team);
		if (data != null) {
			dto.setUpdFlg(true);
			dto.setId(data.getId());
			dto.setList(List.of(data));
		} else {
			dto.setUpdFlg(false);
		}
		return dto;
	}

	/**
	 * 初期値設定（既存値の読み込み）
	 * 【修正】読み込み先を StatArrays(prev) に変更、終端を「<=」に変更
	 */
	private void setInitData(StatArrays prev, List<EachTeamScoreBasedFeatureEntity> list, String ha) {
		final String METHOD_NAME = "setInitData";
		if (list != null && !list.isEmpty()) {
			EachTeamScoreBasedFeatureEntity statEntity = list.get(0);
			Field[] fields = EachTeamScoreBasedFeatureEntity.class.getDeclaredFields();
			for (int i = this.bmM023M024M026InitBean.getStartScoreInsertIdx(); i <= this.bmM023M024M026InitBean
					.getEndScoreInsertIdx(); i++) {
				int idx = i - this.bmM023M024M026InitBean.getStartScoreInsertIdx();
				if (isOtherSide(ha, idx)) {
					continue;
				}
				Field field = fields[i];
				field.setAccessible(true);
				try {
					String statValue = (String) field.get(statEntity);
					if (statValue == null || statValue.isBlank()) {
						continue;
					}
					String[] values = statValue.split(",");
					if (values.length >= 16) {
						prev.min[idx] = values[0].trim();
						prev.minCnt[idx] = Integer.parseInt(values[1].trim());
						prev.max[idx] = values[2].trim();
						prev.maxCnt[idx] = Integer.parseInt(values[3].trim());
						prev.ave[idx] = values[4].trim();
						prev.aveCnt[idx] = Integer.parseInt(values[5].trim());
						prev.sigma[idx] = values[6].trim();
						prev.sigmaCnt[idx] = Integer.parseInt(values[7].trim());
						prev.tMin[idx] = values[8].trim();
						prev.tMinCnt[idx] = Integer.parseInt(values[9].trim());
						prev.tMax[idx] = values[10].trim();
						prev.tMaxCnt[idx] = Integer.parseInt(values[11].trim());
						prev.tAve[idx] = values[12].trim();
						prev.tAveCnt[idx] = Integer.parseInt(values[13].trim());
						prev.tSigma[idx] = values[14].trim();
						prev.tSigmaCnt[idx] = Integer.parseInt(values[15].trim());
					}
				} catch (Exception e) {
					String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							messageCd, e,
							"対象フィールド: " + field.getName());
				}
			}
		}
	}

	/**
	 * 最小値比較設定
	 */
	private String[] setMin(BookDataEntity filter, String[] minList, Integer[] cntList, String ha) {
		final String METHOD_NAME = "setMin";
		Field[] allFields = BookDataEntity.class.getDeclaredFields();
		String fillChar = "";
		for (int i = this.bmM023M024M026InitBean.getStartIdx(); i <= this.bmM023M024M026InitBean.getEndIdx(); i++) {
			int idx = i - this.bmM023M024M026InitBean.getStartIdx();
			if (isOtherSide(ha, idx)) {
				continue;
			}
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
				if (currentCompNumeric != null && minCompNumeric != null &&
						Double.parseDouble(currentCompNumeric) < Double.parseDouble(minCompNumeric)) {
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
	private String[] setTimeMin(BookDataEntity filter, String[] minList, Integer[] cntList, String ha) {
		final String METHOD_NAME = "setTimeMin";
		String fillChar = "";
		for (int i = this.bmM023M024M026InitBean.getStartScoreInsertIdx(); i <= this.bmM023M024M026InitBean
				.getEndScoreInsertIdx(); i++) {
			int idx = i - this.bmM023M024M026InitBean.getStartScoreInsertIdx();
			if (isOtherSide(ha, idx)) {
				continue;
			}
			fillChar = "連番No: " + filter.getSeq();
			try {
				double minTimeTmpsValue = Double.parseDouble(removeQuote(minList[idx]));
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
	private String[] setMax(BookDataEntity filter, String[] maxList, Integer[] cntList, String ha) {
		final String METHOD_NAME = "setMax";
		Field[] allFields = BookDataEntity.class.getDeclaredFields();
		String fillChar = "";
		for (int i = this.bmM023M024M026InitBean.getStartIdx(); i <= this.bmM023M024M026InitBean.getEndIdx(); i++) {
			int idx = i - this.bmM023M024M026InitBean.getStartIdx();
			if (isOtherSide(ha, idx)) {
				continue;
			}
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
				if (currentCompNumeric != null && maxCompNumeric != null &&
						Double.parseDouble(currentCompNumeric) > Double.parseDouble(maxCompNumeric)) {
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
	private String[] setTimeMax(BookDataEntity filter, String[] maxList, Integer[] cntList, String ha) {
		final String METHOD_NAME = "setTimeMax";
		String fillChar = "";
		for (int i = this.bmM023M024M026InitBean.getStartScoreInsertIdx(); i <= this.bmM023M024M026InitBean
				.getEndScoreInsertIdx(); i++) {
			int idx = i - this.bmM023M024M026InitBean.getStartScoreInsertIdx();
			if (isOtherSide(ha, idx)) {
				continue;
			}
			fillChar = "連番No: " + filter.getSeq();
			try {
				double maxTimeTmpsValue = Double.parseDouble(removeQuote(maxList[idx]));
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
	private String[] setSumAve(BookDataEntity filter, String[] aveList, Integer[] cntList, String ha) {
		final String METHOD_NAME = "setSumAve";
		Field[] allFields = BookDataEntity.class.getDeclaredFields();
		String fillChar = "";
		for (int i = this.bmM023M024M026InitBean.getStartIdx(); i <= this.bmM023M024M026InitBean.getEndIdx(); i++) {
			int idx = i - this.bmM023M024M026InitBean.getStartIdx();
			if (isOtherSide(ha, idx)) {
				continue;
			}
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
				if (numericStr == null || numericStr.isBlank() || isPercentAndFractionFormat(currentValue)) {
					continue;
				}

				double numeric = Double.parseDouble(numericStr);
				double prev = 0.0;
				if (aveList[idx] != null && !aveList[idx].isBlank()) {
					prev = Double.parseDouble(aveList[idx]);
				}
				aveList[idx] = String.valueOf(prev + numeric);
				cntList[idx]++;
			} catch (NumberFormatException e) {
				String messageCd = MessageCdConst.MCD00015E_NUMBERFORMAT_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
						e, fillChar);
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
	private String[] setTimeSumAve(BookDataEntity filter, String[] aveList, Integer[] cntList, String ha) {
		final String METHOD_NAME = "setTimeSumAve";
		String fillChar = "";
		for (int i = this.bmM023M024M026InitBean.getStartScoreInsertIdx(); i <= this.bmM023M024M026InitBean
				.getEndScoreInsertIdx(); i++) {
			int idx = i - this.bmM023M024M026InitBean.getStartScoreInsertIdx();
			if (isOtherSide(ha, idx)) {
				continue;
			}
			fillChar = "連番No: " + filter.getSeq();
			try {
				double aveTimeTmpsValue = Double.parseDouble(removeQuote(aveList[idx]));
				double currentTimeValue = ExecuteMainUtil.convertToMinutes(filter.getTime());
				aveList[idx] = String.valueOf(aveTimeTmpsValue + currentTimeValue) + "'";
				cntList[idx]++;
			} catch (NumberFormatException e) {
				String messageCd = MessageCdConst.MCD00015E_NUMBERFORMAT_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
						e, fillChar);
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
	private String[] setSumSigma(BookDataEntity filter, String[] avgList, String[] sigmaList, Integer[] cntList,
			String ha) {
		final String METHOD_NAME = "setSumSigma";
		Field[] allFields = BookDataEntity.class.getDeclaredFields();
		String fillChar = "";
		for (int i = this.bmM023M024M026InitBean.getStartIdx(); i <= this.bmM023M024M026InitBean.getEndIdx(); i++) {
			int idx = i - this.bmM023M024M026InitBean.getStartIdx();
			if (isOtherSide(ha, idx)) {
				continue;
			}
			Field field = allFields[i];
			field.setAccessible(true);
			fillChar = "フィールド名: " + field.getName() + ", 連番No: " + filter.getSeq();
			try {
				String currentValue = (String) field.get(filter);
				fillChar += " , 値: " + currentValue;
				String avgStr = avgList[idx];
				if (currentValue == null || currentValue.isBlank() || isPercentAndFractionFormat(currentValue)) {
					continue;
				}
				if (avgStr == null || avgStr.isBlank()) {
					continue;
				}

				// 【修正】数値変換できない値はスキップ（NullPointerException回避）
				String currentNumeric = parseStatValue(currentValue);
				if (currentNumeric == null || currentNumeric.isBlank()) {
					continue;
				}

				double value = Double.parseDouble(currentNumeric);
				double avg = Double.parseDouble(avgStr);
				double diffSquared = Math.pow(value - avg, 2);

				double prev = 0.0;
				if (sigmaList[idx] != null && !sigmaList[idx].isBlank()) {
					prev = Double.parseDouble(sigmaList[idx]);
				}
				sigmaList[idx] = String.valueOf(prev + diffSquared);
				cntList[idx]++;
			} catch (NumberFormatException e) {
				String messageCd = MessageCdConst.MCD00015E_NUMBERFORMAT_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
						e, fillChar);
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
	private String[] setTimeSumSigma(BookDataEntity filter, String[] aveList, String[] sigmaList, Integer[] cntList,
			String ha) {
		final String METHOD_NAME = "setTimeSumSigma";
		String fillChar = "連番No: " + filter.getSeq();
		try {
			double currentTimeValue = ExecuteMainUtil.convertToMinutes(filter.getTime());

			for (int i = this.bmM023M024M026InitBean.getStartScoreInsertIdx(); i <= this.bmM023M024M026InitBean
					.getEndScoreInsertIdx(); i++) {
				int idx = i - this.bmM023M024M026InitBean.getStartScoreInsertIdx();
				if (isOtherSide(ha, idx)) {
					continue;
				}
				String aveStr = aveList[idx];
				String sigmaStr = sigmaList[idx];
				if (aveStr == null || aveStr.isBlank()) {
					continue;
				}
				double averageValue = Double.parseDouble(removeQuote(aveStr));
				double sigmaValue = 0.0;
				if (sigmaStr != null && !sigmaStr.isBlank()) {
					sigmaValue = Double.parseDouble(removeQuote(sigmaStr));
				}
				double diffSquared = Math.pow(currentTimeValue - averageValue, 2);
				sigmaList[idx] = String.valueOf(sigmaValue + diffSquared) + "'";
				cntList[idx]++;
			}
		} catch (NumberFormatException e) {
			String messageCd = MessageCdConst.MCD00015E_NUMBERFORMAT_ERROR;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					e, fillChar);
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
	private String[] setSkewness(StatEncryptionEntity entity,
			String[] skewnessList, String[] aveList,
			String[] sigmaList, Integer[] cntList, String ha) {
		final String METHOD_NAME = "setSkewness";
		Double[] skewness = new Double[AverageStatisticsSituationConst.COUNTER];
		for (int i = 0; i < skewness.length; i++) {
			skewness[i] = 0.0;
		}
		Field[] allFields = StatEncryptionEntity.class.getDeclaredFields();
		String fillChar = "";
		for (int i = this.bmM030StatEncryptionBean.getStartEncryptionIdx(); i <= this.bmM030StatEncryptionBean
				.getEndEncryptionIdx(); i++) {
			int idx = i - this.bmM030StatEncryptionBean.getStartEncryptionIdx();
			if (idx >= skewness.length || isOtherSide(ha, idx)) {
				continue;
			}
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
				String skewSigma = (cnt <= 1) ? ""
						: String.valueOf(Math.sqrt(Double.parseDouble(skewSumSigma) / (cnt - 1)));

				if ("".equals(skewAve) || "".equals(skewSigma)) {
					continue;
				}

				// 【修正】標準偏差0の場合は0除算になるためスキップ
				double sigma = Double.parseDouble(skewSigma);
				if (sigma == 0.0 || !Double.isFinite(sigma)) {
					continue;
				}
				double mean = Double.parseDouble(skewAve);

				for (String skew : skewList) {
					String currentSkewnessNumeric = parseStatValue(skew);
					if (currentSkewnessNumeric == null) {
						continue;
					}
					skewness[idx] += Math.pow((Double.parseDouble(currentSkewnessNumeric) - mean) / sigma, 3);
				}
				cntList[idx] = cnt;
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			}
		}
		for (int i = 0; i < skewness.length; i++) {
			if (isOtherSide(ha, i)) {
				continue;
			}
			int cnt = toCnt(cntList[i]);
			double skew = skewness[i];
			if (cnt >= 3 && Double.isFinite(skew)) {
				double result = (cnt / ((cnt - 1.0) * (cnt - 2.0))) * skew;
				skewnessList[i] = String.format("%.3f", result);
			} else {
				skewnessList[i] = "";
			}
		}
		return skewnessList;
	}

	/**
	 * 尖度設定
	 */
	private String[] setKurtosis(StatEncryptionEntity entity,
			String[] kurtosisList, String[] aveList,
			String[] sigmaList, Integer[] cntList, String ha) {
		final String METHOD_NAME = "setKurtosis";
		Double[] kurtosis = new Double[AverageStatisticsSituationConst.COUNTER];
		for (int i = 0; i < kurtosis.length; i++) {
			kurtosis[i] = 0.0;
		}
		Field[] allFields = StatEncryptionEntity.class.getDeclaredFields();
		String fillChar = "";
		for (int i = this.bmM030StatEncryptionBean.getStartEncryptionIdx(); i <= this.bmM030StatEncryptionBean
				.getEndEncryptionIdx(); i++) {
			int idx = i - this.bmM030StatEncryptionBean.getStartEncryptionIdx();
			if (idx >= kurtosis.length || isOtherSide(ha, idx)) {
				continue;
			}
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
				String kurtSigma = (cnt <= 1) ? ""
						: String.valueOf(Math.sqrt(Double.parseDouble(kurtSumSigma) / (cnt - 1)));

				if ("".equals(kurtAve) || "".equals(kurtSigma)) {
					continue;
				}

				// 【修正】標準偏差0の場合は0除算になるためスキップ
				double sigma = Double.parseDouble(kurtSigma);
				if (sigma == 0.0 || !Double.isFinite(sigma)) {
					continue;
				}
				double mean = Double.parseDouble(kurtAve);

				for (String kurt : kurtList) {
					String currentKurtosisNumeric = parseStatValue(kurt);
					if (currentKurtosisNumeric == null) {
						continue;
					}
					kurtosis[idx] += Math.pow((Double.parseDouble(currentKurtosisNumeric) - mean) / sigma, 4);
				}
				cntList[idx] = cnt;
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			}
		}

		for (int i = 0; i < kurtosis.length; i++) {
			if (isOtherSide(ha, i)) {
				continue;
			}

			int cnt = toCnt(cntList[i]);
			double kurt = kurtosis[i];
			double result;

			if (cnt >= 4 && Double.isFinite(kurt)) {
				double a = (cnt * (cnt + 1.0)) / ((cnt - 1.0) * (cnt - 2.0) * (cnt - 3.0));
				double b = (3.0 * Math.pow(cnt - 1.0, 2.0)) / ((cnt - 2.0) * (cnt - 3.0));
				result = a * kurt - b;
				kurtosisList[i] = String.format("%.3f", result);
			} else if (cnt > 0 && Double.isFinite(kurt)) {
				double moment = kurt / cnt;
				result = moment - 3.0;
				kurtosisList[i] = String.format("%.3f", result);
			} else {
				kurtosisList[i] = "";
			}
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
			String messageCd = MessageCdConst.MCD00016E_FORMAT_ERROR;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, ex, feature_name);
		}
	}

	/**
	 * 共通割り算リスト
	 * 【修正】終端を「<=」相当（全項目）に変更、null安全化
	 */
	private String[] commonDivision(String[] list, Integer[] cntList, String suffix, String ha) {
		int size = this.bmM023M024M026InitBean.getEndScoreInsertIdx()
				- this.bmM023M024M026InitBean.getStartScoreInsertIdx() + 1;
		for (int i = 0; i < size && i < list.length; i++) {
			if (list[i] == null) {
				list[i] = "0" + suffix;
				continue;
			}
			if (isPercentAndFractionFormat(list[i])) {
				list[i] = "";
			} else {
				if (toCnt(cntList[i]) == 0) {
					list[i] = "0" + suffix;
				} else {
					list[i] = String.valueOf(Double.parseDouble(list[i].replace(suffix, "")) / cntList[i]) + suffix;
				}
			}
		}
		return list;
	}

	/**
	 * insertStr の値を EachTeamScoreBasedFeatureEntity に反映する
	 * @param entity 対象の EachTeamScoreBasedFeatureEntity
	 * @param insertStr カンマ区切りの統計値
	 * @param ind インデックス
	 */
	private EachTeamScoreBasedFeatureEntity setStatValuesToEntity(EachTeamScoreBasedFeatureEntity entity,
			String insertStr, int ind) {
		final String METHOD_NAME = "setStatValuesToEntity";
		try {
			Field[] allFields = EachTeamScoreBasedFeatureEntity.class.getDeclaredFields();
			Field field = allFields[ind];
			field.setAccessible(true);
			field.set(entity, insertStr);
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
			String fillChar = "EachTeamScoreBasedFeatureEntity への値設定エラー";
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
	private EachTeamScoreBasedFeatureEntity setOtherEntity(String score, String situation,
			String country, String league, String team, Boolean updFlg, String id,
			EachTeamScoreBasedFeatureEntity entity) {
		entity.setId(id);
		entity.setUpd(updFlg);
		entity.setScore(score);
		entity.setSituation(situation);
		entity.setCountry(country);
		entity.setLeague(league);
		entity.setTeam(team);
		return entity;
	}

	/**
	 * ビルドメソッド
	 */
	private StatEncryptionEntity buildBmM30Form(final List<BookDataEntity> entities,
			String country, String league, String ha, String chkBody,
			Map<String, Function<BookDataEntity, String>> fieldMap) {

		final String METHOD_NAME = "buildBmM30Form";
		StatEncryptionEntity result = new StatEncryptionEntity();
		String prefix = "H".equals(ha) ? "home" : "away";

		for (Map.Entry<String, Function<BookDataEntity, String>> entry : fieldMap.entrySet()) {
			String fieldName = entry.getKey();
			Function<BookDataEntity, String> getter = entry.getValue();

			if (!fieldName.startsWith(prefix)) {
				continue;
			}

			java.util.StringJoiner joiner = new java.util.StringJoiner(",");
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
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, ex, fieldName);
			}
		}

		result.setCountry(country);
		result.setLeague(league);
		result.setChkBody(chkBody);
		return result;
	}

	/**
	 * 存在データと元データをマージする
	 */
	private StatEncryptionEntity mergeStatEncryptionEntity(StatEncryptionEntity target,
			StatEncryptionEntity source, String ha) {

		final String METHOD_NAME = "mergeStatEncryptionEntity";
		Field[] fields = StatEncryptionEntity.class.getDeclaredFields();
		String prefix = "H".equals(ha) ? "home" : "away";

		int i = 0;
		for (Field field : fields) {
			String fieldName = field.getName();
			if (!fieldName.startsWith(prefix) || i < 9) {
				i++;
				continue;
			}

			try {
				field.setAccessible(true);
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
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fieldName);
			}

			i++;
		}
		return target;
	}

	/**
	 * 暗号化
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
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					e,
					"StatEncryptionEntityの暗号化に失敗しました");
			this.manageLoggerComponent.createSystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					null, null);
		}
		return encryptedEntity;
	}

	/**
	 * key から固定本数ロックを引く
	 */
	private Object getLock(String key) {
		int h = (key == null) ? 0 : key.hashCode();
		int idx = (h & 0x7fffffff) % LOCK_STRIPES;
		return locks[idx];
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
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
						e, numericStr);
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
	private ScoreBasedFeatureOutputDTO setSkewnessOrKurtosisSumSigma(String[] skewOrKurtList, String skewOrKurtAve,
			Integer cnt) {
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
				numeric = Math.pow((numeric - ave), 2);
				sum += numeric;
				cnt++;
			} catch (NumberFormatException e) {
				String messageCd = MessageCdConst.MCD00015E_NUMBERFORMAT_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
						e, numericStr);
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

	/** 区切り文字で 2 分割 */
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

	private static String safe(String s) {
		return (s == null) ? "" : s;
	}

	private static int sizeOf(List<?> list) {
		return (list == null) ? 0 : list.size();
	}

	private static String summarizeEnc(StatEncryptionEntity e) {
		if (e == null) {
			return "null";
		}
		return String.format("id=%s, updFlg=%s, country=%s, league=%s, team=%s, chkBody=%s",
				safe(e.getId()),
				e.isUpdFlg(),
				safe(e.getCountry()),
				safe(e.getLeague()),
				safe(e.getTeam()),
				safe(e.getChkBody()));
	}

	private static int safeParseInt(String value) {
		if (value == null || value.isBlank()) {
			return 0;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (Exception e) {
			return 0;
		}
	}

	/** 【追加】クォート除去 */
	private static String removeQuote(String value) {
		return value == null ? null : value.replace("'", "").trim();
	}

	/** 【追加】double変換（クォート・%を除去） */
	private static double safeParseDouble(String value, double defaultValue) {
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		try {
			return Double.parseDouble(removeQuote(value).replace("%", "").trim());
		} catch (Exception e) {
			return defaultValue;
		}
	}
}
