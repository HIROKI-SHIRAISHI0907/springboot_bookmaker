package dev.application.analyze.bm_m097;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.AnalyzeManualRepository;
import dev.application.domain.repository.bm.BookDataRepository;
import dev.application.domain.repository.bm.CsvDetailManageRepository;
import dev.application.domain.repository.master.CountryLeagueSeasonMasterRepository;
import dev.application.domain.repository.master.FutureMasterRepository;
import dev.application.main.service.CoreStat;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.entity.DataEntity;
import dev.common.entity.FutureEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.getinfo.GetStatInfo;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M097統計分析ロジック（手動データ統計適用ロジック）
 * @author shiraishitoshio
 *
 */
@Component
public class AnalyzeManualStat {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = AnalyzeManualStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = AnalyzeManualStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M097_ANALYZE_MANUAL";

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M097";

	/** 1回のIN句件数（DB負荷対策） */
	private static final int MATCH_ID_BATCH_SIZE = 500;

	/** JST */
	private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

	@Autowired
	private BookDataRepository bookDataRepository;

	@Autowired
	private CsvDetailManageRepository csvDetailManageRepository;

	@Autowired
	private FutureMasterRepository futureMasterRepository;

	@Autowired
	private AnalyzeManualRepository analyzeManualDataRepository;

	@Autowired
	private CountryLeagueSeasonMasterRepository countryLeagueSeasonMasterRepository;

	@Autowired
	private CoreStat coreStat;

	@Autowired
	private GetStatInfo getStatInfo;

