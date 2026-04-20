package dev.application.analyze.bm_m029;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.domain.repository.bm.BookDataRepository;
import dev.application.domain.repository.bm.RealDataProcessRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.DataEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M029 リアルタイム差分保存処理
 *
 * 処理概要:
 * 1. 引数entitiesから対象試合キーを取得
 * 2. dataテーブルから dataCategory + home_team_name + away_team_name で検索
 * 3. seq降順で最新1件と1件前を取得
 * 4. 差分を計算して RealDataProcessEntity に詰め替え
 * 5. 既存があれば更新、無ければ登録
 */
@Component
public class RealDataProcessStat {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = RealDataProcessStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = RealDataProcessStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M029_REAL_DATA_PROCESS_STAT";

	/** BM番号 */
	private static final String BM_NUMBER = "BM_M029";

	/** 数値抽出 */
	private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

	/** 例: 38% (210/300) */
	private static final Pattern PERCENT_FRACTION_PATTERN = Pattern.compile(
			"([-+]?\\d+(?:\\.\\d+)?)\\s*[%％]\\s*\\(\\s*(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+(?:\\.\\d+)?)\\s*\\)");

	/** 例: 210/300 */
	private static final Pattern FRACTION_PATTERN = Pattern.compile(
			"(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+(?:\\.\\d+)?)");

	/** dataテーブル参照Repository */
	@Autowired
	private BookDataRepository dataRepository;

	/** 差分保存Repository */
	@Autowired
	private RealDataProcessRepository realDataProcessStatsRepository;

	/** 例外ラッパー */
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ログ */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 差分保存処理
	 *
	 * 想定構造:
	 * key   = 同一試合キー（gameId / matchId / home-away等）
	 * value = 同一試合候補のDataEntityリスト
	 *
	 * 実際の差分計算は data テーブルの最新2件を使用する
	 */
	public void calcStat(Map<String, List<DataEntity>> entities) {
		final String METHOD_NAME = "calcStat";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		entities.entrySet().parallelStream().forEach(entry -> {
			String matchKey = entry.getKey();
			List<DataEntity> candidates = entry.getValue();

			if (candidates == null || candidates.isEmpty()) {
				return;
			}

			DataEntity seed = findFirstNonNull(candidates);
			if (seed == null) {
				return;
			}

			String dataCategory = trimToNull(seed.getDataCategory());
			String homeTeamName = trimToNull(seed.getHomeTeamName());
			String awayTeamName = trimToNull(seed.getAwayTeamName());

			if (isBlank(dataCategory) || isBlank(homeTeamName) || isBlank(awayTeamName)) {
				String messageCd = MessageCdConst.MCD00099I_LOG;
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
						"skip: key不足 matchKey=" + matchKey
								+ ", dataCategory=" + dataCategory
								+ ", home=" + homeTeamName
								+ ", away=" + awayTeamName);
				return;
			}

			String messageCd = MessageCdConst.MCD00099I_LOG;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					"差分対象取得 matchKey=" + matchKey
							+ ", dataCategory=" + dataCategory
							+ ", home=" + homeTeamName
							+ ", away=" + awayTeamName);

			// dataテーブルから最新2件取得（seq DESC）
			List<DataEntity> latestTwo = this.dataRepository.findLatestTwoByTeams(
					dataCategory, homeTeamName, awayTeamName);

			if (latestTwo == null || latestTwo.isEmpty()) {
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
						"dataテーブル対象なし matchKey=" + matchKey
								+ ", dataCategory=" + dataCategory
								+ ", home=" + homeTeamName
								+ ", away=" + awayTeamName);
				return;
			}

			DataEntity latest = latestTwo.get(0);
			DataEntity previous = latestTwo.size() >= 2 ? latestTwo.get(1) : null;

			RealDataProcessEntity entity = buildDiffEntity(dataCategory, latest, previous);

