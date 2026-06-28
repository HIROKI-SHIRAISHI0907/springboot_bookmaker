package dev.application.analyze.bm_m035;

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
 * <p>BM_M035 試合単位の攻撃生成力統計を算出するサービスクラスです。</p>
 *
 * <ul>
 *   <li>入力: 国×リーグ×カード単位の {@link BookDataEntity} 群</li>
 *   <li>処理: 1試合の最終スナップショットをもとに、
 *       ホーム・アウェーそれぞれの {@link MatchTeamAttackStatsEntity} を生成</li>
 *   <li>出力: {@link MatchTeamAttackStatsWriter} を介して登録</li>
 * </ul>
 *
 * <p>
 * 本クラスでは、各試合時系列の最終状態を採用し、
 * シュート数、枠内シュート数、ボックスタッチ数、コーナー数、
 * ファイナルサードパス数、クロス数などから攻撃生成力を算出します。
 * </p>
 *
 * @author shiraishitoshio
 * @since 1.0
 */
@Component
public class MatchTeamAttackStatsStat implements AnalyzeEntityIF {

	/** プロジェクト名（ログ用） */
	private static final String PROJECT_NAME = MatchTeamAttackStatsStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名（ログ用） */
	private static final String CLASS_NAME = MatchTeamAttackStatsStat.class.getName();

	/** 実行モード（ログ用） */
	private static final String EXEC_MODE = "BM_M035_MATCH_TEAM_ATTACK_STATS";

	/** 記録時間フォーマット */
	private static final DateTimeFormatter RECORD_TIME_FORMATTER =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX");

	@Autowired
	private MatchTeamAttackStatsWriter matchTeamAttackStatsWriter;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 全ての国・リーグ・カードを走査し、
	 * 最終スナップショットから攻撃生成力統計を算出して登録します。
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
	 * 1試合分の時系列データから攻撃生成力統計を算出し、ホーム・アウェーの2件を登録します。
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

			MatchTeamAttackStatsEntity homeEntity = createHomeEntity(base, country, league);
			MatchTeamAttackStatsEntity awayEntity = createAwayEntity(base, country, league);

