package dev.application.analyze.bm_m036;

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
 * <p>BM_M036 試合単位の攻撃効率統計を算出するサービスクラスです。</p>
 *
 * <ul>
 *   <li>入力: 国×リーグ×カード単位の {@link BookDataEntity} 群</li>
 *   <li>処理: 1試合の最終スナップショットをもとに、
 *       ホーム・アウェーそれぞれの {@link MatchTeamEfficiencyStatsEntity} を生成</li>
 *   <li>出力: {@link MatchTeamEfficiencyStatsWriter} を介して登録</li>
 * </ul>
 *
 * <p>
 * 本クラスでは、少ない攻撃でどれだけ質の高い機会を作れているかを定量化するために、
 * 枠内率、枠外率、ボックス内シュート率、ボックスタッチ→シュート変換率、
 * シュート→得点変換率、枠内シュート→得点変換率などを算出します。
 * </p>
 *
 * <p>
 * なお、元データにはセットプレー由来シュート数・得点数の直接項目がないため、
 * 当該項目は未算出（null）とし、備考へ記録します。
 * </p>
 *
 * @author shiraishitoshio
 * @since 1.0
 */
@Component
public class MatchTeamEfficiencyStatsStat implements AnalyzeEntityIF {

	/** プロジェクト名（ログ用） */
	private static final String PROJECT_NAME = MatchTeamEfficiencyStatsStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名（ログ用） */
	private static final String CLASS_NAME = MatchTeamEfficiencyStatsStat.class.getName();

	/** 実行モード（ログ用） */
	private static final String EXEC_MODE = "BM_M036_MATCH_TEAM_EFFICIENCY_STATS";

	/** 記録時間フォーマット */
	private static final DateTimeFormatter RECORD_TIME_FORMATTER =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX");

	@Autowired
	private MatchTeamEfficiencyStatsWriter matchTeamEfficiencyStatsWriter;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 全ての国・リーグ・カードを走査し、
	 * 最終スナップショットから攻撃効率統計を算出して登録します。
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
	 * 1試合分の時系列データから攻撃効率統計を算出し、ホーム・アウェーの2件を登録します。
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

			MatchTeamEfficiencyStatsEntity homeEntity = createHomeEntity(base, country, league);
			MatchTeamEfficiencyStatsEntity awayEntity = createAwayEntity(base, country, league);

			matchTeamEfficiencyStatsWriter.insert(homeEntity);
			matchTeamEfficiencyStatsWriter.insert(awayEntity);

		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
					"攻撃効率統計算出中に例外が発生しました。");
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
	 * ホームチーム視点の攻撃効率統計を生成します。
	 *
	 * @param book 最終スナップショット
	 * @param country 国
	 * @param league リーグ
	 * @return ホームチームの攻撃効率統計
	 */
	private MatchTeamEfficiencyStatsEntity createHomeEntity(BookDataEntity book, String country, String league) {
		MatchTeamEfficiencyStatsEntity entity = new MatchTeamEfficiencyStatsEntity();

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

		entity.setGoalsCount(parseInteger(book.getHomeScore()));
		entity.setShotsCount(parseInteger(book.getHomeShootAll()));
		entity.setShotsOnTargetCount(parseInteger(book.getHomeShootIn()));
		entity.setShotsOffTargetCount(parseInteger(book.getHomeShootOut()));
		entity.setBoxShotsCount(parseInteger(book.getHomeBoxShootIn()));
		entity.setNonBoxShotsCount(parseInteger(book.getHomeBoxShootOut()));
		entity.setBoxTouchesCount(parseInteger(book.getHomeBoxTouch()));
		entity.setSetPieceShotsCount(null);
		entity.setSetPieceGoalsCount(null);

		entity.setOnTargetRate(calcRate(entity.getShotsOnTargetCount(), entity.getShotsCount()));
		entity.setOffTargetRate(calcRate(entity.getShotsOffTargetCount(), entity.getShotsCount()));
		entity.setBoxShotRate(calcRate(entity.getBoxShotsCount(), entity.getShotsCount()));
		entity.setBoxTouchToShotRate(calcRate(entity.getShotsCount(), entity.getBoxTouchesCount()));
		entity.setShotToGoalRate(calcRate(entity.getGoalsCount(), entity.getShotsCount()));
		entity.setOnTargetToGoalRate(calcRate(entity.getGoalsCount(), entity.getShotsOnTargetCount()));
		entity.setSetPieceDependencyRate(calcRate(entity.getSetPieceShotsCount(), entity.getShotsCount()));

		entity.setEfficiencyNote(buildEfficiencyNote(book));
		entity.setCalculatedAt(LocalDateTime.now());

		return entity;
	}

	/**
	 * アウェーチーム視点の攻撃効率統計を生成します。
	 *
	 * @param book 最終スナップショット
	 * @param country 国
	 * @param league リーグ
	 * @return アウェーチームの攻撃効率統計
	 */
	private MatchTeamEfficiencyStatsEntity createAwayEntity(BookDataEntity book, String country, String league) {
		MatchTeamEfficiencyStatsEntity entity = new MatchTeamEfficiencyStatsEntity();

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

		entity.setGoalsCount(parseInteger(book.getAwayScore()));
		entity.setShotsCount(parseInteger(book.getAwayShootAll()));
		entity.setShotsOnTargetCount(parseInteger(book.getAwayShootIn()));
		entity.setShotsOffTargetCount(parseInteger(book.getAwayShootOut()));
		entity.setBoxShotsCount(parseInteger(book.getAwayBoxShootIn()));
		entity.setNonBoxShotsCount(parseInteger(book.getAwayBoxShootOut()));
		entity.setBoxTouchesCount(parseInteger(book.getAwayBoxTouch()));
		entity.setSetPieceShotsCount(null);
		entity.setSetPieceGoalsCount(null);

		entity.setOnTargetRate(calcRate(entity.getShotsOnTargetCount(), entity.getShotsCount()));
		entity.setOffTargetRate(calcRate(entity.getShotsOffTargetCount(), entity.getShotsCount()));
		entity.setBoxShotRate(calcRate(entity.getBoxShotsCount(), entity.getShotsCount()));
		entity.setBoxTouchToShotRate(calcRate(entity.getShotsCount(), entity.getBoxTouchesCount()));
		entity.setShotToGoalRate(calcRate(entity.getGoalsCount(), entity.getShotsCount()));
		entity.setOnTargetToGoalRate(calcRate(entity.getGoalsCount(), entity.getShotsOnTargetCount()));
		entity.setSetPieceDependencyRate(calcRate(entity.getSetPieceShotsCount(), entity.getShotsCount()));

		entity.setEfficiencyNote(buildEfficiencyNote(book));
		entity.setCalculatedAt(LocalDateTime.now());

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
			MatchTeamEfficiencyStatsEntity entity,
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
	 * 効率評価備考を生成します。
	 *
	 * @param book 元データ
	 * @return 備考
	 */
	private String buildEfficiencyNote(BookDataEntity book) {
		StringBuilder sb = new StringBuilder();

		sb.append("setPieceShotsCount/setPieceGoalsCountは元データに直接列がないため未算出");

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