	/** ログ管理ラッパー */
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 登録ロジック
	 * @return
	 */
	@Transactional(rollbackFor = Exception.class)
	public int manualStat() {
		final String METHOD_NAME = "manualStat";

		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 1) 手動データ取得
		List<DataEntity> finList = bookDataRepository.getFinData();
		if (finList == null || finList.isEmpty()) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00004I_OTHER_EXECUTION_GREEN_FIN,
					null,
					"finList not found");
			return 0;
		}

		// 2) future_master から比較用の試合キーを取得
		Map<String, FutureEntity> futureTargetMap = futureMasterRepository.findFutureDatesForManualStat().stream()
				.filter(e -> e != null)
				.filter(e -> !nvl(e.getHomeTeamName()).isEmpty())
				.filter(e -> !nvl(e.getAwayTeamName()).isEmpty())
				.filter(e -> !toTokyoDateString(e.getFutureTime()).isEmpty())
				.collect(Collectors.toMap(
						this::buildFutureMasterKey,
						Function.identity(),
						(a, b) -> a,
						LinkedHashMap::new));

		if (futureTargetMap.isEmpty()) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00004I_OTHER_EXECUTION_GREEN_FIN,
					null,
					"futureTargetMap not found");
			return 0;
		}

		// 3) 終了済み or PENALTY含み かつ home/away/date が future_master と一致するものだけ対象
		List<DataEntity> finishedList = finList.stream()
				.filter(this::isFinishedTarget)
				.filter(e -> futureTargetMap.containsKey(buildDataFutureKey(e)))
				.collect(Collectors.toList());

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, null,
				"manual target by future key: futureTargetMap=" + futureTargetMap.size()
						+ ", finList=" + finList.size()
						+ ", finishedList=" + finishedList.size());

		if (finishedList.isEmpty()) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00004I_OTHER_EXECUTION_GREEN_FIN,
					null,
					"finishedList not found");
			return 0;
		}

		// 4) 同一 match_id の重複を正規化
		List<DataEntity> normalizedFinishedList = normalizeFinishedData(finishedList);

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, null,
				"manual target normalize: before=" + finishedList.size()
						+ ", after=" + normalizedFinishedList.size());

		if (normalizedFinishedList.isEmpty()) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00004I_OTHER_EXECUTION_GREEN_FIN,
					null,
					"normalizedFinishedList not found");
			return 0;
		}

		// 5) matchId をユニーク抽出
		List<String> matchIds = normalizedFinishedList.stream()
				.map(DataEntity::getMatchId)
				.map(this::nvl)
				.filter(s -> !s.isEmpty())
				.distinct()
				.collect(Collectors.toList());

		// 6) analyze_manual_data に既にあるものを一括取得
		//    match_id がある場合は match_id を優先して重複判定する
		Set<String> analyzedKeySet = new HashSet<>();

		for (int i = 0; i < matchIds.size(); i += MATCH_ID_BATCH_SIZE) {
			int end = Math.min(i + MATCH_ID_BATCH_SIZE, matchIds.size());
			List<String> batch = matchIds.subList(i, end);

			List<AnalyzeManualEntity> existingList = analyzeManualDataRepository.selectByMatchIds(batch);
			if (existingList == null || existingList.isEmpty()) {
				continue;
			}

			for (AnalyzeManualEntity existing : existingList) {
				analyzedKeySet.add(buildAnalyzeKey(
						existing.getGameCategory(),
						existing.getTimes(),
						existing.getHomeTeamName(),
						existing.getAwayTeamName(),
						existing.getMatchId()));
			}
		}

		// 7) 未反映データだけを抽出
		List<DataEntity> targetList = normalizedFinishedList.stream()
				.filter(e -> !analyzedKeySet.contains(buildAnalyzeKey(
						e.getDataCategory(),
						e.getTimes(),
						e.getHomeTeamName(),
						e.getAwayTeamName(),
						e.getMatchId())))
				.collect(Collectors.toList());

		if (targetList.isEmpty()) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00004I_OTHER_EXECUTION_GREEN_FIN,
					null,
					"targetList not found");
			return 0;
		}

		// 8) CoreStat に渡せるデータだけに絞る
		List<DataEntity> validTargetList = targetList.stream()
				.filter(this::isValidForCoreStat)
				.collect(Collectors.toList());

		if (validTargetList.isEmpty()) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00004I_OTHER_EXECUTION_GREEN_FIN,
					null,
					"validTargetList not found");
			return 0;
		}

		// 9) 以降に進むには条件がある
		// csv_detail_manage(check_fin_flg='1') に存在しないこと
		// data テーブルに存在しないこと
		List<DataEntity> analyzeTargetList = filterAnalyzeTargets(validTargetList);

		if (analyzeTargetList.isEmpty()) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00004I_OTHER_EXECUTION_GREEN_FIN,
					null,
					"analyzeTargetList not found");
			return 0;
		}

		// 10) CoreStat に渡すMapを作成して実行
		List<BookDataEntity> bookDataList = analyzeTargetList.stream()
				.map(this::toBookDataEntity)
				.collect(Collectors.toList());

		Map<String, Map<String, List<BookDataEntity>>> entities =
				getStatInfo.buildStatMapFromEntities(bookDataList);

		int processedCount;
		try {
			processedCount = coreStat.execute(entities, true);
		} catch (Exception e1) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e1, "coreStat error");
			this.manageLoggerComponent.createSystemException(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, null);
			return 0;
		}

		if (processedCount <= 0) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00004I_OTHER_EXECUTION_GREEN_FIN,
					null,
					"coreStat processedCount=0");
			return 0;
		}

		// 11) 成功した対象のみ analyze_manual_data に登録
		for (DataEntity entity : analyzeTargetList) {
			AnalyzeManualEntity manualEntity = new AnalyzeManualEntity();
			manualEntity.setGameCategory(nvl(entity.getDataCategory()));
			manualEntity.setTimes(nvl(entity.getTimes()));
			manualEntity.setHomeTeamName(nvl(entity.getHomeTeamName()));
			manualEntity.setAwayTeamName(nvl(entity.getAwayTeamName()));
			manualEntity.setMatchId(nvl(entity.getMatchId()));

			int result = analyzeManualDataRepository.insertAnalyzeManualData(manualEntity);
			if (result != 1) {
				String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, result,
						entity.getDataCategory() + ": "
								+ entity.getHomeTeamName() + "-"
								+ entity.getAwayTeamName());
			}

			String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					BM_NUMBER + " analyze_manual_data登録件数: " + result + "件 ("
							+ entity.getDataCategory() + ": "
							+ entity.getHomeTeamName() + "-"
							+ entity.getAwayTeamName() + ")");
		}

		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();

		return 0;
	}

	/**
	 * 分析対象の最終抽出
	 * csv_detail_manage(check_fin_flg='1') に存在せず、
	 * data テーブルにも存在しないものだけを残す
	 *
	 * @param validTargetList
	 * @return
	 */
	private List<DataEntity> filterAnalyzeTargets(List<DataEntity> validTargetList) {
		Map<String, String> seasonCache = new LinkedHashMap<>();
		Map<String, Boolean> csvExistsCache = new LinkedHashMap<>();
		Map<String, Boolean> dataExistsCache = new LinkedHashMap<>();

		List<DataEntity> analyzeTargetList = new ArrayList<>();

		for (DataEntity dataEntity : validTargetList) {
			// シーズン取得をキャッシュ
			String dataCategory = nvl(dataEntity.getDataCategory());
			String season = seasonCache.computeIfAbsent(
					dataCategory,
					this::resolveSeasonSafely);

			if (season.isEmpty()) {
				continue;
			}

			// csv_detail_manage 用キー
			String csvKey = buildCsvDetailKey(
					dataEntity.getDataCategory(),
					season,
					dataEntity.getHomeTeamName(),
					dataEntity.getAwayTeamName());

			boolean csvExists = csvExistsCache.computeIfAbsent(csvKey, k ->
					this.csvDetailManageRepository.existsCheckedFinCount(
							dataEntity.getDataCategory(),
							season,
							dataEntity.getHomeTeamName(),
							dataEntity.getAwayTeamName()) > 0
			);

			// data 用キー
			String dataKey = buildDataRestrictKey(
					dataEntity.getDataCategory(),
					dataEntity.getHomeTeamName(),
					dataEntity.getAwayTeamName(),
					dataEntity.getMatchId());

			boolean dataExists = dataExistsCache.computeIfAbsent(dataKey, k ->
					this.bookDataRepository.getAnalyzeManualRestrictCount(
							dataEntity.getDataCategory(),
							dataEntity.getHomeTeamName(),
							dataEntity.getAwayTeamName(),
							dataEntity.getMatchId()) > 0
			);

			// どちらも存在しない場合のみ対象
			if (!csvExists && !dataExists) {
				analyzeTargetList.add(dataEntity);
			}
		}

		return analyzeTargetList;
	}

	/**
	 * シーズン取得
	 * @param dataCategory
	 * @return
	 */
	private String resolveSeasonSafely(String dataCategory) {
		final String METHOD_NAME = "resolveSeasonSafely";

		String country = "";
		String league = "";

		try {
			List<String> dataList = ExecuteMainUtil.getCountryLeagueByRegex(dataCategory);
			if (dataList != null && dataList.size() >= 2) {
				country = safe(dataList.get(0)).trim();
				league = safe(dataList.get(1)).trim();
			}
		} catch (Exception e) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					"dataCategory から country/league 抽出失敗. dataCategory=" + dataCategory);
		}

		try {
			String season = countryLeagueSeasonMasterRepository.findCurrentSeasonYear(country, league);
			return safe(season).trim();
		} catch (Exception e) {
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					"season取得失敗. country=" + country + ", league=" + league);
			return "";
		}
	}

	/**
	 * 同一 match_id の重複を正規化する。
	 *
	 * 優先順位:
	 * 1. data_category に「ラウンド」を含む方
	 * 2. update_time が新しい方
	 * 3. seq が大きい方
	 */
	private List<DataEntity> normalizeFinishedData(List<DataEntity> source) {
		if (source == null || source.isEmpty()) {
			return new ArrayList<>();
		}

		Map<String, DataEntity> normalizedMap = source.stream()
				.filter(e -> e != null)
				.collect(Collectors.toMap(
						this::buildNormalizeKey,
						e -> e,
						this::preferDataEntity,
						LinkedHashMap::new));

		return new ArrayList<>(normalizedMap.values());
	}

	/**
	 * 正規化用キー
	 * match_id がある場合は match_id を最優先。
	 * match_id が無い場合は誤統合を避けるため従来寄りのキーにする。
	 */
	private String buildNormalizeKey(DataEntity e) {
		String matchId = nvl(e.getMatchId());
		if (!matchId.isEmpty()) {
			return "MID||" + matchId;
		}
		return String.join("||",
				nvl(e.getDataCategory()),
				nvl(e.getTimes()),
				nvl(e.getHomeTeamName()),
				nvl(e.getAwayTeamName()));
	}

	/**
	 * analyze_manual_data 既存判定用キー。
	 * match_id がある場合はカテゴリ違いでも同一試合として扱う。
	 */
	private String buildAnalyzeKey(String gameCategory, String times,
			String homeTeamName, String awayTeamName, String matchId) {
		String normalizedMatchId = nvl(matchId);
		if (!normalizedMatchId.isEmpty()) {
			return "MID||" + normalizedMatchId;
		}
		return String.join("||",
				nvl(gameCategory),
				nvl(times),
				nvl(homeTeamName),
				nvl(awayTeamName),
				normalizedMatchId);
	}

	/**
	 * DataEntity の優先順位比較
	 */
	private DataEntity preferDataEntity(DataEntity a, DataEntity b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}

		// 1. data_category に「ラウンド」を含む方を優先
		boolean aHasRound = containsRound(a.getDataCategory());
		boolean bHasRound = containsRound(b.getDataCategory());
		if (aHasRound && !bHasRound) {
			return a;
		}
		if (!aHasRound && bHasRound) {
			return b;
		}

		// 2. update_time が新しい方
		long aUpdate = parseUpdateTimeToEpochMillis(a.getUpdateTime());
		long bUpdate = parseUpdateTimeToEpochMillis(b.getUpdateTime());
		if (aUpdate > bUpdate) {
			return a;
		}
		if (aUpdate < bUpdate) {
			return b;
		}

		// 3. seq が大きい方
		long aSeq = parseSeqToLong(a.getSeq());
		long bSeq = parseSeqToLong(b.getSeq());
		if (aSeq >= bSeq) {
			return a;
		}
		return b;
	}

	private boolean containsRound(String value) {
		return nvl(value).contains("ラウンド");
	}

	private long parseUpdateTimeToEpochMillis(String updateTime) {
		String value = nvl(updateTime);
		if (value.isEmpty()) {
			return Long.MIN_VALUE;
		}

		List<DateTimeFormatter> formatters = List.of(
				DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX"),
				DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX"));

		for (DateTimeFormatter formatter : formatters) {
			try {
				return OffsetDateTime.parse(value, formatter).toInstant().toEpochMilli();
			} catch (Exception ignore) {
				// 次の formatter を試す
			}
		}

		return Long.MIN_VALUE;
	}

	private long parseSeqToLong(Object seq) {
		if (seq == null) {
			return Long.MIN_VALUE;
		}
		try {
			return Long.parseLong(String.valueOf(seq).trim());
		} catch (NumberFormatException ex) {
			return Long.MIN_VALUE;
		}
	}

	/**
	 * DataEntity -> BookDataEntity 変換
	 */
	private BookDataEntity toBookDataEntity(DataEntity src) {
		BookDataEntity dest = new BookDataEntity();

		dest.setSeq(src.getSeq() == null ? "" : String.valueOf(src.getSeq()));
		dest.setConditionResultDataSeqId(nvl(src.getConditionResultDataSeqId()));
		dest.setHomeRank(nvl(src.getHomeRank()));
		dest.setGameTeamCategory(nvl(src.getDataCategory()));
		dest.setTime(nvl(src.getTimes()));
		dest.setHomeTeamName(nvl(src.getHomeTeamName()));
		dest.setHomeScore(nvl(src.getHomeScore()));
		dest.setAwayRank(nvl(src.getAwayRank()));
		dest.setAwayTeamName(nvl(src.getAwayTeamName()));
		dest.setAwayScore(nvl(src.getAwayScore()));

		dest.setHomeExp(nvl(src.getHomeExp()));
		dest.setAwayExp(nvl(src.getAwayExp()));
		dest.setHomeInGoalExp(nvl(src.getHomeInGoalExp()));
		dest.setAwayInGoalExp(nvl(src.getAwayInGoalExp()));

		dest.setHomeBallPossesion(nvl(src.getHomeDonation()));
		dest.setAwayBallPossesion(nvl(src.getAwayDonation()));

		dest.setHomeShootAll(nvl(src.getHomeShootAll()));
		dest.setAwayShootAll(nvl(src.getAwayShootAll()));
		dest.setHomeShootIn(nvl(src.getHomeShootIn()));
		dest.setAwayShootIn(nvl(src.getAwayShootIn()));
		dest.setHomeShootOut(nvl(src.getHomeShootOut()));
		dest.setAwayShootOut(nvl(src.getAwayShootOut()));

		dest.setHomeShootBlocked(nvl(src.getHomeBlockShoot()));
		dest.setAwayShootBlocked(nvl(src.getAwayBlockShoot()));

		dest.setHomeBigChance(nvl(src.getHomeBigChance()));
		dest.setAwayBigChance(nvl(src.getAwayBigChance()));

		dest.setHomeCornerKick(nvl(src.getHomeCorner()));
		dest.setAwayCornerKick(nvl(src.getAwayCorner()));

		dest.setHomeBoxShootIn(nvl(src.getHomeBoxShootIn()));
		dest.setAwayBoxShootIn(nvl(src.getAwayBoxShootIn()));
		dest.setHomeBoxShootOut(nvl(src.getHomeBoxShootOut()));
		dest.setAwayBoxShootOut(nvl(src.getAwayBoxShootOut()));

		dest.setHomeGoalPost(nvl(src.getHomeGoalPost()));
		dest.setAwayGoalPost(nvl(src.getAwayGoalPost()));
		dest.setHomeGoalHead(nvl(src.getHomeGoalHead()));
		dest.setAwayGoalHead(nvl(src.getAwayGoalHead()));

		dest.setHomeKeeperSave(nvl(src.getHomeKeeperSave()));
		dest.setAwayKeeperSave(nvl(src.getAwayKeeperSave()));

		dest.setHomeFreeKick(nvl(src.getHomeFreeKick()));
		dest.setAwayFreeKick(nvl(src.getAwayFreeKick()));

		dest.setHomeOffSide(nvl(src.getHomeOffside()));
		dest.setAwayOffSide(nvl(src.getAwayOffside()));

		dest.setHomeFoul(nvl(src.getHomeFoul()));
		dest.setAwayFoul(nvl(src.getAwayFoul()));

		dest.setHomeYellowCard(nvl(src.getHomeYellowCard()));
		dest.setAwayYellowCard(nvl(src.getAwayYellowCard()));

		dest.setHomeRedCard(nvl(src.getHomeRedCard()));
		dest.setAwayRedCard(nvl(src.getAwayRedCard()));

		dest.setHomeSlowIn(nvl(src.getHomeSlowIn()));
		dest.setAwaySlowIn(nvl(src.getAwaySlowIn()));

		dest.setHomeBoxTouch(nvl(src.getHomeBoxTouch()));
		dest.setAwayBoxTouch(nvl(src.getAwayBoxTouch()));

		dest.setHomePassCount(nvl(src.getHomePassCount()));
		dest.setAwayPassCount(nvl(src.getAwayPassCount()));

		dest.setHomeLongPassCount(nvl(src.getHomeLongPassCount()));
		dest.setAwayLongPassCount(nvl(src.getAwayLongPassCount()));

		dest.setHomeFinalThirdPassCount(nvl(src.getHomeFinalThirdPassCount()));
		dest.setAwayFinalThirdPassCount(nvl(src.getAwayFinalThirdPassCount()));

		dest.setHomeCrossCount(nvl(src.getHomeCrossCount()));
		dest.setAwayCrossCount(nvl(src.getAwayCrossCount()));

		dest.setHomeTackleCount(nvl(src.getHomeTackleCount()));
		dest.setAwayTackleCount(nvl(src.getAwayTackleCount()));

		dest.setHomeClearCount(nvl(src.getHomeClearCount()));
		dest.setAwayClearCount(nvl(src.getAwayClearCount()));

		dest.setHomeDuelCount(nvl(src.getHomeDuelCount()));
		dest.setAwayDuelCount(nvl(src.getAwayDuelCount()));

		dest.setHomeInterceptCount(nvl(src.getHomeInterceptCount()));
		dest.setAwayInterceptCount(nvl(src.getAwayInterceptCount()));

		dest.setRecordTime(nvl(src.getRecordTime()));
		dest.setWeather(nvl(src.getWeather()));
		dest.setTemperature(nvl(src.getTemparature()));
		dest.setHumid(nvl(src.getHumid()));
		dest.setJudgeMember(nvl(src.getJudgeMember()));
		dest.setHomeManager(nvl(src.getHomeManager()));
		dest.setAwayManager(nvl(src.getAwayManager()));
		dest.setHomeFormation(nvl(src.getHomeFormation()));
		dest.setAwayFormation(nvl(src.getAwayFormation()));
		dest.setStudium(nvl(src.getStudium()));
		dest.setCapacity(nvl(src.getCapacity()));
		dest.setAudience(nvl(src.getAudience()));

		dest.setHomeMaxGettingScorer(nvl(src.getHomeMaxGettingScorer()));
		dest.setAwayMaxGettingScorer(nvl(src.getAwayMaxGettingScorer()));
		dest.setHomeMaxGettingScorerGameSituation(nvl(src.getHomeMaxGettingScorerGameSituation()));
		dest.setAwayMaxGettingScorerGameSituation(nvl(src.getAwayMaxGettingScorerGameSituation()));

		dest.setHomeTeamHomeScore(nvl(src.getHomeTeamHomeScore()));
		dest.setHomeTeamHomeLost(nvl(src.getHomeTeamHomeLost()));
		dest.setAwayTeamHomeScore(nvl(src.getAwayTeamHomeScore()));
		dest.setAwayTeamHomeLost(nvl(src.getAwayTeamHomeLost()));

		dest.setHomeTeamAwayScore(nvl(src.getHomeTeamAwayScore()));
		dest.setHomeTeamAwayLost(nvl(src.getHomeTeamAwayLost()));
		dest.setAwayTeamAwayScore(nvl(src.getAwayTeamAwayScore()));
		dest.setAwayTeamAwayLost(nvl(src.getAwayTeamAwayLost()));

		dest.setNoticeFlg(nvl(src.getNoticeFlg()));
		dest.setGoalTime(nvl(src.getGoalTime()));
		dest.setGoalTeamMember(nvl(src.getGoalTeamMember()));
		dest.setJudge(nvl(src.getJudge()));
		dest.setHomeTeamStyle(nvl(src.getHomeTeamStyle()));
		dest.setAwayTeamStyle(nvl(src.getAwayTeamStyle()));
		dest.setProbablity(nvl(src.getProbablity()));
		dest.setPredictionScoreTime(nvl(src.getPredictionScoreTime()));

		dest.setFilePath(nvl(src.getFile()));
		dest.setFileCount(src.getFileCount());

		return dest;
	}

	private String buildCsvDetailKey(String dataCategory, String season,
			String homeTeamName, String awayTeamName) {
		return String.join("||",
				nvl(dataCategory),
				nvl(season),
				nvl(homeTeamName),
				nvl(awayTeamName));
	}

	private String buildDataRestrictKey(String dataCategory,
			String homeTeamName, String awayTeamName, String matchId) {
		return String.join("||",
				nvl(dataCategory),
				nvl(homeTeamName),
				nvl(awayTeamName),
				nvl(matchId));
	}

	private boolean isValidForCoreStat(DataEntity e) {
		return !nvl(e.getDataCategory()).isEmpty()
				&& !nvl(e.getHomeTeamName()).isEmpty()
				&& !nvl(e.getAwayTeamName()).isEmpty()
				&& isInteger(nvl(e.getHomeScore()))
				&& isInteger(nvl(e.getAwayScore()))
				&& e.getSeq() != null;
	}

	private boolean isInteger(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}
		try {
			Integer.parseInt(value.trim());
			return true;
		} catch (NumberFormatException ex) {
			return false;
		}
	}

	/**
	 * 終了済み対象判定
	 */
	private boolean isFinishedTarget(DataEntity entity) {
		String times = nvl(entity.getTimes());
		return BookMakersCommonConst.FIN.equals(times)
				|| times.contains(BookMakersCommonConst.PENALTY);
	}

	private String nvl(String value) {
		return value == null ? "" : value.trim();
	}

	/**
	 * future_master 側の照合キー
	 * home_team_name + away_team_name + future_time(JST日付)
	 */
	private String buildFutureMasterKey(FutureEntity e) {
		return String.join("||",
				nvl(e.getHomeTeamName()),
				nvl(e.getAwayTeamName()),
				toTokyoDateString(e.getFutureTime()));
	}

	/**
	 * data 側の照合キー
	 * home_team_name + away_team_name + record_time(JST日付)
	 */
	private String buildDataFutureKey(DataEntity e) {
		return String.join("||",
				nvl(e.getHomeTeamName()),
				nvl(e.getAwayTeamName()),
				toTokyoDateString(e.getRecordTime()));
	}

	/**
	 * JST日付文字列へ変換
	 * String / OffsetDateTime の両方を吸収
	 */
	private String toTokyoDateString(Object dateTime) {
		if (dateTime == null) {
			return "";
		}

		if (dateTime instanceof OffsetDateTime) {
			OffsetDateTime odt = (OffsetDateTime) dateTime;
			return odt.toInstant()
					.atZone(JST)
					.toLocalDate()
					.toString();
		}

		String value = String.valueOf(dateTime).trim();
		if (value.isEmpty()) {
			return "";
		}

		List<DateTimeFormatter> formatters = List.of(
				DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX"),
				DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX"));

		for (DateTimeFormatter formatter : formatters) {
			try {
				return OffsetDateTime.parse(value, formatter)
						.toInstant()
						.atZone(JST)
						.toLocalDate()
						.toString();
			} catch (Exception ignore) {
				// 次の formatter を試す
			}
		}

		try {
			return OffsetDateTime.parse(value)
					.toInstant()
					.atZone(JST)
					.toLocalDate()
					.toString();
		} catch (Exception ignore) {
			return "";
		}
	}

	private static String safe(String s) {
		return (s == null) ? "" : s;
	}
}