			matchTeamAttackStatsWriter.insert(homeEntity);
			matchTeamAttackStatsWriter.insert(awayEntity);

		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
					"攻撃生成力統計算出中に例外が発生しました。");
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
			if (BookMakersCommonConst.FIN.equals(trimToNull(entity.getTime()))) {
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
	 * ホームチーム視点の攻撃生成力統計を生成します。
	 *
	 * @param book 最終スナップショット
	 * @param country 国
	 * @param league リーグ
	 * @return ホームチームの攻撃生成力統計
	 */
	private MatchTeamAttackStatsEntity createHomeEntity(BookDataEntity book, String country, String league) {
		MatchTeamAttackStatsEntity entity = new MatchTeamAttackStatsEntity();

		setCommonContext(
				entity,
				book.getMatchId(),
				resolveSeason(book),
				country,
				league,
				league,
				resolveTeamId(book.getHomeTeamName()),
				book.getHomeTeamName(),
				resolveTeamId(book.getAwayTeamName()),
				book.getAwayTeamName());

		Integer actualMinutes = resolveActualMinutes(book);

		entity.setActualMinutes(actualMinutes);
		entity.setShotsCount(parseInteger(book.getHomeShootAll()));
		entity.setShotsPer90(calcPer90(entity.getShotsCount(), actualMinutes));
		entity.setShotsOnTargetCount(parseInteger(book.getHomeShootIn()));
		entity.setShotsOnTargetPer90(calcPer90(entity.getShotsOnTargetCount(), actualMinutes));
		entity.setBoxTouchesCount(parseInteger(book.getHomeBoxTouch()));
		entity.setBoxTouchesPer90(calcPer90(entity.getBoxTouchesCount(), actualMinutes));
		entity.setCornersCount(parseInteger(book.getHomeCornerKick()));
		entity.setCornersPer90(calcPer90(entity.getCornersCount(), actualMinutes));
		entity.setFinalThirdPassesCount(parseInteger(book.getHomeFinalThirdPassCount()));
		entity.setFinalThirdPassesPer90(calcPer90(entity.getFinalThirdPassesCount(), actualMinutes));
		entity.setCrossesCount(parseInteger(book.getHomeCrossCount()));
		entity.setCrossesPer90(calcPer90(entity.getCrossesCount(), actualMinutes));
		entity.setAttackVolumeIndex(calcAttackVolumeIndex(entity));
		entity.setCalculatedAt(LocalDateTime.now());
		entity.setSourceCount(book.getFileCount());
		entity.setNote(buildNote(book));

		return entity;
	}

	/**
	 * アウェーチーム視点の攻撃生成力統計を生成します。
	 *
	 * @param book 最終スナップショット
	 * @param country 国
	 * @param league リーグ
	 * @return アウェーチームの攻撃生成力統計
	 */
	private MatchTeamAttackStatsEntity createAwayEntity(BookDataEntity book, String country, String league) {
		MatchTeamAttackStatsEntity entity = new MatchTeamAttackStatsEntity();

		setCommonContext(
				entity,
				book.getMatchId(),
				resolveSeason(book),
				country,
				league,
				league,
				resolveTeamId(book.getAwayTeamName()),
				book.getAwayTeamName(),
				resolveTeamId(book.getHomeTeamName()),
				book.getHomeTeamName());

		Integer actualMinutes = resolveActualMinutes(book);

		entity.setActualMinutes(actualMinutes);
		entity.setShotsCount(parseInteger(book.getAwayShootAll()));
		entity.setShotsPer90(calcPer90(entity.getShotsCount(), actualMinutes));
		entity.setShotsOnTargetCount(parseInteger(book.getAwayShootIn()));
		entity.setShotsOnTargetPer90(calcPer90(entity.getShotsOnTargetCount(), actualMinutes));
		entity.setBoxTouchesCount(parseInteger(book.getAwayBoxTouch()));
		entity.setBoxTouchesPer90(calcPer90(entity.getBoxTouchesCount(), actualMinutes));
		entity.setCornersCount(parseInteger(book.getAwayCornerKick()));
		entity.setCornersPer90(calcPer90(entity.getCornersCount(), actualMinutes));
		entity.setFinalThirdPassesCount(parseInteger(book.getAwayFinalThirdPassCount()));
		entity.setFinalThirdPassesPer90(calcPer90(entity.getFinalThirdPassesCount(), actualMinutes));
		entity.setCrossesCount(parseInteger(book.getAwayCrossCount()));
		entity.setCrossesPer90(calcPer90(entity.getCrossesCount(), actualMinutes));
		entity.setAttackVolumeIndex(calcAttackVolumeIndex(entity));
		entity.setCalculatedAt(LocalDateTime.now());
		entity.setSourceCount(book.getFileCount());
		entity.setNote(buildNote(book));

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
			MatchTeamAttackStatsEntity entity,
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
	 * <p>
	 * 現時点では記録時間の年をシーズンとして利用します。
	 * </p>
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
	 * <p>
	 * 現時点ではチーム名をそのままIDとして利用します。
	 * </p>
	 *
	 * @param teamName チーム名
	 * @return 暫定チームID
	 */
	private String resolveTeamId(String teamName) {
		return trimToNull(teamName);
	}

	/**
	 * 実プレー換算分数を算出します。
	 *
	 * <p>
	 * {@code timeSortSeconds} がある場合はそれを優先し、
	 * 無い場合は試合時間文字列から秒換算します。
	 * </p>
	 *
	 * @param book 元データ
	 * @return 実プレー換算分数
	 */
	private Integer resolveActualMinutes(BookDataEntity book) {
		Integer seconds = parseInteger(book.getTimeSortSeconds());
		if (seconds == null) {
			seconds = parseMatchTimeToSeconds(book.getTime());
		}
		if (seconds == null || seconds <= 0) {
			return 90;
		}
		return Math.max(1, seconds / 60);
	}

	/**
	 * 90分換算値を算出します。
	 *
	 * @param count 元の件数
	 * @param actualMinutes 実プレー換算分数
	 * @return 90分換算値
	 */
	private BigDecimal calcPer90(Integer count, Integer actualMinutes) {
		if (count == null || actualMinutes == null || actualMinutes <= 0) {
			return null;
		}
		return BigDecimal.valueOf(count)
				.multiply(BigDecimal.valueOf(90))
				.divide(BigDecimal.valueOf(actualMinutes), 6, RoundingMode.HALF_UP);
	}

	/**
	 * 攻撃量指数を算出します。
	 *
	 * <p>
	 * 暫定式として、90分換算値を用いて以下の重みで計算します。TODO
	 * </p>
	 * <ul>
	 *   <li>シュート数: 0.40</li>
	 *   <li>枠内シュート数: 0.25</li>
	 *   <li>ボックスタッチ数: 0.15</li>
	 *   <li>コーナー数: 0.10</li>
	 *   <li>クロス数: 0.05</li>
	 *   <li>ファイナルサードパス数(10で正規化): 0.05</li>
	 * </ul>
	 *
	 * @param entity 対象Entity
	 * @return 攻撃量指数
	 */
	private BigDecimal calcAttackVolumeIndex(MatchTeamAttackStatsEntity entity) {
		BigDecimal shotsPer90 = nvl(entity.getShotsPer90());
		BigDecimal shotsOnTargetPer90 = nvl(entity.getShotsOnTargetPer90());
		BigDecimal boxTouchesPer90 = nvl(entity.getBoxTouchesPer90());
		BigDecimal cornersPer90 = nvl(entity.getCornersPer90());
		BigDecimal crossesPer90 = nvl(entity.getCrossesPer90());
		BigDecimal finalThirdPassesPer90 = nvl(entity.getFinalThirdPassesPer90());

		BigDecimal result = BigDecimal.ZERO;
		result = result.add(shotsPer90.multiply(new BigDecimal("0.40")));
		result = result.add(shotsOnTargetPer90.multiply(new BigDecimal("0.25")));
		result = result.add(boxTouchesPer90.multiply(new BigDecimal("0.15")));
		result = result.add(cornersPer90.multiply(new BigDecimal("0.10")));
		result = result.add(crossesPer90.multiply(new BigDecimal("0.05")));
		result = result.add(finalThirdPassesPer90.divide(new BigDecimal("10"), 6, RoundingMode.HALF_UP)
				.multiply(new BigDecimal("0.05")));

		return result.setScale(6, RoundingMode.HALF_UP);
	}

	/**
	 * null の場合に 0 を返します。
	 *
	 * @param value 対象値
	 * @return null でなければその値、null なら 0
	 */
	private BigDecimal nvl(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	/**
	 * 備考文字列を生成します。
	 *
	 * @param book 元データ
	 * @return 備考
	 */
	private String buildNote(BookDataEntity book) {
		StringBuilder sb = new StringBuilder();

		if (trimToNull(book.getFilePath()) != null) {
			sb.append("filePath=").append(book.getFilePath());
		}
		if (trimToNull(book.getGameLink()) != null) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append("gameLink=").append(book.getGameLink());
		}
		if (trimToNull(book.getGameId()) != null) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append("gameId=").append(book.getGameId());
		}

		return sb.length() == 0 ? null : sb.toString();
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