			saveOrUpdate(entity);
		});

		this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * 差分Entity作成
	 *
	 * previous が無い場合は latest をそのまま格納
	 */
	private RealDataProcessEntity buildDiffEntity(String dataCategory, DataEntity latest, DataEntity previous) {
		RealDataProcessEntity entity = new RealDataProcessEntity();

		// 識別・付帯情報は最新値をそのまま保持
		entity.setConditionResultDataSeqId(latest.getConditionResultDataSeqId());
		entity.setDataCategory(firstNonBlank(latest.getDataCategory(), dataCategory));
		entity.setTimes(latest.getTimes());
		entity.setHomeRank(latest.getHomeRank());
		entity.setAwayRank(latest.getAwayRank());
		entity.setHomeTeamName(latest.getHomeTeamName());
		entity.setAwayTeamName(latest.getAwayTeamName());
		entity.setRecordTime(latest.getRecordTime());
		entity.setWeather(latest.getWeather());
		entity.setTemparature(latest.getTemparature());
		entity.setHumid(latest.getHumid());
		entity.setJudgeMember(latest.getJudgeMember());
		entity.setHomeManager(latest.getHomeManager());
		entity.setAwayManager(latest.getAwayManager());
		entity.setHomeFormation(latest.getHomeFormation());
		entity.setAwayFormation(latest.getAwayFormation());
		entity.setStudium(latest.getStudium());
		entity.setCapacity(latest.getCapacity());
		entity.setAudience(latest.getAudience());
		entity.setLocation(latest.getLocation());
		entity.setHomeMaxGettingScorer(latest.getHomeMaxGettingScorer());
		entity.setAwayMaxGettingScorer(latest.getAwayMaxGettingScorer());
		entity.setHomeMaxGettingScorerGameSituation(latest.getHomeMaxGettingScorerGameSituation());
		entity.setAwayMaxGettingScorerGameSituation(latest.getAwayMaxGettingScorerGameSituation());
		entity.setHomeTeamHomeScore(latest.getHomeTeamHomeScore());
		entity.setHomeTeamHomeLost(latest.getHomeTeamHomeLost());
		entity.setAwayTeamHomeScore(latest.getAwayTeamHomeScore());
		entity.setAwayTeamHomeLost(latest.getAwayTeamHomeLost());
		entity.setHomeTeamAwayScore(latest.getHomeTeamAwayScore());
		entity.setHomeTeamAwayLost(latest.getHomeTeamAwayLost());
		entity.setAwayTeamAwayScore(latest.getAwayTeamAwayScore());
		entity.setAwayTeamAwayLost(latest.getAwayTeamAwayLost());
		entity.setNoticeFlg(latest.getNoticeFlg());
		entity.setGameLink(latest.getGameLink());
		entity.setGoalTime(latest.getGoalTime());
		entity.setGoalTeamMember(latest.getGoalTeamMember());
		entity.setJudge(latest.getJudge());
		entity.setHomeTeamStyle(latest.getHomeTeamStyle());
		entity.setAwayTeamStyle(latest.getAwayTeamStyle());
		entity.setProbablity(diffValue(latest.getProbablity(), previous == null ? null : previous.getProbablity()));
		entity.setPredictionScoreTime(latest.getPredictionScoreTime());
		entity.setGameId(latest.getGameId());
		entity.setMatchId(latest.getMatchId());
		entity.setTimeSortSeconds(latest.getTimeSortSeconds());

		// 差分項目
		entity.setHomeScore(diffValue(latest.getHomeScore(), previous == null ? null : previous.getHomeScore()));
		entity.setAwayScore(diffValue(latest.getAwayScore(), previous == null ? null : previous.getAwayScore()));

		entity.setHomeExp(diffValue(latest.getHomeExp(), previous == null ? null : previous.getHomeExp()));
		entity.setAwayExp(diffValue(latest.getAwayExp(), previous == null ? null : previous.getAwayExp()));
		entity.setHomeInGoalExp(diffValue(latest.getHomeInGoalExp(), previous == null ? null : previous.getHomeInGoalExp()));
		entity.setAwayInGoalExp(diffValue(latest.getAwayInGoalExp(), previous == null ? null : previous.getAwayInGoalExp()));

		entity.setHomeDonation(diffValue(latest.getHomeDonation(), previous == null ? null : previous.getHomeDonation()));
		entity.setAwayDonation(diffValue(latest.getAwayDonation(), previous == null ? null : previous.getAwayDonation()));

		entity.setHomeShootAll(diffValue(latest.getHomeShootAll(), previous == null ? null : previous.getHomeShootAll()));
		entity.setAwayShootAll(diffValue(latest.getAwayShootAll(), previous == null ? null : previous.getAwayShootAll()));
		entity.setHomeShootIn(diffValue(latest.getHomeShootIn(), previous == null ? null : previous.getHomeShootIn()));
		entity.setAwayShootIn(diffValue(latest.getAwayShootIn(), previous == null ? null : previous.getAwayShootIn()));
		entity.setHomeShootOut(diffValue(latest.getHomeShootOut(), previous == null ? null : previous.getHomeShootOut()));
		entity.setAwayShootOut(diffValue(latest.getAwayShootOut(), previous == null ? null : previous.getAwayShootOut()));
		entity.setHomeBlockShoot(diffValue(latest.getHomeBlockShoot(), previous == null ? null : previous.getHomeBlockShoot()));
		entity.setAwayBlockShoot(diffValue(latest.getAwayBlockShoot(), previous == null ? null : previous.getAwayBlockShoot()));
		entity.setHomeBigChance(diffValue(latest.getHomeBigChance(), previous == null ? null : previous.getHomeBigChance()));
		entity.setAwayBigChance(diffValue(latest.getAwayBigChance(), previous == null ? null : previous.getAwayBigChance()));
		entity.setHomeCorner(diffValue(latest.getHomeCorner(), previous == null ? null : previous.getHomeCorner()));
		entity.setAwayCorner(diffValue(latest.getAwayCorner(), previous == null ? null : previous.getAwayCorner()));
		entity.setHomeBoxShootIn(diffValue(latest.getHomeBoxShootIn(), previous == null ? null : previous.getHomeBoxShootIn()));
		entity.setAwayBoxShootIn(diffValue(latest.getAwayBoxShootIn(), previous == null ? null : previous.getAwayBoxShootIn()));
		entity.setHomeBoxShootOut(diffValue(latest.getHomeBoxShootOut(), previous == null ? null : previous.getHomeBoxShootOut()));
		entity.setAwayBoxShootOut(diffValue(latest.getAwayBoxShootOut(), previous == null ? null : previous.getAwayBoxShootOut()));
		entity.setHomeGoalPost(diffValue(latest.getHomeGoalPost(), previous == null ? null : previous.getHomeGoalPost()));
		entity.setAwayGoalPost(diffValue(latest.getAwayGoalPost(), previous == null ? null : previous.getAwayGoalPost()));
		entity.setHomeGoalHead(diffValue(latest.getHomeGoalHead(), previous == null ? null : previous.getHomeGoalHead()));
		entity.setAwayGoalHead(diffValue(latest.getAwayGoalHead(), previous == null ? null : previous.getAwayGoalHead()));
		entity.setHomeKeeperSave(diffValue(latest.getHomeKeeperSave(), previous == null ? null : previous.getHomeKeeperSave()));
		entity.setAwayKeeperSave(diffValue(latest.getAwayKeeperSave(), previous == null ? null : previous.getAwayKeeperSave()));
		entity.setHomeFreeKick(diffValue(latest.getHomeFreeKick(), previous == null ? null : previous.getHomeFreeKick()));
		entity.setAwayFreeKick(diffValue(latest.getAwayFreeKick(), previous == null ? null : previous.getAwayFreeKick()));
		entity.setHomeOffside(diffValue(latest.getHomeOffside(), previous == null ? null : previous.getHomeOffside()));
		entity.setAwayOffside(diffValue(latest.getAwayOffside(), previous == null ? null : previous.getAwayOffside()));
		entity.setHomeFoul(diffValue(latest.getHomeFoul(), previous == null ? null : previous.getHomeFoul()));
		entity.setAwayFoul(diffValue(latest.getAwayFoul(), previous == null ? null : previous.getAwayFoul()));
		entity.setHomeYellowCard(diffValue(latest.getHomeYellowCard(), previous == null ? null : previous.getHomeYellowCard()));
		entity.setAwayYellowCard(diffValue(latest.getAwayYellowCard(), previous == null ? null : previous.getAwayYellowCard()));
		entity.setHomeRedCard(diffValue(latest.getHomeRedCard(), previous == null ? null : previous.getHomeRedCard()));
		entity.setAwayRedCard(diffValue(latest.getAwayRedCard(), previous == null ? null : previous.getAwayRedCard()));
		entity.setHomeSlowIn(diffValue(latest.getHomeSlowIn(), previous == null ? null : previous.getHomeSlowIn()));
		entity.setAwaySlowIn(diffValue(latest.getAwaySlowIn(), previous == null ? null : previous.getAwaySlowIn()));
		entity.setHomeBoxTouch(diffValue(latest.getHomeBoxTouch(), previous == null ? null : previous.getHomeBoxTouch()));
		entity.setAwayBoxTouch(diffValue(latest.getAwayBoxTouch(), previous == null ? null : previous.getAwayBoxTouch()));

		entity.setHomePassCount(diffValue(latest.getHomePassCount(), previous == null ? null : previous.getHomePassCount()));
		entity.setAwayPassCount(diffValue(latest.getAwayPassCount(), previous == null ? null : previous.getAwayPassCount()));
		entity.setHomeLongPassCount(diffValue(latest.getHomeLongPassCount(), previous == null ? null : previous.getHomeLongPassCount()));
		entity.setAwayLongPassCount(diffValue(latest.getAwayLongPassCount(), previous == null ? null : previous.getAwayLongPassCount()));
		entity.setHomeFinalThirdPassCount(diffValue(latest.getHomeFinalThirdPassCount(), previous == null ? null : previous.getHomeFinalThirdPassCount()));
		entity.setAwayFinalThirdPassCount(diffValue(latest.getAwayFinalThirdPassCount(), previous == null ? null : previous.getAwayFinalThirdPassCount()));
		entity.setHomeCrossCount(diffValue(latest.getHomeCrossCount(), previous == null ? null : previous.getHomeCrossCount()));
		entity.setAwayCrossCount(diffValue(latest.getAwayCrossCount(), previous == null ? null : previous.getAwayCrossCount()));
		entity.setHomeTackleCount(diffValue(latest.getHomeTackleCount(), previous == null ? null : previous.getHomeTackleCount()));
		entity.setAwayTackleCount(diffValue(latest.getAwayTackleCount(), previous == null ? null : previous.getAwayTackleCount()));
		entity.setHomeClearCount(diffValue(latest.getHomeClearCount(), previous == null ? null : previous.getHomeClearCount()));
		entity.setAwayClearCount(diffValue(latest.getAwayClearCount(), previous == null ? null : previous.getAwayClearCount()));
		entity.setHomeDuelCount(diffValue(latest.getHomeDuelCount(), previous == null ? null : previous.getHomeDuelCount()));
		entity.setAwayDuelCount(diffValue(latest.getAwayDuelCount(), previous == null ? null : previous.getAwayDuelCount()));
		entity.setHomeInterceptCount(diffValue(latest.getHomeInterceptCount(), previous == null ? null : previous.getHomeInterceptCount()));
		entity.setAwayInterceptCount(diffValue(latest.getAwayInterceptCount(), previous == null ? null : previous.getAwayInterceptCount()));

		return entity;
	}

	/**
	 * insert / update
	 *
	 * 更新判定キー:
	 * data_category + home_team_name + away_team_name
	 */
	private synchronized void saveOrUpdate(RealDataProcessEntity entity) {
		final String METHOD_NAME = "saveOrUpdate";

		int count = this.realDataProcessStatsRepository.countByUniqueKey(
				entity.getDataCategory(),
				entity.getHomeTeamName(),
				entity.getAwayTeamName());

		int result;
		String messageCd;

		if (count > 0) {
			result = this.realDataProcessStatsRepository.updateByUniqueKey(entity);
			messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
		} else {
			result = this.realDataProcessStatsRepository.insert(entity);
			messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
		}

		if (result != 1) {
			String errorCd = MessageCdConst.MCD00007E_INSERT_FAILED;
			this.rootCauseWrapper.throwUnexpectedRowCount(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					errorCd,
					1, result,
					null
			);
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 登録/更新件数: 1件 (" + setLoggerFillChar(entity) + ")");
	}

	/**
	 * 差分文字列作成
	 */
	private String diffValue(String current, String previous) {
		String cur = trimToNull(current);
		String prev = trimToNull(previous);

		if (cur == null && prev == null) {
			return null;
		}
		if (cur != null && prev == null) {
			return cur;
		}
		if (cur == null) {
			return null;
		}

		PercentFraction curPf = parsePercentFraction(cur);
		PercentFraction prevPf = parsePercentFraction(prev);
		if (curPf != null && prevPf != null) {
			BigDecimal p = curPf.percent.subtract(prevPf.percent);
			BigDecimal n = curPf.numerator.subtract(prevPf.numerator);
			BigDecimal d = curPf.denominator.subtract(prevPf.denominator);
			return formatDecimal(p) + "% (" + formatDecimal(n) + "/" + formatDecimal(d) + ")";
		}

		Fraction curFr = parseFraction(cur);
		Fraction prevFr = parseFraction(prev);
		if (curFr != null && prevFr != null) {
			BigDecimal n = curFr.numerator.subtract(prevFr.numerator);
			BigDecimal d = curFr.denominator.subtract(prevFr.denominator);
			return formatDecimal(n) + "/" + formatDecimal(d);
		}

		BigDecimal curNum = extractFirstNumber(cur);
		BigDecimal prevNum = extractFirstNumber(prev);
		if (curNum != null && prevNum != null) {
			BigDecimal diff = curNum.subtract(prevNum);
			if (containsPercent(cur) || containsPercent(prev)) {
				return formatDecimal(diff) + "%";
			}
			return formatDecimal(diff);
		}

		return cur;
	}

	private PercentFraction parsePercentFraction(String text) {
		if (isBlank(text)) {
			return null;
		}
		Matcher m = PERCENT_FRACTION_PATTERN.matcher(text);
		if (!m.find()) {
			return null;
		}

		PercentFraction pf = new PercentFraction();
		pf.percent = new BigDecimal(m.group(1));
		pf.numerator = new BigDecimal(m.group(2));
		pf.denominator = new BigDecimal(m.group(3));
		return pf;
	}

	private Fraction parseFraction(String text) {
		if (isBlank(text)) {
			return null;
		}
		Matcher m = FRACTION_PATTERN.matcher(text);
		if (!m.find()) {
			return null;
		}

		Fraction f = new Fraction();
		f.numerator = new BigDecimal(m.group(1));
		f.denominator = new BigDecimal(m.group(2));
		return f;
	}

	private BigDecimal extractFirstNumber(String text) {
		if (isBlank(text)) {
			return null;
		}
		Matcher m = NUMBER_PATTERN.matcher(text);
		if (!m.find()) {
			return null;
		}
		return new BigDecimal(m.group());
	}

	private boolean containsPercent(String text) {
		return text != null && (text.contains("%") || text.contains("％"));
	}

	private String formatDecimal(BigDecimal value) {
		if (value == null) {
			return null;
		}
		BigDecimal normalized = value.stripTrailingZeros();
		if (normalized.scale() < 0) {
			normalized = normalized.setScale(0);
		}
		return normalized.toPlainString();
	}

	private DataEntity findFirstNonNull(List<DataEntity> list) {
		for (DataEntity e : list) {
			if (e != null) {
				return e;
			}
		}
		return null;
	}

	private String setLoggerFillChar(RealDataProcessEntity entity) {
		StringBuilder sb = new StringBuilder();
		sb.append("国,リーグ: ").append(entity.getDataCategory()).append(", ");
		sb.append("ホームチーム: ").append(entity.getHomeTeamName()).append(", ");
		sb.append("アウェーチーム: ").append(entity.getAwayTeamName()).append(", ");
		sb.append("試合時間: ").append(entity.getTimes()).append(", ");
		sb.append("記録時間: ").append(entity.getRecordTime()).append(", ");
		sb.append("gameId: ").append(entity.getGameId()).append(", ");
		sb.append("matchId: ").append(entity.getMatchId());
		return sb.toString();
	}

	private String firstNonBlank(String a, String b) {
		if (!isBlank(a)) {
			return a;
		}
		return b;
	}

	private String trimToNull(String str) {
		if (str == null) {
			return null;
		}
		String s = str.trim();
		return s.isEmpty() ? null : s;
	}

	private boolean isBlank(String str) {
		return str == null || str.trim().isEmpty();
	}

	private static class PercentFraction {
		private BigDecimal percent;
		private BigDecimal numerator;
		private BigDecimal denominator;
	}

	private static class Fraction {
		private BigDecimal numerator;
		private BigDecimal denominator;
	}
}