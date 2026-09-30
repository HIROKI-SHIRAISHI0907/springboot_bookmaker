package dev.application.analyze.bm_m034;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.CountryLeagueParser;
import dev.common.util.ExecuteMainUtil;
import dev.common.util.RecordTimeConverter;

/**
 * BM_M034 試合中スナップショット（match_team_snapshot_fact）作成ロジック。
 *
 * <h2>何を導出するクラスか</h2>
 * <p>
 * 試合の各時点のデータ（{@link BookDataEntity} 1行 = 1試合・1時点の両チーム累計）を、
 * ホーム視点・アウェー視点の2行（{@link MatchTeamSnapshotFactEntity}）に分けて保存する。
 * リアルタイム予測・モメンタム分析・得点確率分析の元データ。
 * </p>
 * <ul>
 *   <li>試合中のデータも届いた時点ですぐ保存する（試合終了を待たない）。</li>
 *   <li>(data_seq, ha) で UPSERT するので、試合中に毎回全時点が届いても重複しない（既存の時点は上書き、新しい時点だけ追加）。</li>
 *   <li>直前の時点との差分はビュー match_team_snapshot_diff、試合ごとの最新の差分は match_team_snapshot_latest（旧 BM_M029）。</li>
 * </ul>
 *
 * <h2>1行の作り方</h2>
 * <ul>
 *   <li>対象外の行: 取得エラー行（GET_UNEXPECTED_ERROR）・PK 戦の行・通番が数値でない行。</li>
 *   <li>時点の並び順: 元データの通番（dataSeq）。</li>
 *   <li>試合時間（分）: 読める表記（FIN・ハーフタイム・"mm:ss"・"45+2'"・"23'"）だけ変換。読めなければ null（旧実装は 0 秒にしていた）。</li>
 *   <li>前半/後半: 最初のハーフタイム行の通番以前が前半、より後が後半。ハーフタイム行がまだ無ければ null
 *       （試合中に再送されたときに、ハーフタイム行が届いていれば上書きで埋まる）。</li>
 *   <li>パス・ロングパス・ファイナルサードパス・クロス・タックル（"38% (210/300)"）: 成功数・試行数・成功率に分ける。</li>
 *   <li>ポゼッション・成功率は %（0〜100。他テーブルと同じ単位）。</li>
 * </ul>
 *
 * <h2>修正履歴（旧実装からの変更）</h2>
 * <ul>
 *   <li>INSERT のみ（再送のたびに全時点が重複）→ (data_seq, ha) で UPSERT。1行ごとのトランザクション → 1試合1トランザクション＋まとめて UPSERT。</li>
 *   <li>パス系が "38% (210/300)" を整数に変換できず全部 null だった → 成功数・試行数・成功率。</li>
 *   <li>期待値（xG）・枠内ゴール期待値・ボックス内外シュート・ポスト・ヘディング・セーブ・FK・オフサイド・ファウル・スローインを追加。</li>
 *   <li>シーズン = 記録時間の年 → SeasonResolverIF（Writer）。seq_counter で採番。</li>
 *   <li>timeSortSeconds（不要）と、それで決めていた dataQualityFlag を削除。note（filePath 等の文字列）も削除。</li>
 *   <li>記録時間を固定フォーマットで読んでいた → RecordTimeConverter（UNIX 時刻・小数秒・オフセット付きも可。タイムゾーンを保持）。</li>
 *   <li>「国: リーグ - ラウンドN」形式以外のキーは無視。teamId / leagueId（名前と同じ値）を削除。</li>
 *   <li>BookMakersCommonConst の import を dev.common.constant に修正。</li>
 * </ul>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>チーム名が試合途中で変わる</b>（表記ゆれ）と、同じ試合が別の試合として扱われる（差分ビューの区切りが変わる）。</li>
 *   <li><b>元データの通番が振り直される</b>と、同じ時点が別の行として追加される。</li>
 *   <li><b>データ量</b>が多い（Writer 参照）。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Component
public class MatchTeamSnapshotFactStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = MatchTeamSnapshotFactStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = MatchTeamSnapshotFactStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M034_MATCH_TEAM_SNAPSHOT_FACT";

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M034";

	/** キーのラウンド番号（"… - ラウンド 5" の 5） */
	private static final Pattern ROUND_PATTERN = Pattern.compile("(?:ラウンド|Round)\\s*(\\d+)");

	/** 数値（最初に出てくるもの） */
	private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

	/** 成功数/試行数（"38% (210/300)" の 210/300、"210/300"） */
	private static final Pattern FRACTION_PATTERN = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");

	/** 成功率（"38% (210/300)" の 38） */
	private static final Pattern PERCENT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%");

	/** 整数の累計項目 */
	private static final List<Item<Integer>> INT_ITEMS = new ArrayList<>();

	/** 小数の項目（ポゼッション・期待値） */
	private static final List<Item<BigDecimal>> DEC_ITEMS = new ArrayList<>();

	/** 成功数/試行数の項目 */
	private static final List<FractionItem> FRACTION_ITEMS = new ArrayList<>();

	static {
		dec(BookDataEntity::getHomeBallPossesion, BookDataEntity::getAwayBallPossesion, MatchTeamSnapshotFactEntity::setPossession);
		dec(BookDataEntity::getHomeExp, BookDataEntity::getAwayExp, MatchTeamSnapshotFactEntity::setExp);
		dec(BookDataEntity::getHomeInGoalExp, BookDataEntity::getAwayInGoalExp, MatchTeamSnapshotFactEntity::setInGoalExp);

		cnt(BookDataEntity::getHomeShootAll, BookDataEntity::getAwayShootAll, MatchTeamSnapshotFactEntity::setShootAll);
		cnt(BookDataEntity::getHomeShootIn, BookDataEntity::getAwayShootIn, MatchTeamSnapshotFactEntity::setShootIn);
		cnt(BookDataEntity::getHomeShootOut, BookDataEntity::getAwayShootOut, MatchTeamSnapshotFactEntity::setShootOut);
		cnt(BookDataEntity::getHomeShootBlocked, BookDataEntity::getAwayShootBlocked, MatchTeamSnapshotFactEntity::setBlockShoot);
		cnt(BookDataEntity::getHomeBigChance, BookDataEntity::getAwayBigChance, MatchTeamSnapshotFactEntity::setBigChance);
		cnt(BookDataEntity::getHomeCornerKick, BookDataEntity::getAwayCornerKick, MatchTeamSnapshotFactEntity::setCorner);
		cnt(BookDataEntity::getHomeBoxShootIn, BookDataEntity::getAwayBoxShootIn, MatchTeamSnapshotFactEntity::setBoxShootIn);
		cnt(BookDataEntity::getHomeBoxShootOut, BookDataEntity::getAwayBoxShootOut, MatchTeamSnapshotFactEntity::setBoxShootOut);
		cnt(BookDataEntity::getHomeGoalPost, BookDataEntity::getAwayGoalPost, MatchTeamSnapshotFactEntity::setGoalPost);
		cnt(BookDataEntity::getHomeGoalHead, BookDataEntity::getAwayGoalHead, MatchTeamSnapshotFactEntity::setGoalHead);
		cnt(BookDataEntity::getHomeKeeperSave, BookDataEntity::getAwayKeeperSave, MatchTeamSnapshotFactEntity::setKeeperSave);
		cnt(BookDataEntity::getHomeFreeKick, BookDataEntity::getAwayFreeKick, MatchTeamSnapshotFactEntity::setFreeKick);
		cnt(BookDataEntity::getHomeOffSide, BookDataEntity::getAwayOffSide, MatchTeamSnapshotFactEntity::setOffside);
		cnt(BookDataEntity::getHomeFoul, BookDataEntity::getAwayFoul, MatchTeamSnapshotFactEntity::setFoul);
		cnt(BookDataEntity::getHomeYellowCard, BookDataEntity::getAwayYellowCard, MatchTeamSnapshotFactEntity::setYellowCard);
		cnt(BookDataEntity::getHomeRedCard, BookDataEntity::getAwayRedCard, MatchTeamSnapshotFactEntity::setRedCard);
		cnt(BookDataEntity::getHomeSlowIn, BookDataEntity::getAwaySlowIn, MatchTeamSnapshotFactEntity::setSlowIn);
		cnt(BookDataEntity::getHomeBoxTouch, BookDataEntity::getAwayBoxTouch, MatchTeamSnapshotFactEntity::setBoxTouch);
		cnt(BookDataEntity::getHomeClearCount, BookDataEntity::getAwayClearCount, MatchTeamSnapshotFactEntity::setClearCount);
		cnt(BookDataEntity::getHomeDuelCount, BookDataEntity::getAwayDuelCount, MatchTeamSnapshotFactEntity::setDuelCount);
		cnt(BookDataEntity::getHomeInterceptCount, BookDataEntity::getAwayInterceptCount, MatchTeamSnapshotFactEntity::setInterceptCount);

		FRACTION_ITEMS.add(new FractionItem(BookDataEntity::getHomePassCount, BookDataEntity::getAwayPassCount,
				MatchTeamSnapshotFactEntity::setPassCountSuccess, MatchTeamSnapshotFactEntity::setPassCountTry,
				MatchTeamSnapshotFactEntity::setPassCountRate));
		FRACTION_ITEMS.add(new FractionItem(BookDataEntity::getHomeLongPassCount, BookDataEntity::getAwayLongPassCount,
				MatchTeamSnapshotFactEntity::setLongPassCountSuccess, MatchTeamSnapshotFactEntity::setLongPassCountTry,
				MatchTeamSnapshotFactEntity::setLongPassCountRate));
		FRACTION_ITEMS.add(new FractionItem(BookDataEntity::getHomeFinalThirdPassCount, BookDataEntity::getAwayFinalThirdPassCount,
				MatchTeamSnapshotFactEntity::setFinalThirdPassCountSuccess, MatchTeamSnapshotFactEntity::setFinalThirdPassCountTry,
				MatchTeamSnapshotFactEntity::setFinalThirdPassCountRate));
		FRACTION_ITEMS.add(new FractionItem(BookDataEntity::getHomeCrossCount, BookDataEntity::getAwayCrossCount,
				MatchTeamSnapshotFactEntity::setCrossCountSuccess, MatchTeamSnapshotFactEntity::setCrossCountTry,
				MatchTeamSnapshotFactEntity::setCrossCountRate));
		FRACTION_ITEMS.add(new FractionItem(BookDataEntity::getHomeTackleCount, BookDataEntity::getAwayTackleCount,
				MatchTeamSnapshotFactEntity::setTackleCountSuccess, MatchTeamSnapshotFactEntity::setTackleCountTry,
				MatchTeamSnapshotFactEntity::setTackleCountRate));
	}

	/** 登録 */
	@Autowired
	private MatchTeamSnapshotFactWriter matchTeamSnapshotFactWriter;

	/** ログ管理 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		int matchCount = 0;
		int savedMatchCount = 0;
		long rowCount = 0;
		int invalidCount = 0;
		int seasonSkipCount = 0;
		int errorCount = 0;

		// シーズンのキャッシュは Writer 側（スレッド単位）。前回の残りを使わないよう開始時にも破棄する
		this.matchTeamSnapshotFactWriter.clearSeasonCache();
		try {
			if (entities == null || entities.isEmpty()) {
				debugLog(METHOD_NAME, BM_NUMBER + " 入力データなし");
				return;
			}
			for (Map.Entry<String, Map<String, List<BookDataEntity>>> outer : entities.entrySet()) {
				Map<String, List<BookDataEntity>> matchMap = outer.getValue();
				if (matchMap == null || matchMap.isEmpty()) {
					continue;
				}
				// 「国: リーグ - ラウンドN」形式でないキーは無視
				String[] cl = CountryLeagueParser.parse(outer.getKey());
				if (cl == null) {
					invalidCount += matchMap.size();
					debugLog(METHOD_NAME, BM_NUMBER + " 対象外のキーのためスキップ: " + outer.getKey());
					continue;
				}
				Integer roundNo = parseRound(outer.getKey());

				for (Map.Entry<String, List<BookDataEntity>> match : matchMap.entrySet()) {
					matchCount++;
					List<MatchTeamSnapshotFactEntity> rows = buildRows(match.getValue(), roundNo);
					if (rows.isEmpty()) {
						invalidCount++;
						continue;
					}
					try {
						rowCount += this.matchTeamSnapshotFactWriter.saveMatch(cl[0], cl[1], rows);
						savedMatchCount++;
					} catch (MatchTeamSnapshotFactWriter.SeasonNotResolvedException e) {
						// シーズン不明の国,リーグ: 何も保存されていないので、この試合だけスキップ
						seasonSkipCount++;
						debugLog(METHOD_NAME, BM_NUMBER + " シーズン取得不可のためスキップ: matchKey=" + match.getKey()
								+ " (" + e.getMessage() + ")");
					} catch (RuntimeException e) {
						// 1試合の失敗で他の試合を止めない（この試合の変更はロールバック済み）
						errorCount++;
						this.manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
								BM_NUMBER + " matchKey=" + match.getKey());
					}
				}
			}
		} finally {
			this.matchTeamSnapshotFactWriter.clearSeasonCache();
			debugLog(METHOD_NAME, BM_NUMBER + " matchCount=" + matchCount + ", savedMatchCount=" + savedMatchCount
					+ ", rowCount=" + rowCount + ", invalidCount=" + invalidCount
					+ ", seasonSkipCount=" + seasonSkipCount + ", errorCount=" + errorCount);
			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();
		}
	}

	/**
	 * 1試合分のデータから、時点 × 2行（ホーム視点・アウェー視点）を作る。
	 */
	static List<MatchTeamSnapshotFactEntity> buildRows(List<BookDataEntity> raw, Integer roundNo) {
		List<MatchTeamSnapshotFactEntity> out = new ArrayList<>();
		List<BookDataEntity> rows = sortUsableRows(raw);
		if (rows.isEmpty()) {
			return out;
		}

		// 最初のハーフタイム行
		Long htSeq = null;
		for (BookDataEntity e : rows) {
			String t = trimOrNull(e.getTime());
			if (BookMakersCommonConst.HALF_TIME.equals(t) || BookMakersCommonConst.FIRST_HALF_TIME.equals(t)) {
				htSeq = seqToLong(e.getSeq());
				break;
			}
		}

		for (BookDataEntity b : rows) {
			String home = trimOrNull(b.getHomeTeamName());
			String away = trimOrNull(b.getAwayTeamName());
			if (home == null || away == null) {
				continue;
			}
			long dataSeq = seqToLong(b.getSeq());
			Integer half = htSeq == null ? null : (dataSeq <= htSeq ? 1 : 2);
			out.add(toEntity(b, true, home, away, dataSeq, half, roundNo));
			out.add(toEntity(b, false, away, home, dataSeq, half, roundNo));
		}
		return out;
	}

	/**
	 * 1時点・1チーム視点の行を作る（season・seq・country・league は Writer で設定）。
	 */
	static MatchTeamSnapshotFactEntity toEntity(BookDataEntity b, boolean homeSide, String team, String opponent,
			long dataSeq, Integer half, Integer roundNo) {
		MatchTeamSnapshotFactEntity e = new MatchTeamSnapshotFactEntity();
		e.setMatchId(trimOrNull(b.getMatchId()));
		e.setTeam(team);
		e.setOpponent(opponent);
		e.setHa(homeSide ? "H" : "A");
		e.setDataSeq(dataSeq);
		e.setRoundNo(roundNo);
		e.setHalf(half);
		String time = trimOrNull(b.getTime());
		e.setMatchTimeLabel(time);
		e.setMatchMinute(toMinutes(time));
		e.setFinFlg(BookMakersCommonConst.FIN.equals(time));
		Timestamp rt = RecordTimeConverter.toTimestamp(b.getRecordTime());
		e.setRecordTime(rt);

		Integer hs = toInteger(parseNumber(b.getHomeScore()));
		Integer as = toInteger(parseNumber(b.getAwayScore()));
		Integer self = homeSide ? hs : as;
		Integer opp = homeSide ? as : hs;
		e.setTeamScore(self);
		e.setOpponentScore(opp);
		e.setScoreDiff(self == null || opp == null ? null : self - opp);

		for (Item<BigDecimal> it : DEC_ITEMS) {
			it.setter.accept(e, parseNumber((homeSide ? it.home : it.away).apply(b)));
		}
		for (Item<Integer> it : INT_ITEMS) {
			it.setter.accept(e, toInteger(parseNumber((homeSide ? it.home : it.away).apply(b))));
		}
		for (FractionItem it : FRACTION_ITEMS) {
			String v = (homeSide ? it.home : it.away).apply(b);
			Integer[] f = parseFraction(v);
			it.setSuccess.accept(e, f == null ? null : f[0]);
			it.setTry.accept(e, f == null ? null : f[1]);
			it.setRate.accept(e, parseRate(v, f));
		}
		return e;
	}

	/**
	 * 使える行だけを通番の数値順に並べる（null 行・取得エラー行・PK 戦の行・通番が数値でない行は除く）。
	 */
	static List<BookDataEntity> sortUsableRows(List<BookDataEntity> rows) {
		List<BookDataEntity> sorted = new ArrayList<>();
		if (rows == null) {
			return sorted;
		}
		for (BookDataEntity e : rows) {
			if (e == null || seqToLong(e.getSeq()) == Long.MAX_VALUE) {
				continue;
			}
			if (BookMakersCommonConst.GET_UNEXPECTED_ERROR.equals(e.getGoalTime())
					|| BookMakersCommonConst.GET_UNEXPECTED_ERROR.equals(e.getGoalTeamMember())) {
				continue;
			}
			String t = e.getTime();
			if (t != null && t.contains(BookMakersCommonConst.PENALTY)) {
				continue;
			}
			sorted.add(e);
		}
		sorted.sort(Comparator.comparingLong(e -> seqToLong(e.getSeq())));
		return sorted;
	}

	/**
	 * 試合時間を分に変換する（読めなければ null）。
	 * ExecuteMainUtil.convertToMinutes は読めない形式を 0 分にしてしまうため、読める形式だけ渡す。
	 */
	static BigDecimal toMinutes(String time) {
		if (time == null) {
			return null;
		}
		boolean readable = BookMakersCommonConst.FIN.equals(time)
				|| BookMakersCommonConst.HALF_TIME.equals(time) || BookMakersCommonConst.FIRST_HALF_TIME.equals(time)
				|| time.contains(":") || time.contains("+") || time.endsWith("'");
		if (!readable) {
			return null;
		}
		try {
			double d = ExecuteMainUtil.convertToMinutes(time);
			return Double.isFinite(d) ? BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP) : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** 最初に出てくる数値（"55%" → 55、"1,234" → 1234）。読めなければ null */
	static BigDecimal parseNumber(String text) {
		String s = trimOrNull(text);
		if (s == null) {
			return null;
		}
		Matcher m = NUMBER_PATTERN.matcher(s.replace(",", ""));
		return m.find() ? new BigDecimal(m.group()) : null;
	}

	/** 成功数/試行数（{成功数, 試行数}）。読めなければ null */
	static Integer[] parseFraction(String text) {
		String s = trimOrNull(text);
		if (s == null) {
			return null;
		}
		Matcher m = FRACTION_PATTERN.matcher(s.replace(",", ""));
		if (!m.find()) {
			return null;
		}
		try {
			return new Integer[] { Integer.valueOf(m.group(1)), Integer.valueOf(m.group(2)) };
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** 成功率（%）。"38%" があればそれ、無ければ 成功数 ÷ 試行数 × 100（試行数 0 は null） */
	static BigDecimal parseRate(String text, Integer[] fraction) {
		String s = trimOrNull(text);
		if (s != null) {
			Matcher m = PERCENT_PATTERN.matcher(s);
			if (m.find()) {
				return new BigDecimal(m.group(1)).setScale(1, RoundingMode.HALF_UP);
			}
		}
		if (fraction != null && fraction[1] > 0) {
			return BigDecimal.valueOf(fraction[0] * 100L).divide(BigDecimal.valueOf(fraction[1]), 1, RoundingMode.HALF_UP);
		}
		return null;
	}

	/** キーのラウンド番号（無ければ null） */
	static Integer parseRound(String key) {
		if (key == null) {
			return null;
		}
		Matcher m = ROUND_PATTERN.matcher(java.text.Normalizer.normalize(key, java.text.Normalizer.Form.NFKC));
		if (!m.find()) {
			return null;
		}
		try {
			int v = Integer.parseInt(m.group(1));
			return v > 0 ? v : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Integer toInteger(BigDecimal v) {
		return v == null ? null : v.setScale(0, RoundingMode.HALF_UP).intValue();
	}

	private static long seqToLong(String seq) {
		if (seq == null || seq.isBlank()) {
			return Long.MAX_VALUE;
		}
		try {
			return Long.parseLong(seq.trim());
		} catch (NumberFormatException e) {
			return Long.MAX_VALUE;
		}
	}

	private static String trimOrNull(String s) {
		return (s == null || s.isBlank()) ? null : s.trim();
	}

	private void debugLog(String methodName, String message) {
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, methodName, MessageCdConst.MCD00099I_LOG, message);
	}

	private static void cnt(Function<BookDataEntity, String> home, Function<BookDataEntity, String> away,
			BiConsumer<MatchTeamSnapshotFactEntity, Integer> setter) {
		INT_ITEMS.add(new Item<>(home, away, setter));
	}

	private static void dec(Function<BookDataEntity, String> home, Function<BookDataEntity, String> away,
			BiConsumer<MatchTeamSnapshotFactEntity, BigDecimal> setter) {
		DEC_ITEMS.add(new Item<>(home, away, setter));
	}

	/** 数値項目（ホーム・アウェーの取り出し方と、チーム視点の設定先） */
	private static final class Item<T> {
		private final Function<BookDataEntity, String> home;
		private final Function<BookDataEntity, String> away;
		private final BiConsumer<MatchTeamSnapshotFactEntity, T> setter;

		private Item(Function<BookDataEntity, String> home, Function<BookDataEntity, String> away,
				BiConsumer<MatchTeamSnapshotFactEntity, T> setter) {
			this.home = home;
			this.away = away;
			this.setter = setter;
		}
	}

	/** 成功数/試行数の項目 */
	private static final class FractionItem {
		private final Function<BookDataEntity, String> home;
		private final Function<BookDataEntity, String> away;
		private final BiConsumer<MatchTeamSnapshotFactEntity, Integer> setSuccess;
		private final BiConsumer<MatchTeamSnapshotFactEntity, Integer> setTry;
		private final BiConsumer<MatchTeamSnapshotFactEntity, BigDecimal> setRate;

		private FractionItem(Function<BookDataEntity, String> home, Function<BookDataEntity, String> away,
				BiConsumer<MatchTeamSnapshotFactEntity, Integer> setSuccess,
				BiConsumer<MatchTeamSnapshotFactEntity, Integer> setTry,
				BiConsumer<MatchTeamSnapshotFactEntity, BigDecimal> setRate) {
			this.home = home;
			this.away = away;
			this.setSuccess = setSuccess;
			this.setTry = setTry;
			this.setRate = setRate;
		}
	}
}