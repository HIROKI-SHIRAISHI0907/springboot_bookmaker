package dev.application.analyze.bm_m097;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.AnalyzeManualRepository;
import dev.application.domain.repository.bm.BookDataRepository;
import dev.application.main.service.CoreStat;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.entity.DataEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.getinfo.GetStatInfo;
import dev.common.logger.ManageLoggerComponent;

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

	@Autowired
	private BookDataRepository bookDataRepository;

	@Autowired
	private AnalyzeManualRepository analyzeManualDataRepository;

	@Autowired
	private CoreStat coreStat;

	@Autowired
	private GetStatInfo getStatInfo;

	/** ログ管理ラッパー*/
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 登録ロジック
	 */
	@Transactional(rollbackFor = Exception.class)
	public void manualStat() {
		final String METHOD_NAME = "manualStat";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 1) 手動データ取得
		List<DataEntity> finList = bookDataRepository.getFinData();
		if (finList == null || finList.isEmpty()) {
			return;
		}

		// 2) 終了済み or PENALTY含みを対象
		List<DataEntity> finishedList = finList.stream()
				.filter(this::isFinishedTarget)
				.collect(Collectors.toList());

		if (finishedList.isEmpty()) {
			return;
		}

		// 3) matchId をユニーク抽出
		List<String> matchIds = finishedList.stream()
				.map(DataEntity::getMatchId)
				.map(this::nvl)
				.filter(s -> !s.isEmpty())
				.distinct()
				.collect(Collectors.toList());

		// 4) analyze_manual_data に既にあるものを一括取得
		Set<String> analyzedKeySet = new HashSet<>();

		for (int i = 0; i < matchIds.size(); i += MATCH_ID_BATCH_SIZE) {
			int end = Math.min(i + MATCH_ID_BATCH_SIZE, matchIds.size());
			List<String> batch = matchIds.subList(i, end);

			List<AnalyzeManualEntity> existingList = analyzeManualDataRepository.selectByMatchIds(batch);
			if (existingList == null || existingList.isEmpty()) {
				continue;
			}

			for (AnalyzeManualEntity existing : existingList) {
				analyzedKeySet.add(buildKey(
						existing.getGameCategory(),
						existing.getTimes(),
						existing.getHomeTeamName(),
						existing.getAwayTeamName(),
						existing.getMatchId()));
			}
		}

		// 5) 未反映データだけを抽出
		List<DataEntity> targetList = finishedList.stream()
				.filter(e -> !analyzedKeySet.contains(buildKey(
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
					"targetList not found: ");
			return;
		}

		// 6) CoreStat に渡すMapを作成して実行
		List<BookDataEntity> bookDataList = targetList.stream()
				.map(this::toBookDataEntity)
				.collect(Collectors.toList());

		Map<String, Map<String, List<BookDataEntity>>> entities =
				getStatInfo.buildStatMapFromEntities(bookDataList);

		try {
			coreStat.execute(entities);
		} catch (Exception e1) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e1, "coreStat error");
			this.manageLoggerComponent.createSystemException(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, null);
		}

		// 7) 成功したデータを analyze_manual_data に登録
		for (DataEntity entity : targetList) {
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
						entity.getDataCategory() + ": " + entity.getHomeTeamName() +
								"-" + entity.getAwayTeamName());
			}

			String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					BM_NUMBER + " ホーム登録件数: " + result + "件 (" + entity.getDataCategory() + ": "
							+ entity.getHomeTeamName() +
							"-" + entity.getAwayTeamName() + ")");
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
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


	/**
	 * 終了済み対象判定
	 */
	private boolean isFinishedTarget(DataEntity entity) {
		String times = nvl(entity.getTimes());
		return BookMakersCommonConst.FIN.equals(times)
				|| times.contains(BookMakersCommonConst.PENALTY);
	}

	private String buildKey(String gameCategory, String times, String homeTeamName, String awayTeamName, String matchId) {
		return String.join("||",
				nvl(gameCategory),
				nvl(times),
				nvl(homeTeamName),
				nvl(awayTeamName),
				nvl(matchId));
	}

	private String nvl(String value) {
		return value == null ? "" : value.trim();
	}
}
