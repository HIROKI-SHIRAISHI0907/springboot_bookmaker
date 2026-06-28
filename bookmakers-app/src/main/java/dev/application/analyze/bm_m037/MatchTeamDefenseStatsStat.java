package dev.application.analyze.bm_m037;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.common.util.BookMakersCommonConst;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * <p>BM_M037 試合単位の守備力・被圧力統計を算出するサービスクラスです。</p>
 *
 * <ul>
 *   <li>入力: 国×リーグ×カード単位の {@link BookDataEntity} 群</li>
 *   <li>処理: 1試合の最終スナップショットをもとに、
 *       ホーム・アウェーそれぞれの {@link MatchTeamDefenseStatsEntity} を生成</li>
 *   <li>出力: {@link MatchTeamDefenseStatsWriter} を介して登録</li>
 * </ul>
 *
 * <p>
 * 本クラスでは、被シュート数、被枠内シュート数、被ボックスタッチ数、
 * セーブ率、ブロック率、クリア頻度、守備アクション率に加え、
 * 時系列を用いて以下を算出します。
 * </p>
 * <ul>
 *   <li>リード時の被シュート増加率</li>
 *   <li>失点後10分の被攻撃量</li>
 *   <li>退場後10分の被圧力</li>
 * </ul>
 *
 * @author shiraishitoshio
 * @since 1.0
 */
@Component
public class MatchTeamDefenseStatsStat implements AnalyzeEntityIF {

	/** プロジェクト名（ログ用） */
	private static final String PROJECT_NAME = MatchTeamDefenseStatsStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名（ログ用） */
	private static final String CLASS_NAME = MatchTeamDefenseStatsStat.class.getName();

	/** 実行モード（ログ用） */
	private static final String EXEC_MODE = "BM_M037_MATCH_TEAM_DEFENSE_STATS";

	/** 記録時間フォーマット */
	private static final DateTimeFormatter RECORD_TIME_FORMATTER =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX");

	/** 失点後/退場後の圧力観測窓（秒） */
	private static final int PRESSURE_WINDOW_SECONDS = 10 * 60;

