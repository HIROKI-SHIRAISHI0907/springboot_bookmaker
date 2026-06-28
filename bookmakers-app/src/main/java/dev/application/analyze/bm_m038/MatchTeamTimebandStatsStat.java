package dev.application.analyze.bm_m038;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.common.util.BookMakersCommonConst;
import dev.application.analyze.common.util.MatchTimeBandType;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * <p>BM_M038 試合時間帯別統計を算出するサービスクラスです。</p>
 *
 * <ul>
 *   <li>入力: 国×リーグ×カード単位の {@link BookDataEntity} 群</li>
 *   <li>処理: 1試合の時系列データを時間帯別に分解し、
 *       ホーム・アウェーそれぞれの {@link MatchTeamTimebandStatsEntity} を生成</li>
 *   <li>出力: {@link MatchTeamTimebandStatsWriter} を介して登録</li>
 * </ul>
 *
 * <p>
 * 本クラスでは、累積時系列データから各時間帯の差分を取り、
 * 得点数、失点数、シュート数、枠内シュート数、ボックスタッチ数、
 * コーナー数、カード数、得点率、失点率を算出します。
 * </p>
 *
 * <p>
 * また、試合全体のイベント時刻として以下も算出し、各時間帯レコードへ格納します。
 * </p>
 * <ul>
 *   <li>初失点時刻</li>
 *   <li>同点時得点時刻</li>
 *   <li>リード時失点時刻</li>
 *   <li>80分以降失点時刻</li>
 * </ul>
 *
 * @author shiraishitoshio
 * @since 1.0
 */
@Component
public class MatchTeamTimebandStatsStat implements AnalyzeEntityIF {

	/** プロジェクト名（ログ用） */
	private static final String PROJECT_NAME = MatchTeamTimebandStatsStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名（ログ用） */
	private static final String CLASS_NAME = MatchTeamTimebandStatsStat.class.getName();

	/** 実行モード（ログ用） */
	private static final String EXEC_MODE = "BM_M038_CALC_MATCH_TEAM_TIMEBAND_STATS";

	/** 記録時間フォーマット */
	private static final DateTimeFormatter RECORD_TIME_FORMATTER =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX");

	/** 80分の秒数 */
	private static final int LATE_GAME_START_SECONDS = 80 * 60;

	@Autowired
	private MatchTeamTimebandStatsWriter matchTeamTimebandStatsWriter;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 全ての国・リーグ・カードを走査し、
	 * 時間帯別統計を算出して登録します。
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
	 * 1試合分の時系列データから時間帯別統計を算出し、
	 * ホーム・アウェーの各時間帯レコードを登録します。
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

			List<BookDataEntity> sortedEntities = sortEntitiesByTime(entities);
			BookDataEntity base = resolveFinalEntity(sortedEntities);
			if (base == null) {
				return;
			}

			int maxSeconds = resolveAsOfSeconds(base);

			GameEventTimes homeEventTimes = resolveGameEventTimes(sortedEntities, true);
			GameEventTimes awayEventTimes = resolveGameEventTimes(sortedEntities, false);

