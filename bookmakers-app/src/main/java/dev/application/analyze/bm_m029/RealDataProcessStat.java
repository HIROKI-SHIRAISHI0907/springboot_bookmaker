package dev.application.analyze.bm_m029;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.BookDataRepository;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.DataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.CountryLeagueParser;
import dev.common.util.RecordTimeConverter;

/**
 * BM_M029 リアルタイム差分（最新のデータと1つ前のデータで、どれだけ増えたか）。
 *
 * <h2>何を導出するクラスか</h2>
 * <p>
 * 試合ごとに data テーブルの最新のデータと1つ前のデータを取り、各項目が「どれだけ増えたか」を求めて
 * real_data_process に保存する（1試合1行。新しいデータが来るたびに上書き＝最新の差分だけを持つ）。
 * 画面で「直前の更新から何が増えたか」を表示するためのデータ。
 * </p>
 * <ul>
 *   <li>数値項目（シュート数・期待値・ポゼッションなど）: 最新 − 1つ前。</li>
 *   <li>パス・ロングパス・ファイナルサードパス・クロス・タックル（"38% (210/300)"）: 成功数の増加・試行数の増加と、
 *       その区間の成功率（成功数の増加 ÷ 試行数の増加 × 100）。% 同士の引き算はしない。</li>
 *   <li>区間: 1つ前の試合時間（prevTimes）〜 最新の試合時間（times）も保存する。</li>
 *   <li>1つ前のデータが無い場合（その試合の最初のデータ）: hasPrevious = false、増加量は試合開始（0）からの値＝最新の累計値。</li>
 *   <li>現在のスコア・順位・天気などは最新の値。</li>
 * </ul>
 *
 * <h2>処理の流れ</h2>
 * <ol>
 *   <li>引数の各試合から、対戦チームカテゴリ・ホーム・アウェーを取る（同じ試合が複数あっても1回だけ処理）。</li>
 *   <li>「国: リーグ - ラウンドN」形式でないカテゴリは無視（全 BM 共通のルール）。</li>
 *   <li>data テーブルから最新の2件を取る（{@link BookDataRepository#findLatestTwoByTeams}）。</li>
 *   <li>保存しない場合（下記）を除き、差分を計算して {@link RealDataProcessWriter} で保存（1試合＝1トランザクション）。</li>
 * </ol>
 * <p>保存しない場合（前回保存した差分をそのまま残す）:</p>
 * <ul>
 *   <li>最新・1つ前のどちらかが取得エラー行（GET_UNEXPECTED_ERROR）または PK 戦の行。次のデータが来たときに保存される。</li>
 *   <li>最新・1つ前がどちらも試合終了（FIN）。試合終了後に同じデータが続けて来ると差分 0 で上書きされ、
 *       試合終了直前の差分が消えるため。</li>
 *   <li>match_id が空（1試合1行のキーのため）。シーズンが取得できない国・リーグ。</li>
 * </ul>
 *
 * <h2>修正履歴（旧実装からの変更）</h2>
 * <ul>
 *   <li>差分を数値型で保存（旧: 文字列）。成功率系は % の引き算をやめ、成功数・試行数・区間の成功率に分けた。</li>
 *   <li>1つ前が無いときに累計値を差分として入れていたのを、hasPrevious で区別できるようにした。区間（prevTimes〜times）を追加。</li>
 *   <li>取得エラー行・PK 戦の行・試合終了後の重複データで、意味のある差分を上書きしないようにした。</li>
 *   <li>「国: リーグ - ラウンドN」形式以外を無視。シーズン・seq（seq_counter 採番）に対応（Writer）。</li>
 *   <li>parallelStream（共通 ForkJoinPool で DB を並列に呼び、1試合の例外で全体が止まる）をやめ、
 *       1試合ずつ処理して、失敗した試合だけログを出して次へ進むようにした。</li>
 *   <li>timeSortSeconds を削除（不要）。match_id の空チェックを Stat だけにした。</li>
 * </ul>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>findLatestTwoByTeams の並び順</b>: 「最新2件」は SQL の ORDER BY に依存する。seq が文字列型だと
 *       "10" &lt; "9" になり、最新を取り違える。seq を数値として並べているか確認すること。
 *       検索キーも match_id ではなく カテゴリ + チーム名。</li>
 *   <li><b>最新の差分だけを持つ</b>: 前回の処理から2件以上データが増えていても、最新と1つ前の差分しか残らない。</li>
 *   <li><b>マイナスの増加量</b>: データの訂正などで最新の値が小さくなった場合、そのまま（マイナスで）保存する。</li>
 *   <li><b>数値が読めない値は null</b>（例: 空・"-"）。片方だけ読めない場合も null。</li>
 * </ul>
 *
 * @author shiraishitoshio
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

	/** 数値（最初に出てくるもの） */
	private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

	/** 成功数/試行数（例: "38% (210/300)" の 210/300、"210/300"） */
	private static final Pattern FRACTION_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+(?:\\.\\d+)?)");

	/** 整数の項目（得点・シュート数など） */
	private static final List<Item<Integer>> INT_ITEMS = new ArrayList<>();

	/** 小数の項目（期待値・ポゼッション） */
	private static final List<Item<BigDecimal>> DEC_ITEMS = new ArrayList<>();

	/** 成功数/試行数の項目（パスなど） */
	private static final List<FractionItem> FRACTION_ITEMS = new ArrayList<>();

	static {
		intItem(DataEntity::getHomeScore, DataEntity::getAwayScore, RealDataProcessEntity::setHomeScore, RealDataProcessEntity::setAwayScore);
		intItem(DataEntity::getHomeShootAll, DataEntity::getAwayShootAll, RealDataProcessEntity::setHomeShootAll, RealDataProcessEntity::setAwayShootAll);
		intItem(DataEntity::getHomeShootIn, DataEntity::getAwayShootIn, RealDataProcessEntity::setHomeShootIn, RealDataProcessEntity::setAwayShootIn);
		intItem(DataEntity::getHomeShootOut, DataEntity::getAwayShootOut, RealDataProcessEntity::setHomeShootOut, RealDataProcessEntity::setAwayShootOut);
		intItem(DataEntity::getHomeBlockShoot, DataEntity::getAwayBlockShoot, RealDataProcessEntity::setHomeBlockShoot, RealDataProcessEntity::setAwayBlockShoot);
		intItem(DataEntity::getHomeBigChance, DataEntity::getAwayBigChance, RealDataProcessEntity::setHomeBigChance, RealDataProcessEntity::setAwayBigChance);
		intItem(DataEntity::getHomeCorner, DataEntity::getAwayCorner, RealDataProcessEntity::setHomeCorner, RealDataProcessEntity::setAwayCorner);
		intItem(DataEntity::getHomeBoxShootIn, DataEntity::getAwayBoxShootIn, RealDataProcessEntity::setHomeBoxShootIn, RealDataProcessEntity::setAwayBoxShootIn);
		intItem(DataEntity::getHomeBoxShootOut, DataEntity::getAwayBoxShootOut, RealDataProcessEntity::setHomeBoxShootOut, RealDataProcessEntity::setAwayBoxShootOut);
		intItem(DataEntity::getHomeGoalPost, DataEntity::getAwayGoalPost, RealDataProcessEntity::setHomeGoalPost, RealDataProcessEntity::setAwayGoalPost);
		intItem(DataEntity::getHomeGoalHead, DataEntity::getAwayGoalHead, RealDataProcessEntity::setHomeGoalHead, RealDataProcessEntity::setAwayGoalHead);
		intItem(DataEntity::getHomeKeeperSave, DataEntity::getAwayKeeperSave, RealDataProcessEntity::setHomeKeeperSave, RealDataProcessEntity::setAwayKeeperSave);
		intItem(DataEntity::getHomeFreeKick, DataEntity::getAwayFreeKick, RealDataProcessEntity::setHomeFreeKick, RealDataProcessEntity::setAwayFreeKick);
		intItem(DataEntity::getHomeOffside, DataEntity::getAwayOffside, RealDataProcessEntity::setHomeOffside, RealDataProcessEntity::setAwayOffside);
		intItem(DataEntity::getHomeFoul, DataEntity::getAwayFoul, RealDataProcessEntity::setHomeFoul, RealDataProcessEntity::setAwayFoul);
		intItem(DataEntity::getHomeYellowCard, DataEntity::getAwayYellowCard, RealDataProcessEntity::setHomeYellowCard, RealDataProcessEntity::setAwayYellowCard);
		intItem(DataEntity::getHomeRedCard, DataEntity::getAwayRedCard, RealDataProcessEntity::setHomeRedCard, RealDataProcessEntity::setAwayRedCard);
		intItem(DataEntity::getHomeSlowIn, DataEntity::getAwaySlowIn, RealDataProcessEntity::setHomeSlowIn, RealDataProcessEntity::setAwaySlowIn);
		intItem(DataEntity::getHomeBoxTouch, DataEntity::getAwayBoxTouch, RealDataProcessEntity::setHomeBoxTouch, RealDataProcessEntity::setAwayBoxTouch);
		intItem(DataEntity::getHomeClearCount, DataEntity::getAwayClearCount, RealDataProcessEntity::setHomeClearCount, RealDataProcessEntity::setAwayClearCount);
		intItem(DataEntity::getHomeDuelCount, DataEntity::getAwayDuelCount, RealDataProcessEntity::setHomeDuelCount, RealDataProcessEntity::setAwayDuelCount);
		intItem(DataEntity::getHomeInterceptCount, DataEntity::getAwayInterceptCount, RealDataProcessEntity::setHomeInterceptCount, RealDataProcessEntity::setAwayInterceptCount);

		decItem(DataEntity::getHomeExp, DataEntity::getAwayExp, RealDataProcessEntity::setHomeExp, RealDataProcessEntity::setAwayExp);
		decItem(DataEntity::getHomeInGoalExp, DataEntity::getAwayInGoalExp, RealDataProcessEntity::setHomeInGoalExp, RealDataProcessEntity::setAwayInGoalExp);
		decItem(DataEntity::getHomeDonation, DataEntity::getAwayDonation, RealDataProcessEntity::setHomeDonation, RealDataProcessEntity::setAwayDonation);

		FRACTION_ITEMS.add(new FractionItem(DataEntity::getHomePassCount, DataEntity::getAwayPassCount,
				RealDataProcessEntity::setHomePassCountSuccess, RealDataProcessEntity::setHomePassCountTry, RealDataProcessEntity::setHomePassCountRate,
				RealDataProcessEntity::setAwayPassCountSuccess, RealDataProcessEntity::setAwayPassCountTry, RealDataProcessEntity::setAwayPassCountRate));
		FRACTION_ITEMS.add(new FractionItem(DataEntity::getHomeLongPassCount, DataEntity::getAwayLongPassCount,
				RealDataProcessEntity::setHomeLongPassCountSuccess, RealDataProcessEntity::setHomeLongPassCountTry, RealDataProcessEntity::setHomeLongPassCountRate,
				RealDataProcessEntity::setAwayLongPassCountSuccess, RealDataProcessEntity::setAwayLongPassCountTry, RealDataProcessEntity::setAwayLongPassCountRate));
		FRACTION_ITEMS.add(new FractionItem(DataEntity::getHomeFinalThirdPassCount, DataEntity::getAwayFinalThirdPassCount,
				RealDataProcessEntity::setHomeFinalThirdPassCountSuccess, RealDataProcessEntity::setHomeFinalThirdPassCountTry, RealDataProcessEntity::setHomeFinalThirdPassCountRate,
				RealDataProcessEntity::setAwayFinalThirdPassCountSuccess, RealDataProcessEntity::setAwayFinalThirdPassCountTry, RealDataProcessEntity::setAwayFinalThirdPassCountRate));
		FRACTION_ITEMS.add(new FractionItem(DataEntity::getHomeCrossCount, DataEntity::getAwayCrossCount,
				RealDataProcessEntity::setHomeCrossCountSuccess, RealDataProcessEntity::setHomeCrossCountTry, RealDataProcessEntity::setHomeCrossCountRate,
				RealDataProcessEntity::setAwayCrossCountSuccess, RealDataProcessEntity::setAwayCrossCountTry, RealDataProcessEntity::setAwayCrossCountRate));
		FRACTION_ITEMS.add(new FractionItem(DataEntity::getHomeTackleCount, DataEntity::getAwayTackleCount,
				RealDataProcessEntity::setHomeTackleCountSuccess, RealDataProcessEntity::setHomeTackleCountTry, RealDataProcessEntity::setHomeTackleCountRate,
				RealDataProcessEntity::setAwayTackleCountSuccess, RealDataProcessEntity::setAwayTackleCountTry, RealDataProcessEntity::setAwayTackleCountRate));
	}

	/** dataテーブル参照Repository */
	@Autowired
	private BookDataRepository dataRepository;

	/** 差分保存Writer */
	@Autowired
	private RealDataProcessWriter realDataProcessWriter;

	/** ログ */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 差分保存処理。
	 *
	 * @param entities key = 試合キー（ログ用）/ value = その試合のデータ（対戦チームカテゴリ・ホーム・アウェーを取るために使う）
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void calcStat(Map<String, List<DataEntity>> entities) {
		final String METHOD_NAME = "calcStat";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		int matchCount = 0;
		int savedCount = 0;
		int skipCount = 0;
		int errorCount = 0;

		// シーズンのキャッシュは Writer 側（スレッド単位）。前回の残りを使わないよう開始時にも破棄する
		this.realDataProcessWriter.clearSeasonCache();
		try {
			if (entities == null || entities.isEmpty()) {
				debugLog(METHOD_NAME, BM_NUMBER + " 入力データなし");
				return;
			}

			Set<String> done = new HashSet<>();
			for (Map.Entry<String, List<DataEntity>> entry : entities.entrySet()) {
				String matchKey = entry.getKey();
				DataEntity seed = findFirstNonNull(entry.getValue());
				if (seed == null) {
					continue;
				}
				String dataCategory = trimToNull(seed.getDataCategory());
				String home = trimToNull(seed.getHomeTeamName());
				String away = trimToNull(seed.getAwayTeamName());
				if (dataCategory == null || home == null || away == null) {
					skipCount++;
					debugLog(METHOD_NAME, BM_NUMBER + " skip: キー不足 matchKey=" + matchKey
							+ ", dataCategory=" + dataCategory + ", home=" + home + ", away=" + away);
					continue;
				}
				// 同じ試合が複数のキーで来ても1回だけ処理する
				if (!done.add(dataCategory + "\u0000" + home + "\u0000" + away)) {
					continue;
				}
				matchCount++;

				try {
					if (processMatch(METHOD_NAME, matchKey, dataCategory, home, away)) {
						savedCount++;
					} else {
						skipCount++;
					}
				} catch (RealDataProcessWriter.SeasonNotResolvedException e) {
					// シーズン不明の国,リーグ: 何も保存されていないので、この試合だけスキップ
					skipCount++;
					debugLog(METHOD_NAME, BM_NUMBER + " skip: シーズン取得不可 matchKey=" + matchKey + " (" + e.getMessage() + ")");
				} catch (RuntimeException e) {
					// 1試合の失敗で他の試合を止めない（この試合の変更は Writer のトランザクションでロールバック済み）
					errorCount++;
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
							BM_NUMBER + " matchKey=" + matchKey + ", dataCategory=" + dataCategory
									+ ", home=" + home + ", away=" + away);
				}
			}
		} finally {
			this.realDataProcessWriter.clearSeasonCache();
			debugLog(METHOD_NAME, BM_NUMBER + " matchCount=" + matchCount + ", savedCount=" + savedCount
					+ ", skipCount=" + skipCount + ", errorCount=" + errorCount);
			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();
		}
	}

	/**
	 * 1試合分: 最新2件を取り、保存できる場合は差分を保存する。
	 *
	 * @return 保存したら true、保存しない場合（ログ済み）は false
	 */
	private boolean processMatch(String methodName, String matchKey, String dataCategory, String home, String away) {
		// 「国: リーグ - ラウンドN」形式でなければ無視
		String[] cl = CountryLeagueParser.parse(dataCategory);
		if (cl == null) {
			debugLog(methodName, BM_NUMBER + " skip: 対象外のカテゴリ " + dataCategory);
			return false;
		}

		List<DataEntity> latestTwo = this.dataRepository.findLatestTwoByTeams(dataCategory, home, away);
		if (latestTwo == null || latestTwo.isEmpty() || latestTwo.get(0) == null) {
			debugLog(methodName, BM_NUMBER + " skip: data テーブルに対象なし matchKey=" + matchKey);
			return false;
		}
		DataEntity latest = latestTwo.get(0);
		DataEntity previous = latestTwo.size() >= 2 ? latestTwo.get(1) : null;

		String reason = skipReason(latest, previous);
		if (reason != null) {
			debugLog(methodName, BM_NUMBER + " skip: " + reason + " matchKey=" + matchKey);
			return false;
		}
		if (trimToNull(latest.getMatchId()) == null) {
			debugLog(methodName, BM_NUMBER + " skip: matchId が空 matchKey=" + matchKey + ", gameId=" + latest.getGameId());
			return false;
		}

		RealDataProcessEntity entity = buildDiffEntity(dataCategory, latest, previous);
		this.realDataProcessWriter.save(cl[0], cl[1], entity);
		return true;
	}

	/**
	 * 保存しない理由（保存してよければ null）。
	 */
	static String skipReason(DataEntity latest, DataEntity previous) {
		if (isUnusable(latest)) {
			return "最新が取得エラー行または PK 戦の行";
		}
		if (previous != null && isUnusable(previous)) {
			return "1つ前が取得エラー行または PK 戦の行";
		}
		if (previous != null
				&& BookMakersCommonConst.FIN.equals(trimToNull(latest.getTimes()))
				&& BookMakersCommonConst.FIN.equals(trimToNull(previous.getTimes()))) {
			return "試合終了後の重複データ（試合終了時点の差分を残す）";
		}
		return null;
	}

	/** 取得エラー行・PK 戦の行 */
	private static boolean isUnusable(DataEntity e) {
		if (BookMakersCommonConst.GET_UNEXPECTED_ERROR.equals(e.getGoalTime())
				|| BookMakersCommonConst.GET_UNEXPECTED_ERROR.equals(e.getGoalTeamMember())) {
			return true;
		}
		String t = e.getTimes();
		return t != null && t.contains(BookMakersCommonConst.PENALTY);
	}

	/**
	 * 差分 Entity を作る（previous が null なら増加量 = 最新の値）。
	 */
	static RealDataProcessEntity buildDiffEntity(String dataCategory, DataEntity latest, DataEntity previous) {
		RealDataProcessEntity e = new RealDataProcessEntity();

		// 識別・区間
		e.setMatchId(trimToNull(latest.getMatchId()));
		e.setDataCategory(dataCategory);
		e.setGameId(latest.getGameId());
		e.setGameLink(latest.getGameLink());
		e.setConditionResultDataSeqId(latest.getConditionResultDataSeqId());
		e.setHomeTeamName(trimToNull(latest.getHomeTeamName()));
		e.setAwayTeamName(trimToNull(latest.getAwayTeamName()));
		e.setHomeRank(latest.getHomeRank());
		e.setAwayRank(latest.getAwayRank());
		e.setHasPrevious(previous != null);
		e.setPrevTimes(previous == null ? null : previous.getTimes());
		e.setTimes(latest.getTimes());
		e.setPrevRecordTime(previous == null ? null : RecordTimeConverter.toTimestamp(previous.getRecordTime()));
		e.setRecordTime(RecordTimeConverter.toTimestamp(latest.getRecordTime()));
		e.setHomeCurrentScore(toInteger(parseNumber(latest.getHomeScore())));
		e.setAwayCurrentScore(toInteger(parseNumber(latest.getAwayScore())));

		// 増加量
		for (Item<Integer> it : INT_ITEMS) {
			it.setHome.accept(e, toInteger(diff(it.home, latest, previous)));
			it.setAway.accept(e, toInteger(diff(it.away, latest, previous)));
		}
		for (Item<BigDecimal> it : DEC_ITEMS) {
			it.setHome.accept(e, diff(it.home, latest, previous));
			it.setAway.accept(e, diff(it.away, latest, previous));
		}
		for (FractionItem it : FRACTION_ITEMS) {
			setFraction(it.home, latest, previous, e, it.setHomeSuccess, it.setHomeTry, it.setHomeRate);
			setFraction(it.away, latest, previous, e, it.setAwaySuccess, it.setAwayTry, it.setAwayRate);
		}

		// 確率: 最新の値と、数値が読めれば増減
		e.setProbablity(latest.getProbablity());
		e.setProbablityDiff(diff(DataEntity::getProbablity, latest, previous));

		// 付帯情報（最新の値）
		e.setPredictionScoreTime(latest.getPredictionScoreTime());
		e.setWeather(latest.getWeather());
		e.setTemperature(latest.getTemparature());
		e.setHumid(latest.getHumid());
		e.setJudgeMember(latest.getJudgeMember());
		e.setHomeManager(latest.getHomeManager());
		e.setAwayManager(latest.getAwayManager());
		e.setHomeFormation(latest.getHomeFormation());
		e.setAwayFormation(latest.getAwayFormation());
		e.setStudium(latest.getStudium());
		e.setCapacity(latest.getCapacity());
		e.setAudience(latest.getAudience());
		e.setLocation(latest.getLocation());
		e.setHomeMaxGettingScorer(latest.getHomeMaxGettingScorer());
		e.setAwayMaxGettingScorer(latest.getAwayMaxGettingScorer());
		e.setHomeMaxGettingScorerGameSituation(latest.getHomeMaxGettingScorerGameSituation());
		e.setAwayMaxGettingScorerGameSituation(latest.getAwayMaxGettingScorerGameSituation());
		e.setHomeTeamHomeScore(latest.getHomeTeamHomeScore());
		e.setHomeTeamHomeLost(latest.getHomeTeamHomeLost());
		e.setAwayTeamHomeScore(latest.getAwayTeamHomeScore());
		e.setAwayTeamHomeLost(latest.getAwayTeamHomeLost());
		e.setHomeTeamAwayScore(latest.getHomeTeamAwayScore());
		e.setHomeTeamAwayLost(latest.getHomeTeamAwayLost());
		e.setAwayTeamAwayScore(latest.getAwayTeamAwayScore());
		e.setAwayTeamAwayLost(latest.getAwayTeamAwayLost());
		e.setNoticeFlg(latest.getNoticeFlg());
		e.setGoalTime(latest.getGoalTime());
		e.setGoalTeamMember(latest.getGoalTeamMember());
		e.setJudge(latest.getJudge());
		e.setHomeTeamStyle(latest.getHomeTeamStyle());
		e.setAwayTeamStyle(latest.getAwayTeamStyle());
		return e;
	}

	/**
	 * 数値の増加量（最新 − 1つ前）。1つ前が無ければ最新の値。どちらかが読めなければ null。
	 */
	static BigDecimal diff(Function<DataEntity, String> getter, DataEntity latest, DataEntity previous) {
		BigDecimal cur = parseNumber(getter.apply(latest));
		if (cur == null) {
			return null;
		}
		if (previous == null) {
			return cur;
		}
		BigDecimal prev = parseNumber(getter.apply(previous));
		return prev == null ? null : cur.subtract(prev);
	}

	/**
	 * 成功数・試行数の増加と区間の成功率を設定する（"38% (210/300)" 形式）。
	 */
	private static void setFraction(Function<DataEntity, String> getter, DataEntity latest, DataEntity previous,
			RealDataProcessEntity e,
			BiConsumer<RealDataProcessEntity, Integer> setSuccess,
			BiConsumer<RealDataProcessEntity, Integer> setTry,
			BiConsumer<RealDataProcessEntity, BigDecimal> setRate) {
		BigDecimal[] cur = parseFraction(getter.apply(latest));
		BigDecimal[] prev = previous == null ? new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO }
				: parseFraction(getter.apply(previous));
		if (cur == null || prev == null) {
			setSuccess.accept(e, null);
			setTry.accept(e, null);
			setRate.accept(e, null);
			return;
		}
		BigDecimal success = cur[0].subtract(prev[0]);
		BigDecimal tries = cur[1].subtract(prev[1]);
		setSuccess.accept(e, toInteger(success));
		setTry.accept(e, toInteger(tries));
		setRate.accept(e, rate(success, tries));
	}

	/** 区間の成功率（%、小数1桁）。試行数の増加が 0 以下、または成功数の増加がマイナスなら null */
	static BigDecimal rate(BigDecimal success, BigDecimal tries) {
		if (tries.signum() <= 0 || success.signum() < 0) {
			return null;
		}
		return success.multiply(BigDecimal.valueOf(100)).divide(tries, 1, RoundingMode.HALF_UP);
	}

	/** 最初に出てくる数値（"55%" → 55）。読めなければ null */
	static BigDecimal parseNumber(String text) {
		String s = trimToNull(text);
		if (s == null) {
			return null;
		}
		Matcher m = NUMBER_PATTERN.matcher(s.replace(",", ""));
		return m.find() ? new BigDecimal(m.group()) : null;
	}

	/** 成功数/試行数（{成功数, 試行数}）。読めなければ null */
	static BigDecimal[] parseFraction(String text) {
		String s = trimToNull(text);
		if (s == null) {
			return null;
		}
		Matcher m = FRACTION_PATTERN.matcher(s.replace(",", ""));
		return m.find() ? new BigDecimal[] { new BigDecimal(m.group(1)), new BigDecimal(m.group(2)) } : null;
	}

	private static Integer toInteger(BigDecimal v) {
		return v == null ? null : v.setScale(0, RoundingMode.HALF_UP).intValue();
	}

	private static DataEntity findFirstNonNull(List<DataEntity> list) {
		if (list == null) {
			return null;
		}
		for (DataEntity e : list) {
			if (e != null) {
				return e;
			}
		}
		return null;
	}

	private static String trimToNull(String str) {
		if (str == null) {
			return null;
		}
		String s = str.trim();
		return s.isEmpty() ? null : s;
	}

	private void debugLog(String methodName, String message) {
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, methodName, MessageCdConst.MCD00099I_LOG, message);
	}

	private static void intItem(Function<DataEntity, String> home, Function<DataEntity, String> away,
			BiConsumer<RealDataProcessEntity, Integer> setHome, BiConsumer<RealDataProcessEntity, Integer> setAway) {
		INT_ITEMS.add(new Item<>(home, away, setHome, setAway));
	}

	private static void decItem(Function<DataEntity, String> home, Function<DataEntity, String> away,
			BiConsumer<RealDataProcessEntity, BigDecimal> setHome, BiConsumer<RealDataProcessEntity, BigDecimal> setAway) {
		DEC_ITEMS.add(new Item<>(home, away, setHome, setAway));
	}

	/** 数値項目（ホーム・アウェーの取り出し方と設定先） */
	private static final class Item<T> {
		private final Function<DataEntity, String> home;
		private final Function<DataEntity, String> away;
		private final BiConsumer<RealDataProcessEntity, T> setHome;
		private final BiConsumer<RealDataProcessEntity, T> setAway;

		private Item(Function<DataEntity, String> home, Function<DataEntity, String> away,
				BiConsumer<RealDataProcessEntity, T> setHome, BiConsumer<RealDataProcessEntity, T> setAway) {
			this.home = home;
			this.away = away;
			this.setHome = setHome;
			this.setAway = setAway;
		}
	}

	/** 成功数/試行数の項目 */
	private static final class FractionItem {
		private final Function<DataEntity, String> home;
		private final Function<DataEntity, String> away;
		private final BiConsumer<RealDataProcessEntity, Integer> setHomeSuccess;
		private final BiConsumer<RealDataProcessEntity, Integer> setHomeTry;
		private final BiConsumer<RealDataProcessEntity, BigDecimal> setHomeRate;
		private final BiConsumer<RealDataProcessEntity, Integer> setAwaySuccess;
		private final BiConsumer<RealDataProcessEntity, Integer> setAwayTry;
		private final BiConsumer<RealDataProcessEntity, BigDecimal> setAwayRate;

		private FractionItem(Function<DataEntity, String> home, Function<DataEntity, String> away,
				BiConsumer<RealDataProcessEntity, Integer> setHomeSuccess,
				BiConsumer<RealDataProcessEntity, Integer> setHomeTry,
				BiConsumer<RealDataProcessEntity, BigDecimal> setHomeRate,
				BiConsumer<RealDataProcessEntity, Integer> setAwaySuccess,
				BiConsumer<RealDataProcessEntity, Integer> setAwayTry,
				BiConsumer<RealDataProcessEntity, BigDecimal> setAwayRate) {
			this.home = home;
			this.away = away;
			this.setHomeSuccess = setHomeSuccess;
			this.setHomeTry = setHomeTry;
			this.setHomeRate = setHomeRate;
			this.setAwaySuccess = setAwaySuccess;
			this.setAwayTry = setAwayTry;
			this.setAwayRate = setAwayRate;
		}
	}
}