	@Autowired
	private MatchTeamDefenseStatsWriter matchTeamDefenseStatsWriter;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 全ての国・リーグ・カードを走査し、
	 * 最終スナップショットと時系列差分から守備統計を算出して登録します。
	 * </p>
	 *
	 * @param entities 国×リーグごとにまとめられた時系列データ
	 */
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		manageLoggerComponent.init(EXEC_MODE, null);
		manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		try {
			for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
				String[] dataCategory = ExecuteMainUtil.splitLeagueInfo(entry.getKey());
				String country = dataCategory != null && dataCategory.length > 0 ? dataCategory[0] : null;
				String league = dataCategory != null && dataCategory.length > 1 ? dataCategory[1] : null;

				Map<String, List<BookDataEntity>> entrySub = entry.getValue();
				for (List<BookDataEntity> entityList : entrySub.values()) {
					if (entityList == null || entityList.isEmpty()) {
						continue;
					}
					decideBasedMain(entityList, country, league);
				}
			}
		} finally {
			manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			manageLoggerComponent.clear();
		}
	}

	/**
	 * 1試合分の時系列データから守備統計を算出し、ホーム・アウェーの2件を登録します。
	 *
	 * @param entities 1試合分の時系列データ
	 * @param country 国
	 * @param league リーグ
	 */
	private void decideBasedMain(List<BookDataEntity> entities, String country, String league) {
		final String METHOD_NAME = "decideBasedMain";

		try {
			if (entities == null || entities.isEmpty()) {
				return;
			}

			manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, entities.get(0).getFilePath());

			BookDataEntity base = resolveFinalEntity(entities);
			if (base == null) {
				return;
			}

			MatchTeamDefenseStatsEntity homeEntity = createHomeEntity(base, entities, country, league);
			MatchTeamDefenseStatsEntity awayEntity = createAwayEntity(base, entities, country, league);

			matchTeamDefenseStatsWriter.insert(homeEntity);
			matchTeamDefenseStatsWriter.insert(awayEntity);

		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
					"守備力・被圧力統計算出中に例外が発生しました。");
		}
	}

	/**
	 * 試合時系列の最終スナップショットを返します。
	 *
	 * <p>
	 * 優先順位は以下の通りです。
	 * </p>
	 * <ol>
	 *   <li>times = 終了済 の行</li>
	 *   <li>timeSortSeconds が最大の行</li>
	 *   <li>リスト末尾の行</li>
	 * </ol>
	 *
	 * @param entities 試合時系列データ
	 * @return 最終スナップショット
	 */
	private BookDataEntity resolveFinalEntity(List<BookDataEntity> entities) {
		BookDataEntity ended = null;
		for (BookDataEntity entity : entities) {
			if ("終了済".equals(trimToNull(entity.getTime()))) {
				ended = entity;
			}
		}
		if (ended != null) {
			return ended;
		}

		BookDataEntity maxEntity = null;
		Integer maxSeconds = null;
		for (BookDataEntity entity : entities) {
			Integer seconds = parseInteger(entity.getTimeSortSeconds());
			if (seconds == null) {
				seconds = parseMatchTimeToSeconds(entity.getTime());
			}
			if (maxEntity == null || (seconds != null && maxSeconds != null && seconds > maxSeconds)
					|| (maxEntity == null && seconds != null)) {
				maxEntity = entity;
				maxSeconds = seconds;
			}
		}
		if (maxEntity != null) {
			return maxEntity;
		}

		return entities.get(entities.size() - 1);
	}

	/**
	 * ホームチーム視点の守備統計を生成します。
	 *
	 * @param base 最終スナップショット
	 * @param entities 試合時系列
	 * @param country 国
	 * @param league リーグ
	 * @return ホームチーム守備統計
	 */
	private MatchTeamDefenseStatsEntity createHomeEntity(
			BookDataEntity base,
			List<BookDataEntity> entities,
			String country,
			String league) {

		MatchTeamDefenseStatsEntity entity = new MatchTeamDefenseStatsEntity();

		setCommonContext(
				entity,
				base.getMatchId(),
				resolveSeason(base),
				country,
				league,
				league,
				resolveTeamId(base.getHomeTeamName()),
				base.getHomeTeamName(),
				resolveTeamId(base.getAwayTeamName()),
				base.getAwayTeamName());

		Integer shotsConceded = parseInteger(base.getAwayShootAll());
		Integer shotsOnTargetConceded = parseInteger(base.getAwayShootIn());
		Integer boxTouchesConceded = parseInteger(base.getAwayBoxTouch());
		Integer keeperSaves = parseInteger(base.getHomeKeeperSave());
		Integer blockedShots = parseInteger(base.getAwayShootBlocked());
		Integer clearances = parseInteger(base.getHomeClearCount());
		Integer tackles = parseInteger(base.getHomeTackleCount());
		Integer interceptions = parseInteger(base.getHomeInterceptCount());
		Integer opponentFinalThirdPasses = parseInteger(base.getAwayFinalThirdPassCount());

		entity.setShotsConcededCount(shotsConceded);
		entity.setShotsOnTargetConcededCount(shotsOnTargetConceded);
		entity.setBoxTouchesConcededCount(boxTouchesConceded);
		entity.setKeeperSavesCount(keeperSaves);
		entity.setSaveRate(calcRate(keeperSaves, shotsOnTargetConceded));
		entity.setBlockedShotsCount(blockedShots);
		entity.setBlockRate(calcRate(blockedShots, shotsConceded));
		entity.setClearancesCount(clearances);
		entity.setClearanceFrequency(calcRate(clearances, boxTouchesConceded));
		entity.setTacklesCount(tackles);
		entity.setInterceptionsCount(interceptions);
		entity.setDefensiveActionRate(calcDefensiveActionRate(tackles, interceptions, clearances, shotsConceded,
				boxTouchesConceded, opponentFinalThirdPasses));
		entity.setLeadStateShotsConcededIncreaseRate(calcLeadStateShotsConcededIncreaseRate(entities, true));
		entity.setPostConceded10mPressure(calcPostConceded10mPressure(entities, true));
		entity.setPostRedCard10mPressure(calcPostRedCard10mPressure(entities, true));
		entity.setCalculatedAt(LocalDateTime.now());
		entity.setNote(buildNote(base));

		return entity;
	}

	/**
	 * アウェーチーム視点の守備統計を生成します。
	 *
	 * @param base 最終スナップショット
	 * @param entities 試合時系列
	 * @param country 国
	 * @param league リーグ
	 * @return アウェーチーム守備統計
	 */
	private MatchTeamDefenseStatsEntity createAwayEntity(
			BookDataEntity base,
			List<BookDataEntity> entities,
			String country,
			String league) {

		MatchTeamDefenseStatsEntity entity = new MatchTeamDefenseStatsEntity();

		setCommonContext(
				entity,
				base.getMatchId(),
				resolveSeason(base),
				country,
				league,
				league,
				resolveTeamId(base.getAwayTeamName()),
				base.getAwayTeamName(),
				resolveTeamId(base.getHomeTeamName()),
				base.getHomeTeamName());

		Integer shotsConceded = parseInteger(base.getHomeShootAll());
		Integer shotsOnTargetConceded = parseInteger(base.getHomeShootIn());
		Integer boxTouchesConceded = parseInteger(base.getHomeBoxTouch());
		Integer keeperSaves = parseInteger(base.getAwayKeeperSave());
		Integer blockedShots = parseInteger(base.getHomeShootBlocked());
		Integer clearances = parseInteger(base.getAwayClearCount());
		Integer tackles = parseInteger(base.getAwayTackleCount());
		Integer interceptions = parseInteger(base.getAwayInterceptCount());
		Integer opponentFinalThirdPasses = parseInteger(base.getHomeFinalThirdPassCount());

		entity.setShotsConcededCount(shotsConceded);
		entity.setShotsOnTargetConcededCount(shotsOnTargetConceded);
		entity.setBoxTouchesConcededCount(boxTouchesConceded);
		entity.setKeeperSavesCount(keeperSaves);
		entity.setSaveRate(calcRate(keeperSaves, shotsOnTargetConceded));
		entity.setBlockedShotsCount(blockedShots);
		entity.setBlockRate(calcRate(blockedShots, shotsConceded));
		entity.setClearancesCount(clearances);
		entity.setClearanceFrequency(calcRate(clearances, boxTouchesConceded));
		entity.setTacklesCount(tackles);
		entity.setInterceptionsCount(interceptions);
		entity.setDefensiveActionRate(calcDefensiveActionRate(tackles, interceptions, clearances, shotsConceded,
				boxTouchesConceded, opponentFinalThirdPasses));
		entity.setLeadStateShotsConcededIncreaseRate(calcLeadStateShotsConcededIncreaseRate(entities, false));
		entity.setPostConceded10mPressure(calcPostConceded10mPressure(entities, false));
		entity.setPostRedCard10mPressure(calcPostRedCard10mPressure(entities, false));
		entity.setCalculatedAt(LocalDateTime.now());
		entity.setNote(buildNote(base));

		return entity;
	}

	/**
	 * 共通文脈項目を設定します。
	 *
	 * @param entity 対象Entity
	 * @param matchId 試合ID
	 * @param season シーズン
	 * @param country 国
	 * @param leagueId リーグID
	 * @param leagueName リーグ名
	 * @param teamId チームID
	 * @param teamName チーム名
	 * @param opponentTeamId 対戦相手チームID
	 * @param opponentTeamName 対戦相手チーム名
	 */
	private void setCommonContext(
			MatchTeamDefenseStatsEntity entity,
			String matchId,
			String season,
			String country,
			String leagueId,
			String leagueName,
			String teamId,
			String teamName,
			String opponentTeamId,
			String opponentTeamName) {

		entity.setMatchId(trimToNull(matchId));
		entity.setSeason(trimToNull(season));
		entity.setCountry(trimToNull(country));
		entity.setLeagueId(trimToNull(leagueId));
		entity.setLeagueName(trimToNull(leagueName));
		entity.setTeamId(trimToNull(teamId));
		entity.setTeamName(trimToNull(teamName));
		entity.setOpponentTeamId(trimToNull(opponentTeamId));
		entity.setOpponentTeamName(trimToNull(opponentTeamName));
	}

	/**
	 * シーズン文字列を解決します。
	 *
	 * @param book 元データ
	 * @return シーズン
	 */
	private String resolveSeason(BookDataEntity book) {
		LocalDateTime record = parseLocalDateTime(book.getRecordTime());
		if (record != null) {
			return String.valueOf(record.getYear());
		}
		return null;
	}

	/**
	 * 暫定チームIDを解決します。
	 *
	 * @param teamName チーム名
	 * @return 暫定チームID
	 */
	private String resolveTeamId(String teamName) {
		return trimToNull(teamName);
	}

	/**
	 * 分子・分母から率を算出します。
	 *
	 * @param numerator 分子
	 * @param denominator 分母
	 * @return 算出率。分母が null または 0 の場合は null
	 */
	private BigDecimal calcRate(Integer numerator, Integer denominator) {
		if (numerator == null || denominator == null || denominator == 0) {
			return null;
		}
		return BigDecimal.valueOf(numerator)
				.divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
	}

	/**
	 * 守備アクション率を算出します。
	 *
	 * <p>
	 * 守備アクション率 = (タックル数 + インターセプト数 + クリア数)
	 * / (被シュート数 + 被ボックスタッチ数 + 相手ファイナルサードパス数)
	 * </p>
	 *
	 * @param tackles タックル数
	 * @param interceptions インターセプト数
	 * @param clearances クリア数
	 * @param shotsConceded 被シュート数
	 * @param boxTouchesConceded 被ボックスタッチ数
	 * @param opponentFinalThirdPasses 相手ファイナルサードパス数
	 * @return 守備アクション率
	 */
	private BigDecimal calcDefensiveActionRate(
			Integer tackles,
			Integer interceptions,
			Integer clearances,
			Integer shotsConceded,
			Integer boxTouchesConceded,
			Integer opponentFinalThirdPasses) {

		int numerator = nvl(tackles) + nvl(interceptions) + nvl(clearances);
		int denominator = nvl(shotsConceded) + nvl(boxTouchesConceded) + nvl(opponentFinalThirdPasses);

		if (denominator == 0) {
			return null;
		}

		return BigDecimal.valueOf(numerator)
				.divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
	}

	/**
	 * リード時の被シュート増加率を算出します。
	 *
	 * <p>
	 * 以下の式で算出します。
	 * </p>
	 * <pre>
	 * ((リード時の被シュート/分) / (非リード時の被シュート/分)) - 1
	 * </pre>
	 *
	 * @param entities 試合時系列
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return リード時の被シュート増加率
	 */
	private BigDecimal calcLeadStateShotsConcededIncreaseRate(List<BookDataEntity> entities, boolean homePerspective) {
		int leadShotsConceded = 0;
		int nonLeadShotsConceded = 0;
		int leadSeconds = 0;
		int nonLeadSeconds = 0;

		for (int i = 1; i < entities.size(); i++) {
			BookDataEntity prev = entities.get(i - 1);
			BookDataEntity curr = entities.get(i);

			int deltaSeconds = Math.max(0, resolveAsOfSeconds(curr) - resolveAsOfSeconds(prev));
			if (deltaSeconds == 0) {
				continue;
			}

			int prevTeamScore = homePerspective ? nvl(parseInteger(prev.getHomeScore())) : nvl(parseInteger(prev.getAwayScore()));
			int prevOpponentScore = homePerspective ? nvl(parseInteger(prev.getAwayScore())) : nvl(parseInteger(prev.getHomeScore()));

			int prevOpponentShots = homePerspective ? nvl(parseInteger(prev.getAwayShootAll())) : nvl(parseInteger(prev.getHomeShootAll()));
			int currOpponentShots = homePerspective ? nvl(parseInteger(curr.getAwayShootAll())) : nvl(parseInteger(curr.getHomeShootAll()));

			int deltaOpponentShots = Math.max(0, currOpponentShots - prevOpponentShots);

			if (prevTeamScore > prevOpponentScore) {
				leadShotsConceded += deltaOpponentShots;
				leadSeconds += deltaSeconds;
			} else {
				nonLeadShotsConceded += deltaOpponentShots;
				nonLeadSeconds += deltaSeconds;
			}
		}

		if (leadSeconds == 0 || nonLeadSeconds == 0 || nonLeadShotsConceded == 0) {
			return null;
		}

		BigDecimal leadRate = BigDecimal.valueOf(leadShotsConceded)
				.divide(BigDecimal.valueOf(leadSeconds), 6, RoundingMode.HALF_UP);
		BigDecimal nonLeadRate = BigDecimal.valueOf(nonLeadShotsConceded)
				.divide(BigDecimal.valueOf(nonLeadSeconds), 6, RoundingMode.HALF_UP);

		if (BigDecimal.ZERO.compareTo(nonLeadRate) == 0) {
			return null;
		}

		return leadRate.divide(nonLeadRate, 6, RoundingMode.HALF_UP)
				.subtract(BigDecimal.ONE)
				.setScale(6, RoundingMode.HALF_UP);
	}

	/**
	 * 失点後10分の被攻撃量を算出します。
	 *
	 * <p>
	 * 失点イベントごとに、以後10分間の相手攻撃圧を以下の重み付きで算出し、
	 * 試合内平均を返します。TODO
	 * </p>
	 * <pre>
	 * pressure = 被シュート増分 × 1.0
	 *          + 被枠内シュート増分 × 1.5
	 *          + 被ボックスタッチ増分 × 0.2
	 * </pre>
	 *
	 * @param entities 試合時系列
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return 失点後10分の平均被攻撃量
	 */
	private BigDecimal calcPostConceded10mPressure(List<BookDataEntity> entities, boolean homePerspective) {
		BigDecimal total = BigDecimal.ZERO;
		int eventCount = 0;

		for (int i = 1; i < entities.size(); i++) {
			BookDataEntity prev = entities.get(i - 1);
			BookDataEntity curr = entities.get(i);

			int prevOpponentScore = homePerspective ? nvl(parseInteger(prev.getAwayScore())) : nvl(parseInteger(prev.getHomeScore()));
			int currOpponentScore = homePerspective ? nvl(parseInteger(curr.getAwayScore())) : nvl(parseInteger(curr.getHomeScore()));

			if (currOpponentScore > prevOpponentScore) {
				int startSeconds = resolveAsOfSeconds(curr);
				BigDecimal pressure = calcPressureInWindow(entities, i, startSeconds, PRESSURE_WINDOW_SECONDS, homePerspective);
				if (pressure != null) {
					total = total.add(pressure);
					eventCount++;
				}
			}
		}

		if (eventCount == 0) {
			return null;
		}

		return total.divide(BigDecimal.valueOf(eventCount), 6, RoundingMode.HALF_UP);
	}

	/**
	 * 退場後10分の被圧力を算出します。
	 *
	 * <p>
	 * 自チームのレッドカード枚数が増えたタイミングを退場イベントとみなし、
	 * その後10分間の被攻撃量を算出します。
	 * </p>
	 *
	 * @param entities 試合時系列
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return 退場後10分の平均被圧力
	 */
	private BigDecimal calcPostRedCard10mPressure(List<BookDataEntity> entities, boolean homePerspective) {
		BigDecimal total = BigDecimal.ZERO;
		int eventCount = 0;

		for (int i = 1; i < entities.size(); i++) {
			BookDataEntity prev = entities.get(i - 1);
			BookDataEntity curr = entities.get(i);

			int prevRed = homePerspective ? nvl(parseInteger(prev.getHomeRedCard())) : nvl(parseInteger(prev.getAwayRedCard()));
			int currRed = homePerspective ? nvl(parseInteger(curr.getHomeRedCard())) : nvl(parseInteger(curr.getAwayRedCard()));

			if (currRed > prevRed) {
				int startSeconds = resolveAsOfSeconds(curr);
				BigDecimal pressure = calcPressureInWindow(entities, i, startSeconds, PRESSURE_WINDOW_SECONDS, homePerspective);
				if (pressure != null) {
					total = total.add(pressure);
					eventCount++;
				}
			}
		}

		if (eventCount == 0) {
			return null;
		}

		return total.divide(BigDecimal.valueOf(eventCount), 6, RoundingMode.HALF_UP);
	}

	/**
	 * 指定開始地点から一定時間窓内の被攻撃量を算出します。
	 *
	 * @param entities 試合時系列
	 * @param startIndex 開始インデックス
	 * @param startSeconds 開始秒
	 * @param windowSeconds 観測窓秒数
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return 被攻撃量
	 */
	private BigDecimal calcPressureInWindow(
			List<BookDataEntity> entities,
			int startIndex,
			int startSeconds,
			int windowSeconds,
			boolean homePerspective) {

		BookDataEntity start = entities.get(startIndex);
		BookDataEntity end = start;

		int endSeconds = startSeconds + windowSeconds;

		for (int i = startIndex; i < entities.size(); i++) {
			BookDataEntity candidate = entities.get(i);
			int candidateSeconds = resolveAsOfSeconds(candidate);
			end = candidate;
			if (candidateSeconds >= endSeconds) {
				break;
			}
		}

		int startShots = homePerspective ? nvl(parseInteger(start.getAwayShootAll())) : nvl(parseInteger(start.getHomeShootAll()));
		int endShots = homePerspective ? nvl(parseInteger(end.getAwayShootAll())) : nvl(parseInteger(end.getHomeShootAll()));

		int startShotsOnTarget = homePerspective ? nvl(parseInteger(start.getAwayShootIn())) : nvl(parseInteger(start.getHomeShootIn()));
		int endShotsOnTarget = homePerspective ? nvl(parseInteger(end.getAwayShootIn())) : nvl(parseInteger(end.getHomeShootIn()));

		int startBoxTouches = homePerspective ? nvl(parseInteger(start.getAwayBoxTouch())) : nvl(parseInteger(start.getHomeBoxTouch()));
		int endBoxTouches = homePerspective ? nvl(parseInteger(end.getAwayBoxTouch())) : nvl(parseInteger(end.getHomeBoxTouch()));

		int deltaShots = Math.max(0, endShots - startShots);
		int deltaShotsOnTarget = Math.max(0, endShotsOnTarget - startShotsOnTarget);
		int deltaBoxTouches = Math.max(0, endBoxTouches - startBoxTouches);

		return BigDecimal.valueOf(deltaShots)
				.add(BigDecimal.valueOf(deltaShotsOnTarget).multiply(new BigDecimal("1.5")))
				.add(BigDecimal.valueOf(deltaBoxTouches).multiply(new BigDecimal("0.2")))
				.setScale(6, RoundingMode.HALF_UP);
	}

	/**
	 * 試合経過秒を解決します。
	 *
	 * @param entity 元データ
	 * @return 経過秒
	 */
	private Integer resolveAsOfSeconds(BookDataEntity entity) {
		Integer timeSortSeconds = parseInteger(entity.getTimeSortSeconds());
		if (timeSortSeconds != null) {
			return timeSortSeconds;
		}
		return parseMatchTimeToSeconds(entity.getTime());
	}

	/**
	 * 備考文字列を生成します。
	 *
	 * @param book 元データ
	 * @return 備考
	 */
	private String buildNote(BookDataEntity book) {
		StringBuilder sb = new StringBuilder();

		sb.append("clearanceFrequency=clearances/boxTouchesConceded");
		sb.append(", defensiveActionRate=(tackles+interceptions+clearances)/(shotsConceded+boxTouchesConceded+opponentFinalThirdPasses)");
		sb.append(", pressureWindow=10min");

		if (trimToNull(book.getFilePath()) != null) {
			sb.append(", filePath=").append(book.getFilePath());
		}
		if (trimToNull(book.getGameLink()) != null) {
			sb.append(", gameLink=").append(book.getGameLink());
		}
		if (trimToNull(book.getGameId()) != null) {
			sb.append(", gameId=").append(book.getGameId());
		}

		return sb.toString();
	}

	/**
	 * 記録時間文字列を {@link LocalDateTime} に変換します。
	 *
	 * @param value 記録時間
	 * @return 変換結果。変換不能時は null
	 */
	private LocalDateTime parseLocalDateTime(String value) {
		String normalized = trimToNull(value);
		if (normalized == null) {
			return null;
		}
		try {
			return OffsetDateTime.parse(normalized, RECORD_TIME_FORMATTER).toLocalDateTime();
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * 文字列を整数へ変換します。
	 *
	 * @param value 対象文字列
	 * @return 整数。変換不能時は null
	 */
	private Integer parseInteger(String value) {
		String normalized = trimToNull(value);
		if (normalized == null) {
			return null;
		}
		try {
			return Integer.parseInt(normalized.replace(",", ""));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * 試合時間文字列を秒へ変換します。
	 *
	 * @param time 試合時間文字列
	 * @return 秒
	 */
	private Integer parseMatchTimeToSeconds(String time) {
		String normalized = trimToNull(time);
		if (normalized == null) {
			return 0;
		}

		if (BookMakersCommonConst.HALF_TIME.equals(normalized)) {
			return 45 * 60;
		}
		if (BookMakersCommonConst.FIN.equals(normalized)) {
			return 90 * 60;
		}

		try {
			if (normalized.contains(":")) {
				String[] parts = normalized.split(":");
				String minutePart = parts[0].trim();
				int seconds = Integer.parseInt(parts[1].trim());

				int minutes;
				if (minutePart.contains("+")) {
					String[] minParts = minutePart.split("\\+");
					minutes = 0;
					for (String p : minParts) {
						minutes += Integer.parseInt(p.trim());
					}
				} else {
					minutes = Integer.parseInt(minutePart);
				}
				return minutes * 60 + seconds;
			}
			return Integer.parseInt(normalized);
		} catch (Exception e) {
			return 0;
		}
	}

	/**
	 * null の場合 0 を返します。
	 *
	 * @param value 対象値
	 * @return 値または0
	 */
	private int nvl(Integer value) {
		return value == null ? 0 : value;
	}

	/**
	 * 文字列をtrimし、空文字ならnullを返します。
	 *
	 * @param value 対象文字列
	 * @return trim後文字列。空文字ならnull
	 */
	private String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