			for (MatchTimeBandType timeBand : MatchTimeBandType.values()) {
				MatchTeamTimebandStatsEntity homeEntity =
						createTimebandEntity(base, sortedEntities, country, league, timeBand, true, maxSeconds, homeEventTimes);
				MatchTeamTimebandStatsEntity awayEntity =
						createTimebandEntity(base, sortedEntities, country, league, timeBand, false, maxSeconds, awayEventTimes);

				matchTeamTimebandStatsWriter.insert(homeEntity);
				matchTeamTimebandStatsWriter.insert(awayEntity);
			}

		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
					"時間帯別統計算出中に例外が発生しました。");
		}
	}

	/**
	 * 指定視点・指定時間帯の統計Entityを生成します。
	 *
	 * @param base 試合最終スナップショット
	 * @param entities 時系列データ
	 * @param country 国
	 * @param league リーグ
	 * @param timeBand 時間帯
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @param maxSeconds 試合最終経過秒
	 * @param eventTimes 試合イベント時刻
	 * @return 時間帯別統計Entity
	 */
	private MatchTeamTimebandStatsEntity createTimebandEntity(
			BookDataEntity base,
			List<BookDataEntity> entities,
			String country,
			String league,
			MatchTimeBandType timeBand,
			boolean homePerspective,
			int maxSeconds,
			GameEventTimes eventTimes) {

		MatchTeamTimebandStatsEntity entity = new MatchTeamTimebandStatsEntity();

		setCommonContext(
				entity,
				base.getMatchId(),
				resolveSeason(base),
				country,
				league,
				league,
				resolveTeamId(homePerspective ? base.getHomeTeamName() : base.getAwayTeamName()),
				homePerspective ? base.getHomeTeamName() : base.getAwayTeamName(),
				resolveTeamId(homePerspective ? base.getAwayTeamName() : base.getHomeTeamName()),
				homePerspective ? base.getAwayTeamName() : base.getHomeTeamName());

		int startExclusive = getBandStartExclusiveSeconds(timeBand);
		int configuredEnd = getBandEndInclusiveSeconds(timeBand);
		int endInclusive = isLastBand(timeBand) ? maxSeconds : Math.min(configuredEnd, maxSeconds);

		BookDataEntity beforeStart = findLatestEntityAtOrBefore(entities, startExclusive);
		BookDataEntity atEnd = findLatestEntityAtOrBefore(entities, endInclusive);

		int goals = calcDelta(
				getTeamScore(atEnd, homePerspective),
				getTeamScore(beforeStart, homePerspective));
		int goalsConceded = calcDelta(
				getOpponentScore(atEnd, homePerspective),
				getOpponentScore(beforeStart, homePerspective));
		int shots = calcDelta(
				getTeamShots(atEnd, homePerspective),
				getTeamShots(beforeStart, homePerspective));
		int shotsOnTarget = calcDelta(
				getTeamShotsOnTarget(atEnd, homePerspective),
				getTeamShotsOnTarget(beforeStart, homePerspective));
		int boxTouches = calcDelta(
				getTeamBoxTouches(atEnd, homePerspective),
				getTeamBoxTouches(beforeStart, homePerspective));
		int corners = calcDelta(
				getTeamCorners(atEnd, homePerspective),
				getTeamCorners(beforeStart, homePerspective));
		int cards = calcDelta(
				getTeamCards(atEnd, homePerspective),
				getTeamCards(beforeStart, homePerspective));

		Integer bandSeconds = calcBandDurationSeconds(startExclusive, endInclusive);
		BigDecimal scoringRate = calcRateByMinutes(goals, bandSeconds);
		BigDecimal concedingRate = calcRateByMinutes(goalsConceded, bandSeconds);

		entity.setTimeBand(timeBand);
		entity.setGoalsCount(goals);
		entity.setGoalsConcededCount(goalsConceded);
		entity.setShotsCount(shots);
		entity.setShotsOnTargetCount(shotsOnTarget);
		entity.setBoxTouchesCount(boxTouches);
		entity.setCornersCount(corners);
		entity.setCardsCount(cards);
		entity.setScoringRate(scoringRate);
		entity.setConcedingRate(concedingRate);
		entity.setFirstConcededTimeSeconds(eventTimes.getFirstConcededTimeSeconds());
		entity.setEqualStateScoringTimeSeconds(eventTimes.getEqualStateScoringTimeSeconds());
		entity.setLeadStateConcededTimeSeconds(eventTimes.getLeadStateConcededTimeSeconds());
		entity.setLateConcededTimeSeconds(eventTimes.getLateConcededTimeSeconds());
		entity.setCalculatedAt(LocalDateTime.now());

		return entity;
	}

	/**
	 * 試合全体イベント時刻を解決します。
	 *
	 * @param entities 時系列データ
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return イベント時刻情報
	 */
	private GameEventTimes resolveGameEventTimes(List<BookDataEntity> entities, boolean homePerspective) {
		GameEventTimes eventTimes = new GameEventTimes();

		for (int i = 1; i < entities.size(); i++) {
			BookDataEntity prev = entities.get(i - 1);
			BookDataEntity curr = entities.get(i);

			int prevTeamScore = getTeamScore(prev, homePerspective);
			int prevOpponentScore = getOpponentScore(prev, homePerspective);
			int currTeamScore = getTeamScore(curr, homePerspective);
			int currOpponentScore = getOpponentScore(curr, homePerspective);
			int currSeconds = resolveAsOfSeconds(curr);

			boolean teamScored = currTeamScore > prevTeamScore;
			boolean opponentScored = currOpponentScore > prevOpponentScore;

			if (opponentScored && eventTimes.getFirstConcededTimeSeconds() == null) {
				eventTimes.setFirstConcededTimeSeconds(currSeconds);
			}

			if (teamScored && prevTeamScore == prevOpponentScore
					&& eventTimes.getEqualStateScoringTimeSeconds() == null) {
				eventTimes.setEqualStateScoringTimeSeconds(currSeconds);
			}

			if (opponentScored && prevTeamScore > prevOpponentScore
					&& eventTimes.getLeadStateConcededTimeSeconds() == null) {
				eventTimes.setLeadStateConcededTimeSeconds(currSeconds);
			}

			if (opponentScored && currSeconds >= LATE_GAME_START_SECONDS
					&& eventTimes.getLateConcededTimeSeconds() == null) {
				eventTimes.setLateConcededTimeSeconds(currSeconds);
			}
		}

		return eventTimes;
	}

	/**
	 * 時系列データを試合経過秒・通番順に整列します。
	 *
	 * @param entities 元時系列データ
	 * @return 整列済みリスト
	 */
	private List<BookDataEntity> sortEntitiesByTime(List<BookDataEntity> entities) {
		List<BookDataEntity> sorted = new ArrayList<>(entities);
		sorted.sort(
				Comparator.comparingInt((BookDataEntity e) -> resolveAsOfSeconds(e))
						.thenComparingInt(e -> nvl(parseInteger(e.getSeq()))));
		return sorted;
	}

	/**
	 * 試合時系列の最終スナップショットを返します。
	 *
	 * <p>
	 * 優先順位は以下の通りです。
	 * </p>
	 * <ol>
	 *   <li>times = 終了済 の行</li>
	 *   <li>整列済みリスト末尾の行</li>
	 * </ol>
	 *
	 * @param entities 試合時系列データ
	 * @return 最終スナップショット
	 */
	private BookDataEntity resolveFinalEntity(List<BookDataEntity> entities) {
		BookDataEntity ended = null;
		for (BookDataEntity entity : entities) {
			if (BookMakersCommonConst.FIN.equals(trimToNull(entity.getTime()))) {
				ended = entity;
			}
		}
		if (ended != null) {
			return ended;
		}
		return entities.get(entities.size() - 1);
	}

	/**
	 * 指定秒以下で最も新しいスナップショットを返します。
	 *
	 * @param entities 試合時系列データ
	 * @param seconds 上限秒
	 * @return 条件に合うスナップショット。存在しない場合は null
	 */
	private BookDataEntity findLatestEntityAtOrBefore(List<BookDataEntity> entities, int seconds) {
		BookDataEntity result = null;
		for (BookDataEntity entity : entities) {
			int current = resolveAsOfSeconds(entity);
			if (current <= seconds) {
				result = entity;
			} else {
				break;
			}
		}
		return result;
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
			MatchTeamTimebandStatsEntity entity,
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
	 * 試合経過秒を解決します。
	 *
	 * @param entity 元データ
	 * @return 経過秒
	 */
	private int resolveAsOfSeconds(BookDataEntity entity) {
		Integer timeSortSeconds = parseInteger(entity.getTimeSortSeconds());
		if (timeSortSeconds != null) {
			return timeSortSeconds;
		}
		return parseMatchTimeToSeconds(entity.getTime());
	}

	/**
	 * 時間帯開始の直前秒を返します。
	 *
	 * @param timeBand 時間帯
	 * @return 開始直前秒
	 */
	private int getBandStartExclusiveSeconds(MatchTimeBandType timeBand) {
		switch (timeBand) {
		case MIN_0_15:
			return 0;
		case MIN_16_30:
			return 900;
		case MIN_31_45_PLUS:
			return 1800;
		case MIN_46_60:
			return 2700;
		case MIN_61_75:
			return 3600;
		case MIN_76_90_PLUS:
			return 4500;
		default:
			return 0;
		}
	}

	/**
	 * 時間帯終了秒を返します。
	 *
	 * @param timeBand 時間帯
	 * @return 終了秒
	 */
	private int getBandEndInclusiveSeconds(MatchTimeBandType timeBand) {
		switch (timeBand) {
		case MIN_0_15:
			return 900;
		case MIN_16_30:
			return 1800;
		case MIN_31_45_PLUS:
			return 2700;
		case MIN_46_60:
			return 3600;
		case MIN_61_75:
			return 4500;
		case MIN_76_90_PLUS:
			return Integer.MAX_VALUE;
		default:
			return Integer.MAX_VALUE;
		}
	}

	/**
	 * 最終時間帯かどうかを返します。
	 *
	 * @param timeBand 時間帯
	 * @return true: 最終時間帯
	 */
	private boolean isLastBand(MatchTimeBandType timeBand) {
		return MatchTimeBandType.MIN_76_90_PLUS.equals(timeBand);
	}

	/**
	 * 時間帯継続秒を算出します。
	 *
	 * @param startExclusive 開始直前秒
	 * @param endInclusive 終了秒
	 * @return 継続秒
	 */
	private Integer calcBandDurationSeconds(int startExclusive, int endInclusive) {
		int duration = endInclusive - startExclusive;
		return duration > 0 ? duration : null;
	}

	/**
	 * 件数を分単位で割って率を算出します。
	 *
	 * @param count 件数
	 * @param durationSeconds 継続秒
	 * @return 分あたり率
	 */
	private BigDecimal calcRateByMinutes(int count, Integer durationSeconds) {
		if (durationSeconds == null || durationSeconds <= 0) {
			return null;
		}
		BigDecimal minutes = BigDecimal.valueOf(durationSeconds)
				.divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);
		if (BigDecimal.ZERO.compareTo(minutes) == 0) {
			return null;
		}
		return BigDecimal.valueOf(count)
				.divide(minutes, 6, RoundingMode.HALF_UP);
	}

	/**
	 * 差分を算出します。
	 *
	 * @param current 現在値
	 * @param previous 直前値
	 * @return 差分
	 */
	private int calcDelta(int current, int previous) {
		return Math.max(0, current - previous);
	}

	/**
	 * 対象視点の得点数を返します。
	 *
	 * @param entity 元データ
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return 得点数
	 */
	private int getTeamScore(BookDataEntity entity, boolean homePerspective) {
		if (entity == null) {
			return 0;
		}
		return homePerspective ? nvl(parseInteger(entity.getHomeScore())) : nvl(parseInteger(entity.getAwayScore()));
	}

	/**
	 * 対象視点の失点側得点数を返します。
	 *
	 * @param entity 元データ
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return 相手得点数
	 */
	private int getOpponentScore(BookDataEntity entity, boolean homePerspective) {
		if (entity == null) {
			return 0;
		}
		return homePerspective ? nvl(parseInteger(entity.getAwayScore())) : nvl(parseInteger(entity.getHomeScore()));
	}

	/**
	 * 対象視点のシュート数を返します。
	 *
	 * @param entity 元データ
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return シュート数
	 */
	private int getTeamShots(BookDataEntity entity, boolean homePerspective) {
		if (entity == null) {
			return 0;
		}
		return homePerspective ? nvl(parseInteger(entity.getHomeShootAll())) : nvl(parseInteger(entity.getAwayShootAll()));
	}

	/**
	 * 対象視点の枠内シュート数を返します。
	 *
	 * @param entity 元データ
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return 枠内シュート数
	 */
	private int getTeamShotsOnTarget(BookDataEntity entity, boolean homePerspective) {
		if (entity == null) {
			return 0;
		}
		return homePerspective ? nvl(parseInteger(entity.getHomeShootIn())) : nvl(parseInteger(entity.getAwayShootIn()));
	}

	/**
	 * 対象視点のボックスタッチ数を返します。
	 *
	 * @param entity 元データ
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return ボックスタッチ数
	 */
	private int getTeamBoxTouches(BookDataEntity entity, boolean homePerspective) {
		if (entity == null) {
			return 0;
		}
		return homePerspective ? nvl(parseInteger(entity.getHomeBoxTouch())) : nvl(parseInteger(entity.getAwayBoxTouch()));
	}

	/**
	 * 対象視点のコーナー数を返します。
	 *
	 * @param entity 元データ
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return コーナー数
	 */
	private int getTeamCorners(BookDataEntity entity, boolean homePerspective) {
		if (entity == null) {
			return 0;
		}
		return homePerspective ? nvl(parseInteger(entity.getHomeCornerKick())) : nvl(parseInteger(entity.getAwayCornerKick()));
	}

	/**
	 * 対象視点の累積カード数を返します。
	 *
	 * <p>
	 * イエローカード数 + レッドカード数 を返します。
	 * </p>
	 *
	 * @param entity 元データ
	 * @param homePerspective true: ホーム視点 / false: アウェー視点
	 * @return 累積カード数
	 */
	private int getTeamCards(BookDataEntity entity, boolean homePerspective) {
		if (entity == null) {
			return 0;
		}
		int yellow = homePerspective ? nvl(parseInteger(entity.getHomeYellowCard())) : nvl(parseInteger(entity.getAwayYellowCard()));
		int red = homePerspective ? nvl(parseInteger(entity.getHomeRedCard())) : nvl(parseInteger(entity.getAwayRedCard()));
		return yellow + red;
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
	 * <p>
	 * 例:
	 * </p>
	 * <ul>
	 *   <li>7:50 → 470</li>
	 *   <li>45+2:10 → 2830</li>
	 *   <li>ハーフタイム → 2700</li>
	 *   <li>終了済 → 5400</li>
	 * </ul>
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

	/**
	 * 試合全体イベント時刻を保持する内部クラスです。
	 */
	private static class GameEventTimes {

		/** 初失点時刻 */
		private Integer firstConcededTimeSeconds;

		/** 同点時得点時刻 */
		private Integer equalStateScoringTimeSeconds;

		/** リード時失点時刻 */
		private Integer leadStateConcededTimeSeconds;

		/** 80分以降失点時刻 */
		private Integer lateConcededTimeSeconds;

		public Integer getFirstConcededTimeSeconds() {
			return firstConcededTimeSeconds;
		}

		public void setFirstConcededTimeSeconds(Integer firstConcededTimeSeconds) {
			this.firstConcededTimeSeconds = firstConcededTimeSeconds;
		}

		public Integer getEqualStateScoringTimeSeconds() {
			return equalStateScoringTimeSeconds;
		}

		public void setEqualStateScoringTimeSeconds(Integer equalStateScoringTimeSeconds) {
			this.equalStateScoringTimeSeconds = equalStateScoringTimeSeconds;
		}

		public Integer getLeadStateConcededTimeSeconds() {
			return leadStateConcededTimeSeconds;
		}

		public void setLeadStateConcededTimeSeconds(Integer leadStateConcededTimeSeconds) {
			this.leadStateConcededTimeSeconds = leadStateConcededTimeSeconds;
		}

		public Integer getLateConcededTimeSeconds() {
			return lateConcededTimeSeconds;
		}

		public void setLateConcededTimeSeconds(Integer lateConcededTimeSeconds) {
			this.lateConcededTimeSeconds = lateConcededTimeSeconds;
		}
	}
}
