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

import dev.application.analyze.bm_m023.AverageStatisticsSituationConst;
import dev.application.analyze.bm_m023.BmM023M024M026InitBean;
import dev.application.analyze.bm_m023.ScoreBasedFeatureOutputDTO;
import dev.application.analyze.bm_m023.StatFormatResolver;
import dev.application.analyze.bm_m030.BmM030StatEncryptionBean;
import dev.application.analyze.bm_m030.StatEncryptionEntity;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.bm.EachTeamScoreBasedFeatureStatsRepository;
import dev.application.domain.repository.bm.StatEncryptionRepository;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * BM_M026統計分析ロジック（手動データ投入の場合は適用対象外）
 * @author shiraishitoshio
 *
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

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M026";

	// ===== Striped Lock（固定本数ロック）=====
	private static final int LOCK_STRIPES = 2048;
	private final Object[] locks = new Object[LOCK_STRIPES];

	{
		for (int i = 0; i < LOCK_STRIPES; i++) {
			locks[i] = new Object();
		}
	}

	/** BmM023M024M026InitBeanクラス */
	@Autowired
	private BmM023M024M026InitBean bmM023M024M026InitBean;

	/** BmM030StatEncryptionBeanクラス */
	@Autowired
	private BmM030StatEncryptionBean bmM030StatEncryptionBean;

	/** EachTeamScoreBasedFeatureStatsRepositoryレポジトリクラス */
	@Autowired
	private EachTeamScoreBasedFeatureStatsRepository eachTeamScoreBasedFeatureStatsRepository;

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
	 * @throws Exception
	 */
	@Override
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
		int encInsertCount = 0;
		int encUpdateCount = 0;

		try {
			// ★ 実行単位で共有一時Mapをクリア
			this.bmM030StatEncryptionBean.resetForRun();

			if (entities == null || entities.isEmpty()) {
				log.info("[BM_M026] calcStat skip. entities empty");
				return;
			}

			log.info("[BM_M026] calcStat start. leagueCount={}", totalLeagueCount);

			// 全リーグ・国を走査
			for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
				leagueIndex++;

				String[] data = safeLeague(entry.getKey());
				String country = data[0];
				String league = data[1];

				// どちらかが空なら、このグループはスキップ
				if (country.isBlank() || league.isBlank()) {
					this.manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							"skip: invalid league key", null, "key=" + entry.getKey());
					log.warn("[BM_M026] league skip. invalid key={}", entry.getKey());
					continue;
				}

				log.info("[BM_M026] league start. leagueIndex={}/{}, country={}, league={}",
						leagueIndex, totalLeagueCount, country, league);

				// 互換維持（暗号鍵・index 初期化）
				this.bmM030StatEncryptionBean.init(country, league);

				// ★ 共有Mapは使わず、league単位ローカルMapに変更
				ConcurrentHashMap<String, StatEncryptionEntity> bmM30Map = new ConcurrentHashMap<>();

				Map<String, List<BookDataEntity>> entrySub = entry.getValue();
				if (entrySub == null || entrySub.isEmpty()) {
					log.info("[BM_M026] league skip. inner map empty. country={}, league={}", country, league);
					continue;
				}

				int leagueSavedCount = 0;
				int matchIndex = 0;
				int totalMatchCount = entrySub.size();

				// team単位の統計作成・保存
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

					// 統計テーブル登録・更新
					for (Map.Entry<String, List<EachTeamScoreBasedFeatureEntity>> entrys : resultMap.entrySet()) {
						List<EachTeamScoreBasedFeatureEntity> vals = entrys.getValue();
						if (vals == null || vals.isEmpty()) {
							continue;
						}
						for (EachTeamScoreBasedFeatureEntity subSubEntity : vals) {
							if (subSubEntity == null) {
								continue;
							}
							save(subSubEntity);
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

				// 暗号化保存テーブル登録・更新（このleague分）
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

					StatEncryptionEntity newEntrys = encryption(e);

					log.info("[BM_M026] after encryption. encIndex={}/{}, key={}, updFlg={}, id={}",
							encIndex, encTotal, encKey, newEntrys.isUpdFlg(), safe(newEntrys.getId()));

					boolean shouldUpdate = newEntrys.isUpdFlg()
							&& newEntrys.getId() != null
							&& !newEntrys.getId().isBlank();

					if (shouldUpdate) {
						log.info("[BM_M026] before stat_encryption update. encIndex={}/{}, key={}, id={}",
								encIndex, encTotal, encKey, newEntrys.getId());

						int result = this.statEncryptionRepository.updateEncValues(newEntrys);

						log.info("[BM_M026] after stat_encryption update. encIndex={}/{}, key={}, result={}",
								encIndex, encTotal, encKey, result);

						if (result != 1) {
							String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
							this.rootCauseWrapper.throwUnexpectedRowCount(
									PROJECT_NAME, CLASS_NAME, METHOD_NAME,
									messageCd,
									1, result,
									String.format("id=%s, team=%s, country=%s, league=%s, chkBody=%s",
											newEntrys.getId(),
											e.getTeam(),
											e.getCountry(),
											e.getLeague(),
											e.getChkBody()));
						}

						encUpdateCount += result;

						String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
						this.manageLoggerComponent.debugInfoLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME,
								messageCd, BM_NUMBER + " 更新件数: " + result + "件");

					} else {
						log.info("[BM_M026] before stat_encryption insert. encIndex={}/{}, key={}",
								encIndex, encTotal, encKey);

						int result = this.statEncryptionRepository.insert(newEntrys);

						log.info("[BM_M026] after stat_encryption insert. encIndex={}/{}, key={}, result={}",
								encIndex, encTotal, encKey, result);

						if (result != 1) {
							String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
							this.rootCauseWrapper.throwUnexpectedRowCount(
									PROJECT_NAME, CLASS_NAME, METHOD_NAME,
									messageCd,
									1, result,
									String.format("id=%s, team=%s, country=%s, league=%s, chkBody=%s",
											newEntrys.getId(),
											e.getTeam(),
											e.getCountry(),
											e.getLeague(),
											e.getChkBody()));
						}

						encInsertCount += result;

						String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
						this.manageLoggerComponent.debugInfoLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME,
								messageCd, BM_NUMBER + " 登録件数: " + result + "件");
					}
				}

				log.info("[BM_M026] league done. leagueIndex={}/{}, country={}, league={}, processedMatchCount={}, skippedNotFinCount={}, savedEntityCount={}, leagueSavedCount={}, encInsertCount={}, encUpdateCount={}, bmM30MapSize={}",
						leagueIndex, totalLeagueCount, country, league,
						processedMatchCount, skippedNotFinCount, savedEntityCount, leagueSavedCount,
						encInsertCount, encUpdateCount, bmM30Map.size());
			}

			log.info("[BM_M026] calcStat finished. leagueCount={}, processedMatchCount={}, skippedNotFinCount={}, savedEntityCount={}, encInsertCount={}, encUpdateCount={}, elapsedMs={}",
					totalLeagueCount, processedMatchCount, skippedNotFinCount,
					savedEntityCount, encInsertCount, encUpdateCount,
					(System.currentTimeMillis() - startedAt));

		} catch (Exception e) {
			log.error("[BM_M026] calcStat failed. processedMatchCount={}, skippedNotFinCount={}, savedEntityCount={}, encInsertCount={}, encUpdateCount={}",
					processedMatchCount, skippedNotFinCount, savedEntityCount, encInsertCount, encUpdateCount, e);
			throw e;
		} finally {
			// endLog
			this.manageLoggerComponent.debugEndInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();
		}
	}

	/**
	 * 処理メインロジック
	 * @param entities エンティティ
	 * @param country 国
	 * @param league リーグ
	 * @param bmM30Map 保存データマップ
	 * @return
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

		// 空ならskip
		if (filteredList == null || filteredList.isEmpty()) {
			log.info("[BM_M026] basedEntities skip. filtered empty. country={}, league={}, team={}, ha={}, flg={}, connectScore={}",
					country, league, team, ha, flg, safe(connectScore));
			return;
		}

		// chkBody決定（ALL/FIRST/SECONDは flg、EACH_SCOREは connectScore）
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
				statList, ha);

		BookDataEntity returnDataEntity = ExecuteMainUtil.getMaxSeqEntities(entities);
		initFormat(returnDataEntity, minList, "Min");
		initFormat(returnDataEntity, maxList, "Max");

		for (BookDataEntity filter : filteredList) {
			minList = setMin(filter, minList, minCntList, ha);
			maxList = setMax(filter, maxList, maxCntList, ha);
			aveList = setSumAve(filter, aveList, aveCntList, ha);

			tMinList = setTimeMin(filter, tMinList, tMinCntList, ha);
			tMaxList = setTimeMax(filter, tMaxList, tMaxCntList, ha);
			tAveList = setTimeSumAve(filter, tAveList, tAveCntList, ha);
		}

		aveList = commonDivision(aveList, aveCntList, "", ha);
		tAveList = commonDivision(tAveList, tAveCntList, "'", ha);

		for (BookDataEntity filter : filteredList) {
			sigmaList = setSumSigma(filter, aveList, sigmaList, sigmaCntList, ha);
			tSigmaList = setTimeSumSigma(filter, tAveList, tSigmaList, tSigmaCntList, ha);
		}

		sigmaList = commonDivision(sigmaList, sigmaCntList, "", ha);
		tSigmaList = commonDivision(tSigmaList, tSigmaCntList, "'", ha);

		for (int i = 0; i < sigmaList.length; i++) {
			if (("H".equals(ha) && i % 2 == 1) || ("A".equals(ha) && i % 2 == 0)) {
				continue;
			}
			sigmaList[i] = String.format("%.2f", Math.sqrt(Double.parseDouble(sigmaList[i])));
			tSigmaList[i] = String.format("%.2f", Math.sqrt(Double.parseDouble(tSigmaList[i].replace("'", ""))));
		}

		String[] aveSkewKurtList = this.bmM023M024M026InitBean.getAvgList().clone();
		String[] sigmaSkewKurtList = this.bmM023M024M026InitBean.getSigmaList().clone();
		String[] skewnessList = this.bmM023M024M026InitBean.getSkewnessList().clone();
		String[] kurtosisList = this.bmM023M024M026InitBean.getKurtosisList().clone();
		Integer[] kurtosisCntList = this.bmM023M024M026InitBean.getSkewnessCntList().clone();

		skewnessList = setSkewness(decidedEntity, skewnessList, aveSkewKurtList, sigmaSkewKurtList, kurtosisCntList,
				ha);
		kurtosisList = setKurtosis(decidedEntity, kurtosisList, aveSkewKurtList, sigmaSkewKurtList, kurtosisCntList,
				ha);

		EachTeamScoreBasedFeatureEntity entity = new EachTeamScoreBasedFeatureEntity();
		StringBuilder sb = new StringBuilder();

		for (int i = this.bmM023M024M026InitBean.getStartScoreInsertIdx(); i < this.bmM023M024M026InitBean
				.getEndScoreInsertIdx(); i++) {

			int idx = i - this.bmM023M024M026InitBean.getStartScoreInsertIdx();

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

			sb.append(min).append(",")
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
	 * 更新登録メソッド
	 * @param entity
	 */
	private void save(EachTeamScoreBasedFeatureEntity entity) {
		String key = String.format(
				"score=%s, country=%s, league=%s, team=%s, situation=%s",
				safe(entity.getScore()),
				safe(entity.getCountry()),
				safe(entity.getLeague()),
				safe(entity.getTeam()),
				safe(entity.getSituation()));

		try {
			log.info("[BM_M026] before each_team_score_based_feature_stats update. {}", key);

			int updated = eachTeamScoreBasedFeatureStatsRepository.updateStatValues(entity);

			log.info("[BM_M026] after each_team_score_based_feature_stats update. updated={}, {}", updated, key);

			if (updated == 1) {
				log.info("[BM_M026] each_team_score_based_feature_stats updated. {}", key);
				return;
			}

			if (updated > 1) {
				throw new IllegalStateException(
						"Unexpected update row count (expected=1, actual=" + updated + "), key=" + key);
			}

			// updated == 0
			log.warn("[BM_M026] no row updated. try insert. {}", key);

			try {
				log.info("[BM_M026] before each_team_score_based_feature_stats insert. {}", key);

				int inserted = eachTeamScoreBasedFeatureStatsRepository.insert(entity);

				log.info("[BM_M026] after each_team_score_based_feature_stats insert. inserted={}, {}", inserted, key);

				if (inserted == 1) {
					log.info("[BM_M026] each_team_score_based_feature_stats inserted. {}", key);
					return;
				}

				throw new IllegalStateException(
						"Unexpected insert row count (expected=1, actual=" + inserted + "), key=" + key);

			} catch (org.springframework.dao.DuplicateKeyException e) {
				log.warn("[BM_M026] duplicate on insert, retry update. {}", key, e);

				int retried = eachTeamScoreBasedFeatureStatsRepository.updateStatValues(entity);
				log.info("[BM_M026] after retry update. retried={}, {}", retried, key);

				if (retried == 1) {
					log.info("[BM_M026] each_team_score_based_feature_stats updated after duplicate. {}", key);
					return;
				}

				throw new IllegalStateException(
						"Upsert failed after duplicate retry (expected=1, actual=" + retried + "), key=" + key, e);
			}

		} catch (RuntimeException e) {
			log.error("[BM_M026] save failed. key={}, entity={}", key, entity, e);
			throw e;
		}
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
	 * @param ha
	 */
	private void setInitData(String[] minList, Integer[] minCntList, String[] maxList, Integer[] maxCntList,
			String[] aveList, Integer[] aveCntList, String[] sigmaList, Integer[] sigmaCntList,
			String[] tMinList, Integer[] tMinCntList, String[] tMaxList, Integer[] tMaxCntList,
			String[] tAveList, Integer[] tAveCntList, String[] tSigmaList, Integer[] tSigmaCntList,
			List<EachTeamScoreBasedFeatureEntity> list, String ha) {
		final String METHOD_NAME = "setInitData";
		if (list != null && !list.isEmpty()) {
			EachTeamScoreBasedFeatureEntity statEntity = list.get(0);
			Field[] fields = EachTeamScoreBasedFeatureEntity.class.getDeclaredFields();
			for (int i = this.bmM023M024M026InitBean.getStartScoreInsertIdx(); i < this.bmM023M024M026InitBean
					.getEndScoreInsertIdx(); i++) {
				int idx = i - this.bmM023M024M026InitBean.getStartScoreInsertIdx();
				if (("H".equals(ha) && idx % 2 == 1) || ("A".equals(ha) && idx % 2 == 0)) {
					continue;
				}
				Field field = fields[i];
				field.setAccessible(true);
				try {
					String statValue = (String) field.get(statEntity);
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
	 * @param filteredList
	 * @param minList
	 * @param cntList
	 * @param ha
	 */
	private String[] setMin(BookDataEntity filter, String[] minList, Integer[] cntList, String ha) {
		final String METHOD_NAME = "setMin";
		Field[] allFields = BookDataEntity.class.getDeclaredFields();
		String fillChar = "";
		for (int i = this.bmM023M024M026InitBean.getStartIdx(); i <= this.bmM023M024M026InitBean.getEndIdx(); i++) {
			int idx = i - this.bmM023M024M026InitBean.getStartIdx();
			if (("H".equals(ha) && idx % 2 == 1) || ("A".equals(ha) && idx % 2 == 0)) {
				continue;
			}
			Field field = allFields[i];
			field.setAccessible(true);
			fillChar = "フィールド名: " + field.getName() + ", 連番No: " + filter.getSeq();
			try {
				String currentValue = (String) field.get(filter);
				fillChar += " , 値: " + currentValue;
				if (currentValue == null || currentValue.isBlank())
					continue;

				String minValue = minList[idx];
				if (!isSameFormat(minValue, currentValue))
					continue;

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
	 * @param filteredList
	 * @param minList
	 * @param cntList
	 * @param ha
	 */
	private String[] setTimeMin(BookDataEntity filter, String[] minList, Integer[] cntList, String ha) {
		final String METHOD_NAME = "setTimeMin";
		String fillChar = "";
		for (int i = this.bmM023M024M026InitBean.getStartScoreInsertIdx(); i < this.bmM023M024M026InitBean
				.getEndScoreInsertIdx(); i++) {
			int idx = i - this.bmM023M024M026InitBean.getStartScoreInsertIdx();
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
	 * @param filteredList
	 * @param maxList
	 * @param cntList
	 * @param ha
	 */
	private String[] setMax(BookDataEntity filter, String[] maxList, Integer[] cntList, String ha) {
		final String METHOD_NAME = "setMax";
		Field[] allFields = BookDataEntity.class.getDeclaredFields();
		String fillChar = "";
		for (int i = this.bmM023M024M026InitBean.getStartIdx(); i <= this.bmM023M024M026InitBean.getEndIdx(); i++) {
			int idx = i - this.bmM023M024M026InitBean.getStartIdx();
			if (("H".equals(ha) && idx % 2 == 1) || ("A".equals(ha) && idx % 2 == 0)) {
				continue;
			}
			Field field = allFields[i];
			field.setAccessible(true);
			fillChar = "フィールド名: " + field.getName() + ", 連番No: " + filter.getSeq();
			try {
				String currentValue = (String) field.get(filter);
				fillChar += " , 値: " + currentValue;
				if (currentValue == null || currentValue.isBlank())
					continue;

				String maxValue = maxList[idx];
				if (!isSameFormat(maxValue, currentValue))
					continue;

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
	 * @param filteredList
	 * @param minList
	 * @param cntList
	 * @param ha
	 */
	private String[] setTimeMax(BookDataEntity filter, String[] maxList, Integer[] cntList, String ha) {
		final String METHOD_NAME = "setTimeMax";
		String fillChar = "";
		for (int i = this.bmM023M024M026InitBean.getStartScoreInsertIdx(); i < this.bmM023M024M026InitBean
				.getEndScoreInsertIdx(); i++) {
			int idx = i - this.bmM023M024M026InitBean.getStartScoreInsertIdx();
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
		for (int i = this.bmM023M024M026InitBean.getStartIdx(); i <= this.bmM023M024M026InitBean.getEndIdx(); i++) {
			int idx = i - this.bmM023M024M026InitBean.getStartIdx();
			if (("H".equals(ha) && idx % 2 == 1) || ("A".equals(ha) && idx % 2 == 0)) {
				continue;
			}
			Field field = allFields[i];
			field.setAccessible(true);
			fillChar = "フィールド名: " + field.getName() + ", 連番No: " + filter.getSeq();
			try {
				String currentValue = (String) field.get(filter);
				fillChar += " , 値: " + currentValue;
				if (currentValue == null || currentValue.isBlank())
					continue;

				String numericStr = parseStatValue(currentValue);
				if (numericStr == null || numericStr.isBlank() || isPercentAndFractionFormat(currentValue))
					continue;

				double numeric = Double.parseDouble(numericStr);
				double prev = 0.0;
				if (aveList[idx] != null && !aveList[idx].isBlank()) {
					prev = Double.parseDouble(aveList[idx]);
				}
				double sum = prev + numeric;
				aveList[idx] = String.valueOf(sum);
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
	 * @param filteredList
	 * @param aveList
	 * @param cntList
	 * @param ha
	 */
	private String[] setTimeSumAve(BookDataEntity filter, String[] aveList, Integer[] cntList, String ha) {
		final String METHOD_NAME = "setTimeSumAve";
		String fillChar = "";
		for (int i = this.bmM023M024M026InitBean.getStartScoreInsertIdx(); i < this.bmM023M024M026InitBean
				.getEndScoreInsertIdx(); i++) {
			int idx = i - this.bmM023M024M026InitBean.getStartScoreInsertIdx();
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
		for (int i = this.bmM023M024M026InitBean.getStartIdx(); i <= this.bmM023M024M026InitBean.getEndIdx(); i++) {
			int idx = i - this.bmM023M024M026InitBean.getStartIdx();
			if (("H".equals(ha) && idx % 2 == 1) || ("A".equals(ha) && idx % 2 == 0)) {
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
				if (avgStr == null || avgStr.isBlank())
					continue;

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
			double currentTimeValue = ExecuteMainUtil.convertToMinutes(filter.getTime());

			for (int i = this.bmM023M024M026InitBean.getStartScoreInsertIdx(); i < this.bmM023M024M026InitBean
					.getEndScoreInsertIdx(); i++) {
				int idx = i - this.bmM023M024M026InitBean.getStartScoreInsertIdx();
				if (("H".equals(ha) && idx % 2 == 1) || ("A".equals(ha) && idx % 2 == 0)) {
					continue;
				}
				String aveStr = aveList[idx];
				String sigmaStr = sigmaList[idx];
				if (aveStr == null || aveStr.isBlank())
					continue;
				double averageValue = Double.parseDouble(aveStr.replace("'", ""));
				double sigmaValue = 0.0;
				if (sigmaStr != null && !sigmaStr.isBlank()) {
					sigmaValue = Double.parseDouble(sigmaStr.replace("'", ""));
				}
				double diffSquared = Math.pow(currentTimeValue - averageValue, 2);
				double updated = sigmaValue + diffSquared;
				sigmaList[idx] = String.valueOf(updated) + "'";
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
	 * @param entity
	 * @param skewnessList
	 * @param aveList
	 * @param sigmaList
	 * @param cntList
	 * @param ha homeaway
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
			if (("H".equals(ha) && idx % 2 == 1) || ("A".equals(ha) && idx % 2 == 0)) {
				continue;
			}
			Field field = allFields[i];
			field.setAccessible(true);
			fillChar = "フィールド名: " + field.getName();
			try {
				String currentValue = (String) field.get(entity);
				fillChar += " , 値: " + currentValue;
				if (currentValue == null || currentValue.isBlank() || isPercentAndFractionFormat(currentValue))
					continue;

				String[] skewList = currentValue.split(",");
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
				String skewSigma = (cnt == 1) ? ""
						: String.valueOf(
								Math.sqrt(Double.parseDouble(skewSumSigma) / (cnt - 1)));
				if ("".equals(skewAve) || "".equals(skewSigma))
					continue;

				for (String skew : skewList) {
					String currentSkewnessNumeric = parseStatValue(skew);
					if (currentSkewnessNumeric == null)
						continue;
					skewness[idx] += Math.pow((Double.parseDouble(currentSkewnessNumeric)
							- Double.parseDouble(skewAve)) / Double.parseDouble(skewSigma), 3);
				}
				cntList[idx] = cnt;
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00014E_REFLECTION_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			}
		}
		for (int i = 0; i < skewness.length; i++) {
			if (("H".equals(ha) && i % 2 == 1) ||
					("A".equals(ha) && i % 2 == 0)) {
				continue;
			}
			int cnt = cntList[i];
			double skew = skewness[i];
			double result = (cnt / ((cnt - 1.0) * (cnt - 2.0))) * skew;
			skewnessList[i] = String.format("%.3f", result);
		}
		return skewnessList;
	}

	/**
	 * 尖度設定
	 * @param entity
	 * @param kurtosisList
	 * @param aveList
	 * @param sigmaList
	 * @param cntList(歪度で求めた件数を設定)
	 * @param ha homeaway
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
			if (("H".equals(ha) && idx % 2 == 1) || ("A".equals(ha) && idx % 2 == 0)) {
				continue;
			}
			Field field = allFields[i];
			field.setAccessible(true);
			fillChar = "フィールド名: " + field.getName();
			try {
				String currentValue = (String) field.get(entity);
				fillChar += " , 値: " + currentValue;
				if (currentValue == null || currentValue.isBlank() || isPercentAndFractionFormat(currentValue))
					continue;

				String[] kurtList = currentValue.split(",");
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
				String kurtSigma = (cnt == 1) ? ""
						: String.valueOf(
								Math.sqrt(Double.parseDouble(kurtSumSigma) / (cnt - 1)));
				if ("".equals(kurtAve) || "".equals(kurtSigma))
					continue;

				for (String kurt : kurtList) {
					String currentKurtosisNumeric = parseStatValue(kurt);
					if (currentKurtosisNumeric == null)
						continue;
					kurtosis[idx] += (Math.pow((Double.parseDouble(currentKurtosisNumeric)
							- Double.parseDouble(kurtAve)), 4) / Math.pow(
									Double.parseDouble(kurtSigma), 4));
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
	 * @param list
	 * @param cntList
	 * @param suffix
	 * @param ha
	 * @return
	 */
	private String[] commonDivision(String[] list, Integer[] cntList, String suffix, String ha) {
		for (int i = 0; i < this.bmM023M024M026InitBean.getEndScoreInsertIdx()
				- this.bmM023M024M026InitBean.getStartScoreInsertIdx(); i++) {
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
	 * @param score スコア
	 * @param situation 状況
	 * @param country 国
	 * @param league リーグ
	 * @param team チーム
	 * @param updFlg 更新フラグ
	 * @return
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
	 * @param entities
	 * @param country
	*/
	private StatEncryptionEntity buildBmM30Form(final List<BookDataEntity> entities,
			String country
			, String league, String ha, String chkBody,
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

			// joining をやめて逐次追加（巨大な中間Stringを作りにくくする）
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
	 * @param target 元データ
	 * @param source 追加データ
	 * @param ha Home or Away
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
	 * @param entity
	 * @return
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
	 * key から固定本数ロックを引く（Mapに溜めないので肥大化しない）
	 */
	private Object getLock(String key) {
		int h = (key == null) ? 0 : key.hashCode();
		int idx = (h & 0x7fffffff) % LOCK_STRIPES;
		return locks[idx];
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
			String numericStr = parseStatValue(currentValue);
			if (numericStr == null || numericStr.isBlank() || isPercentAndFractionFormat(currentValue))
				continue;

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
		String skewOrKurtSumAve = String.valueOf(sum);
		scoreBasedFeatureOutputDTO.setCnt(String.valueOf(cnt));
		scoreBasedFeatureOutputDTO.setAve(skewOrKurtSumAve);
		return scoreBasedFeatureOutputDTO;
	}

	/**
	 * 歪度or尖度標準偏差導出のための加算処理（値を加算し、件数もインクリメント）
	 * @param skewOrKurtList 加算リスト（String型）
	 * @param skewOrKurtAve 平均値
	 * @param cnt 件数カウント（Integer型）
	 * @return 加算後のsigma
	 */
	private ScoreBasedFeatureOutputDTO setSkewnessOrKurtosisSumSigma(String[] skewOrKurtList, String skewOrKurtAve,
			Integer cnt) {
		ScoreBasedFeatureOutputDTO scoreBasedFeatureOutputDTO = new ScoreBasedFeatureOutputDTO();
		final String METHOD_NAME = "setSkewnessOrKurtosisSumSigma";
		double sum = 0.0;
		for (int i = 0; i < skewOrKurtList.length; i++) {
			String currentValue = skewOrKurtList[i];
			String numericStr = parseStatValue(currentValue);
			if (numericStr == null || numericStr.isBlank() || isPercentAndFractionFormat(currentValue))
				continue;

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
		String skewOrKurtSumSigma = String.valueOf(sum);
		scoreBasedFeatureOutputDTO.setCnt(String.valueOf(cnt));
		scoreBasedFeatureOutputDTO.setSigma(skewOrKurtSumSigma);
		return scoreBasedFeatureOutputDTO;
	}

	/** 区切り文字で 2 分割（必ず長さ2を返す: [left, right]）。区切りが無ければ right は空文字。 */
	private static String[] split2(String s, String sep) {
		if (s == null)
			return new String[] { "", "" };
		int i = s.indexOf(sep);
		if (i < 0)
			return new String[] { s, "" };
		return new String[] { s.substring(0, i), s.substring(i + sep.length()) };
	}

	/** 「国,リーグ」を安全に分割（null/形式不正でも長さ2を返す） */
	private static String[] safeLeague(String key) {
		try {
			String[] a = ExecuteMainUtil.splitLeagueInfo(key);
			if (a != null && a.length >= 2)
				return new String[] { a[0], a[1] };
			if (a != null && a.length == 1)
				return new String[] { a[0], "" };
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
}
