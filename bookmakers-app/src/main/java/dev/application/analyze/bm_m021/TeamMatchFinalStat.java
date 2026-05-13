package dev.application.analyze.bm_m021;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;
import lombok.RequiredArgsConstructor;

/**
 * BM_M021統計分析ロジック
 * - 集計/変換のみ担当
 * - DB更新はWriterへ委譲
 *
 * @author shiraishitoshio
 */
@Component
@RequiredArgsConstructor
public class TeamMatchFinalStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamMatchFinalStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamMatchFinalStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M021_TEAM_MATCH_FINAL";

	/** SLF4J Logger */
	private static final Logger log = LoggerFactory.getLogger(TeamMatchFinalStat.class);

	/** Mapper */
	private final BookDataToTeamMatchFinalMapper bookDataToTeamMatchFinalMapper;

	/** Writer */
	private final TeamMatchFinalWriter teamMatchFinalWriter;

	/** ログ管理クラス */
	private final ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";

		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		long started = System.currentTimeMillis();

		try {
			if (entities == null || entities.isEmpty()) {
				log.info("[BM_M021] entities is empty. nothing to process.");
				this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
				return;
			}

			log.info("[BM_M021] start calcStat. leagueCount={}", entities.size());

			int leagueIndex = 0;
			int processedMatchCount = 0;
			int skippedNotFinCount = 0;
			int savedEntityCount = 0;

			for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
				leagueIndex++;

				String leagueKey = entry.getKey();
				Map<String, List<BookDataEntity>> matchMap = entry.getValue();

				int matchGroupSize = (matchMap == null) ? 0 : matchMap.size();
				log.info("[BM_M021] league start. leagueIndex={}/{}, leagueKey={}, matchGroupSize={}",
						leagueIndex, entities.size(), leagueKey, matchGroupSize);

				if (matchMap == null || matchMap.isEmpty()) {
					log.warn("[BM_M021] matchMap is empty. leagueKey={}", leagueKey);
					continue;
				}

				int matchIndex = 0;

				for (Map.Entry<String, List<BookDataEntity>> matchEntry : matchMap.entrySet()) {
					matchIndex++;

					String matchKey = matchEntry.getKey();
					List<BookDataEntity> dataList = matchEntry.getValue();

					if (dataList == null || dataList.isEmpty()) {
						log.warn("[BM_M021] dataList is empty. leagueKey={}, matchKey={}", leagueKey, matchKey);
						continue;
					}

					BookDataEntity returnMaxEntity;
					try {
						returnMaxEntity = ExecuteMainUtil.getMaxSeqEntities(dataList);
					} catch (Exception e) {
						log.error("[BM_M021] getMaxSeqEntities failed. leagueKey={}, matchKey={}, dataSize={}",
								leagueKey, matchKey, dataList.size(), e);
						throw e;
					}

					if (returnMaxEntity == null) {
						log.warn("[BM_M021] returnMaxEntity is null. leagueKey={}, matchKey={}, dataSize={}",
								leagueKey, matchKey, dataList.size());
						continue;
					}

					String fillChar = setLoggerFillChar(
							returnMaxEntity.getGameTeamCategory(),
							returnMaxEntity.getHomeTeamName(),
							returnMaxEntity.getAwayTeamName());

					String filePath = safe(returnMaxEntity.getFilePath());
					String time = safe(returnMaxEntity.getTime());

					String messageCd = MessageCdConst.MCD00099I_LOG;
					this.manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, filePath);

					log.info(
							"[BM_M021] match start. leagueKey={}, matchIndex={}/{}, matchKey={}, dataSize={}, time={}, fillChar={}, filePath={}",
							leagueKey, matchIndex, matchGroupSize, matchKey, dataList.size(), time, fillChar, filePath);

					if (!finDataExistsChk(returnMaxEntity)) {
						skippedNotFinCount++;
						log.info("[BM_M021] skip not FIN data. leagueKey={}, matchKey={}, fillChar={}",
								leagueKey, matchKey, fillChar);
						continue;
					}

					TeamMatchFinalOutputDTO dto;
					try {
						log.info("[BM_M021] setFinalData start. leagueKey={}, matchKey={}, fillChar={}",
								leagueKey, matchKey, fillChar);

						dto = setFinalData(dataList, returnMaxEntity);

						log.info("[BM_M021] setFinalData done. leagueKey={}, matchKey={}, fillChar={}",
								leagueKey, matchKey, fillChar);
					} catch (Exception e) {
						log.error("[BM_M021] setFinalData failed. leagueKey={}, matchKey={}, fillChar={}",
								leagueKey, matchKey, fillChar, e);
						throw e;
					}

					try {
						log.info("[BM_M021] setFinal start. leagueKey={}, matchKey={}, fillChar={}",
								leagueKey, matchKey, fillChar);

						int savedThisMatch = setFinal(dto, returnMaxEntity);
						savedEntityCount += savedThisMatch;
						processedMatchCount++;

						log.info("[BM_M021] setFinal done. leagueKey={}, matchKey={}, fillChar={}, savedThisMatch={}",
								leagueKey, matchKey, fillChar, savedThisMatch);
					} catch (Exception e) {
						log.error("[BM_M021] setFinal failed. leagueKey={}, matchKey={}, fillChar={}",
								leagueKey, matchKey, fillChar, e);
						throw e;
					}
				}

				log.info("[BM_M021] league done. leagueIndex={}/{}, leagueKey={}",
						leagueIndex, entities.size(), leagueKey);
			}

			long elapsed = System.currentTimeMillis() - started;
			log.info("[BM_M021] calcStat finished. processedMatchCount={}, skippedNotFinCount={}, savedEntityCount={}, elapsedMs={}",
					processedMatchCount, skippedNotFinCount, savedEntityCount, elapsed);

			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		} finally {
			this.manageLoggerComponent.clear();
		}
	}

	/**
	 * 終了済データ存在チェック
	 * @param entity BookDataEntity
	 * @return true: FIN / false: FIN以外
	 */
	private boolean finDataExistsChk(BookDataEntity entity) {
		final String METHOD_NAME = "finDataExistsChk";
		String fillChar = setLoggerFillChar(entity.getGameTeamCategory(),
				entity.getHomeTeamName(), entity.getAwayTeamName());

		if (!BookMakersCommonConst.FIN.equals(entity.getTime())) {
			String messageCd = MessageCdConst.MCD00013I_NO_FIN_DATA;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar);
			return false;
		}
		return true;
	}

	/**
	 * 埋め字設定
	 * @param detaKey 国リーグ
	 * @param home ホーム
	 * @param away アウェー
	 * @return 埋め字
	 */
	private String setLoggerFillChar(String detaKey, String home, String away) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("国,リーグ: ").append(safe(detaKey)).append(", ");
		stringBuilder.append("ホーム: ").append(safe(home)).append(", ");
		stringBuilder.append("アウェー: ").append(safe(away));
		return stringBuilder.toString();
	}

	/**
	 * 特殊最終データ格納
	 * @param entity List<BookDataEntity>
	 * @param returnMaxEntity BookDataEntity
	 * @return TeamMatchFinalOutputDTO
	 */
	private TeamMatchFinalOutputDTO setFinalData(List<BookDataEntity> entity, BookDataEntity returnMaxEntity) {
		TeamMatchFinalOutputDTO teamMatchFinalOutputDTO = new TeamMatchFinalOutputDTO();

		String fillChar = setLoggerFillChar(
				returnMaxEntity.getGameTeamCategory(),
				returnMaxEntity.getHomeTeamName(),
				returnMaxEntity.getAwayTeamName());

		log.info("[BM_M021] setFinalData detail start. fillChar={}, entitySize={}", fillChar, entity == null ? 0 : entity.size());

		// ボール保持率
		double totalHomePossession = 0.0;
		double totalAwayPossession = 0.0;
		int countHome = 0;
		int countAway = 0;

		for (BookDataEntity e : entity) {
			String homePossStr = e.getHomeBallPossesion();
			if (homePossStr != null && homePossStr.endsWith("%")) {
				try {
					double value = Double.parseDouble(homePossStr.replace("%", "").trim());
					totalHomePossession += value;
					countHome++;
				} catch (NumberFormatException ex) {
					log.warn("[BM_M021] invalid home possession. fillChar={}, raw={}", fillChar, homePossStr);
				}
			}

			String awayPossStr = e.getAwayBallPossesion();
			if (awayPossStr != null && awayPossStr.endsWith("%")) {
				try {
					double value = Double.parseDouble(awayPossStr.replace("%", "").trim());
					totalAwayPossession += value;
					countAway++;
				} catch (NumberFormatException ex) {
					log.warn("[BM_M021] invalid away possession. fillChar={}, raw={}", fillChar, awayPossStr);
				}
			}
		}

		String avgHomePossession = countHome > 0
				? String.format("%.2f%%", totalHomePossession / countHome)
				: String.format("%.2f%%", totalHomePossession);

		String avgAwayPossession = countAway > 0
				? String.format("%.2f%%", totalAwayPossession / countAway)
				: String.format("%.2f%%", totalAwayPossession);

		log.info("[BM_M021] possession calculated. fillChar={}, avgHomePossession={}, avgAwayPossession={}, countHome={}, countAway={}",
				fillChar, avgHomePossession, avgAwayPossession, countHome, countAway);

		// 3分割データ
		List<String> homePassList = safeSplitGroup(returnMaxEntity.getHomePassCount(), "homePass", fillChar);
		List<String> awayPassList = safeSplitGroup(returnMaxEntity.getAwayPassCount(), "awayPass", fillChar);
		List<String> homeFinalPassList = safeSplitGroup(returnMaxEntity.getHomeFinalThirdPassCount(), "homeFinalThirdPass", fillChar);
		List<String> awayFinalPassList = safeSplitGroup(returnMaxEntity.getAwayFinalThirdPassCount(), "awayFinalThirdPass", fillChar);
		List<String> homeCrossList = safeSplitGroup(returnMaxEntity.getHomeCrossCount(), "homeCross", fillChar);
		List<String> awayCrossList = safeSplitGroup(returnMaxEntity.getAwayCrossCount(), "awayCross", fillChar);
		List<String> homeTackleList = safeSplitGroup(returnMaxEntity.getHomeTackleCount(), "homeTackle", fillChar);
		List<String> awayTackleList = safeSplitGroup(returnMaxEntity.getAwayTackleCount(), "awayTackle", fillChar);

		FinalData homeFinalData = new FinalData();
		homeFinalData.setPossession(avgHomePossession);
		homeFinalData.setPass(new RetentionData(
				safeListGet(homePassList, 0, "homePass[0]", fillChar),
				safeListGet(homePassList, 1, "homePass[1]", fillChar),
				safeListGet(homePassList, 2, "homePass[2]", fillChar)));
		homeFinalData.setFinalThirdPass(new RetentionData(
				safeListGet(homeFinalPassList, 0, "homeFinalThirdPass[0]", fillChar),
				safeListGet(homeFinalPassList, 1, "homeFinalThirdPass[1]", fillChar),
				safeListGet(homeFinalPassList, 2, "homeFinalThirdPass[2]", fillChar)));
		homeFinalData.setCross(new RetentionData(
				safeListGet(homeCrossList, 0, "homeCross[0]", fillChar),
				safeListGet(homeCrossList, 1, "homeCross[1]", fillChar),
				safeListGet(homeCrossList, 2, "homeCross[2]", fillChar)));
		homeFinalData.setTackle(new RetentionData(
				safeListGet(homeTackleList, 0, "homeTackle[0]", fillChar),
				safeListGet(homeTackleList, 1, "homeTackle[1]", fillChar),
				safeListGet(homeTackleList, 2, "homeTackle[2]", fillChar)));

		FinalData awayFinalData = new FinalData();
		awayFinalData.setPossession(avgAwayPossession);
		awayFinalData.setPass(new RetentionData(
				safeListGet(awayPassList, 0, "awayPass[0]", fillChar),
				safeListGet(awayPassList, 1, "awayPass[1]", fillChar),
				safeListGet(awayPassList, 2, "awayPass[2]", fillChar)));
		awayFinalData.setFinalThirdPass(new RetentionData(
				safeListGet(awayFinalPassList, 0, "awayFinalThirdPass[0]", fillChar),
				safeListGet(awayFinalPassList, 1, "awayFinalThirdPass[1]", fillChar),
				safeListGet(awayFinalPassList, 2, "awayFinalThirdPass[2]", fillChar)));
		awayFinalData.setCross(new RetentionData(
				safeListGet(awayCrossList, 0, "awayCross[0]", fillChar),
				safeListGet(awayCrossList, 1, "awayCross[1]", fillChar),
				safeListGet(awayCrossList, 2, "awayCross[2]", fillChar)));
		awayFinalData.setTackle(new RetentionData(
				safeListGet(awayTackleList, 0, "awayTackle[0]", fillChar),
				safeListGet(awayTackleList, 1, "awayTackle[1]", fillChar),
				safeListGet(awayTackleList, 2, "awayTackle[2]", fillChar)));

		teamMatchFinalOutputDTO.setHomeObject(homeFinalData);
		teamMatchFinalOutputDTO.setAwayObject(awayFinalData);

		log.info("[BM_M021] setFinalData detail done. fillChar={}", fillChar);

		return teamMatchFinalOutputDTO;
	}

	/**
	 * 最終登録データを作成し、Writerへ保存委譲
	 * @param dto TeamMatchFinalOutputDTO
	 * @param returnMaxEntity BookDataEntity
	 * @return 保存件数
	 */
	private int setFinal(final TeamMatchFinalOutputDTO dto, final BookDataEntity returnMaxEntity) {
		BookDataEntity mappEntity = returnMaxEntity;

		FinalData finalHomeData = dto.getHomeObject();
		FinalData finalAwayData = dto.getAwayObject();

		String resultHome = compareScore(returnMaxEntity.getHomeScore(), returnMaxEntity.getAwayScore());
		String resultAway = compareScore(returnMaxEntity.getAwayScore(), returnMaxEntity.getHomeScore());

		String fillChar = setLoggerFillChar(returnMaxEntity.getGameTeamCategory(),
				returnMaxEntity.getHomeTeamName(), returnMaxEntity.getAwayTeamName());

		log.info("[BM_M021] mapping home entity. fillChar={}, resultHome={}", fillChar, resultHome);
		TeamMatchFinalStatsEntity homeMatchFinalStatsEntity = this.bookDataToTeamMatchFinalMapper.mapHomeStruct(
				mappEntity,
				finalHomeData,
				finalAwayData,
				"H",
				setSymbol(resultHome) + safe(returnMaxEntity.getHomeScore()) + "-" + safe(returnMaxEntity.getAwayScore()),
				resultHome);

		log.info("[BM_M021] mapping away entity. fillChar={}, resultAway={}", fillChar, resultAway);
		TeamMatchFinalStatsEntity awayMatchFinalStatsEntity = this.bookDataToTeamMatchFinalMapper.mapAwayStruct(
				mappEntity,
				finalAwayData,
				finalHomeData,
				"A",
				setSymbol(resultAway) + safe(returnMaxEntity.getAwayScore()) + "-" + safe(returnMaxEntity.getHomeScore()),
				resultAway);

		return this.teamMatchFinalWriter.writePair(homeMatchFinalStatsEntity, awayMatchFinalStatsEntity, fillChar);
	}

	/**
	 * スコア比較
	 * @param ownScore 自チーム得点
	 * @param oppositeScore 相手チーム得点
	 * @return WIN / LOSE / DRAW
	 */
	private String compareScore(final String ownScore, final String oppositeScore) {
		try {
			int own = Integer.parseInt(safe(ownScore));
			int opposite = Integer.parseInt(safe(oppositeScore));
			if (own > opposite) {
				return "WIN";
			}
			if (own < opposite) {
				return "LOSE";
			}
			return "DRAW";
		} catch (NumberFormatException e) {
			log.warn("[BM_M021] compareScore parse failed. ownScore={}, oppositeScore={}", ownScore, oppositeScore);
			return "DRAW";
		}
	}

	/**
	 * 勝敗のマークを返す
	 * @param result 勝敗
	 * @return ○ / ● / △
	 */
	private String setSymbol(final String result) {
		if ("WIN".equals(result)) {
			return "○";
		} else if ("LOSE".equals(result)) {
			return "●";
		}
		return "△";
	}

	/**
	 * null安全
	 */
	private String safe(String value) {
		return value == null ? "" : value;
	}

	/**
	 * splitGroupの安全ラッパ
	 */
	private List<String> safeSplitGroup(String raw, String label, String fillChar) {
		try {
			List<String> list = ExecuteMainUtil.splitGroup(raw);
			if (list == null) {
				log.warn("[BM_M021] splitGroup returned null. label={}, fillChar={}, raw={}", label, fillChar, raw);
				return Collections.emptyList();
			}
			log.info("[BM_M021] splitGroup done. label={}, fillChar={}, raw={}, size={}, values={}",
					label, fillChar, raw, list.size(), list);
			return list;
		} catch (Exception e) {
			log.error("[BM_M021] splitGroup failed. label={}, fillChar={}, raw={}", label, fillChar, raw, e);
			return Collections.emptyList();
		}
	}

	/**
	 * List安全取得
	 */
	private String safeListGet(List<String> list, int index, String label, String fillChar) {
		if (list == null) {
			log.warn("[BM_M021] list is null. label={}, fillChar={}", label, fillChar);
			return "";
		}
		if (index < 0 || index >= list.size()) {
			log.warn("[BM_M021] list index out of range. label={}, fillChar={}, size={}, index={}",
					label, fillChar, list.size(), index);
			return "";
		}
		String value = list.get(index);
		return value == null ? "" : value;
	}
}
