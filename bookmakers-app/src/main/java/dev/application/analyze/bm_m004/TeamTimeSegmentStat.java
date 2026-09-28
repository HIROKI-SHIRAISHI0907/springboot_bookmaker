package dev.application.analyze.bm_m004;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.common.util.BookMakersCommonConst;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M004統計分析ロジック（手動データ投入の場合は適用対象外）
 *
 * <h2>何を導出するクラスか</h2>
 * <p>
 * 対象チームを基準にした、相手チームとの対戦成績を「時間帯別」に求め、team_time_segment_stats に保存する。
 * 試合時間を11の時間帯（0-10, 11-20, 21-30, 31-40, 41-45, 46-50, 51-60, 61-70, 71-80, 81-90, AT）に分け、
 * 各時間帯に対象チームが記録した値（得点・失点・期待値・ポゼッション・シュート・パスなど約40項目）を1行にする。
 * 1試合につき、ホームチーム基準（ha=H, home* 項目）とアウェーチーム基準（ha=A, away* 項目）で
 * 2チーム × 11時間帯 = 22行を保存する。
 * </p>
 *
 * <h3>値の求め方（その時間帯の値）</h3>
 * <p>
 * BookDataEntity の各項目は試合開始からの累計のため、時間帯ごとに
 * 「その時間帯の最後のスナップショットの累計 − 直前の時間帯の最後のスナップショットの累計」で求める。
 * 最初の時間帯の基準はキックオフ（すべて 0）。
 * </p>
 * <ul>
 *   <li>回数（シュート・ファウル等）・期待値・得点/失点: 上記の差分。累計が減った場合は 0。</li>
 *   <li>パス系（"X% (成功/試行)"）: 成功数・試行数それぞれの差分を取り、成功率 = 成功数 ÷ 試行数 × 100。</li>
 *   <li>ポゼッション: 累計ポゼッション P と経過分数 T から
 *       (P終了×T終了 − P開始×T開始) ÷ (T終了 − T開始) で逆算した近似値。
 *       T が進んでいない場合（前半ATと後半開始の重なり等）は、時間帯の最後の累計値をそのまま使う。</li>
 * </ul>
 *
 * <h3>時間帯の判定</h3>
 * <ul>
 *   <li>"23" / "23'" / "23:45" → 23分。"45+2" → 41-45。"90+3"・91分以上 → AT。</li>
 *   <li>ハーフタイム行 → 41-45 の最後（45分）。試合終了（FIN）行 → AT の最後（試合全体の最終累計）。</li>
 *   <li>数字を含まないその他の文字 → 集計対象外。</li>
 * </ul>
 *
 * <h3>保存方法</h3>
 * <p>
 * 1試合分の行（シーズン未設定）を {@link TeamTimeSegmentWriter#upsertMatch} に渡す。
 * シーズンは他の BM と同じく Writer 側で SeasonResolverIF（country_league_season_master）から取得して設定する。
 * シーズンが取得できない国,リーグの試合は、Writer が {@link TeamTimeSegmentWriter.SeasonNotResolvedException}
 * を投げるので、その試合だけスキップして次の試合に進む（DB エラーなどその他の例外は処理全体を止める）。
 * 一意キー（シーズン・国リーグ・対象チーム・相手チーム・H/A・時間帯）で UPSERT するため、
 * 同じ試合を再処理しても行は増えず、上書きされる（冪等）。試合終了（FIN）の試合だけを対象にする。
 * </p>
 *
 * <h2>修正履歴</h2>
 * <ul>
 *   <li>横持ち（時間帯×項目の88列、INSERT のみ）から縦持ち（1行=1時間帯、UPSERT）に作り直した。</li>
 *   <li>値を「時間帯内の累計の平均」から「その時間帯の値（増分）」に変更。</li>
 *   <li>対象項目を8項目から、BookDataEntity のチーム別項目すべて（29項目＋得点/失点）に拡大。
 *       「枠内シュート」に ボックス内シュート を入れていた取り違えも解消（shoot_in / box_shoot_in を別列に）。</li>
 *   <li>アウェーチーム基準の行と ha（H/A）を追加。</li>
 *   <li>シーズン取得を Stat から Writer に移動（他の BM の Writer と同じ構成に統一）。</li>
 *   <li>チーム名をマップのキーではなく行データから取得（キー形式によって全試合スキップされていた問題の解消）。</li>
 *   <li>FIN 行が 0-10分に入っていた不具合、45+x が 46-50 に入っていた不具合、数値変換エラーで全体が止まる不具合を解消。</li>
 * </ul>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>時間帯の境界はスナップショット次第</b>: 時間帯内の最後の行が 8分なら、8〜10分の出来事は次の時間帯に入る。
 *       データ取得間隔が粗いほど、時間帯の割り当ては不正確になる。</li>
 *   <li><b>行がない時間帯</b>: 値はすべて null（snapshot_count = 0）。その時間帯の出来事は、
 *       次にデータがある時間帯にまとめて入る。</li>
 *   <li><b>ゴール取り消し</b>: 取り消しでスコアが減った時間帯の得点は 0 とするため、取り消し前の時間帯に得点が残る
 *       （FIN 行の最終スコアと時間帯別の合計が一致しないことがある）。</li>
 *   <li><b>ポゼッションは近似値</b>: 表示される分数（アディショナルタイムの扱い）に依存する。</li>
 *   <li><b>シーズンは処理日基準</b>: シーズン切替直後に前シーズンの試合を処理すると、新シーズンとして保存される。</li>
 *   <li><b>同じ組み合わせの試合がシーズン内に複数ある場合</b>: 後から処理した試合で上書きされる。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Component
public class TeamTimeSegmentStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamTimeSegmentStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamTimeSegmentStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M004_TEAM_TIME_SEGMENT_SHOOTING";

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M004";

	/** 時間帯（並び順どおり） */
	private static final String[] SEGMENTS = {
			"0-10", "11-20", "21-30", "31-40", "41-45",
			"46-50", "51-60", "61-70", "71-80", "81-90", "AT"
	};

	/** 時間帯の添字 */
	private static final int SEG_41_45 = 4;
	private static final int SEG_AT = 10;

	/** "X% (成功/試行)" 形式 */
	private static final Pattern TRI_PATTERN =
			Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)%\\s*\\(\\s*(\\d+)\\s*/\\s*(\\d+)\\s*\\)\\s*$");

	/** 登録処理 */
	private final TeamTimeSegmentWriter writer;

	/** ログ管理クラス */
	private final ManageLoggerComponent manageLoggerComponent;

	@Autowired
	public TeamTimeSegmentStat(
			TeamTimeSegmentWriter writer,
			ManageLoggerComponent manageLoggerComponent) {
		this.writer = writer;
		this.manageLoggerComponent = manageLoggerComponent;
	}

	/**
	 * 試合終了済みの試合ごとに、ホーム基準・アウェー基準の時間帯別成績を算出して UPSERT する。
	 *
	 * @param entities 国,リーグ → 試合キー → スナップショット行 のマップ（null 可）
	 */
	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		int matchCount = 0;
		int savedMatchCount = 0;
		int skipCount = 0;

		// シーズンのキャッシュは Writer 側（スレッド単位）。前回の残りを使わないよう開始時にも破棄する
		this.writer.clearSeasonCache();
		try {
			if (entities == null || entities.isEmpty()) {
				debugLog(METHOD_NAME, BM_NUMBER + " 入力データなし");
				return;
			}

			for (Entry<String, Map<String, List<BookDataEntity>>> outerEntry : entities.entrySet()) {
				String countryLeague = outerEntry.getKey();
				Map<String, List<BookDataEntity>> matchMap = outerEntry.getValue();
				if (matchMap == null || matchMap.isEmpty()) {
					continue;
				}

				for (Entry<String, List<BookDataEntity>> matchEntry : matchMap.entrySet()) {
					matchCount++;
					String matchKey = matchEntry.getKey();

					List<BookDataEntity> sorted = sortBySeq(matchEntry.getValue());
					if (sorted.isEmpty()) {
						skipCount++;
						continue;
					}

					BookDataEntity last = sorted.get(sorted.size() - 1);
					if (!dev.common.constant.BookMakersCommonConst.FIN.equals(last.getTime())) {
						// 試合途中: 終了後のデータが届いたときに処理する
						skipCount++;
						continue;
					}

					String home = trimOrNull(last.getHomeTeamName());
					String away = trimOrNull(last.getAwayTeamName());
					if (home == null || away == null) {
						skipCount++;
						debugLog(METHOD_NAME, BM_NUMBER + " チーム名なし: matchKey=" + matchKey);
						continue;
					}

					// 時間帯ごとの最後のスナップショット
					Snapshot[] segmentEnd = new Snapshot[SEGMENTS.length];
					int[] snapshotCount = new int[SEGMENTS.length];
					collectSegmentEnds(sorted, segmentEnd, snapshotCount);

					List<TeamTimeSegmentStatsEntity> rows = new ArrayList<>(SEGMENTS.length * 2);
					rows.addAll(buildRows(countryLeague, home, away, "H", last.getMatchId(),
							segmentEnd, snapshotCount, true));
					rows.addAll(buildRows(countryLeague, away, home, "A", last.getMatchId(),
							segmentEnd, snapshotCount, false));

					try {
						this.writer.upsertMatch(rows);
						savedMatchCount++;
					} catch (TeamTimeSegmentWriter.SeasonNotResolvedException e) {
						// シーズン不明の国,リーグ: 何も保存されていないので、この試合だけスキップ
						skipCount++;
						debugLog(METHOD_NAME, BM_NUMBER + " シーズン取得不可のためスキップ: " + countryLeague
								+ ", matchKey=" + matchKey + " (" + e.getMessage() + ")");
					}
				}
			}
		} finally {
			this.writer.clearSeasonCache();
			debugLog(METHOD_NAME, BM_NUMBER + " matchCount=" + matchCount + ", savedMatchCount=" + savedMatchCount
					+ ", skipCount=" + skipCount);
			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();
		}
	}

	/**
	 * 行を時間帯に振り分け、時間帯ごとの最後のスナップショットと行数を求める。
	 * 行は通番順に並んでいる前提（後の行で上書き）。
	 */
	private void collectSegmentEnds(List<BookDataEntity> sorted, Snapshot[] segmentEnd, int[] snapshotCount) {
		double lastMinute = 0.0;
		for (BookDataEntity row : sorted) {
			Snapshot snap = toSnapshot(row, lastMinute);
			if (snap == null) {
				continue;
			}
			lastMinute = Math.max(lastMinute, snap.minute);
			segmentEnd[snap.segment] = snap;
			snapshotCount[snap.segment]++;
		}
	}

	/**
	 * 1チーム分（11時間帯）の行を作る（シーズンは Writer で設定するため未設定）。
	 *
	 * @param isHome true: home* 項目を対象チーム、away* 項目を相手チームとして使う
	 */
	private List<TeamTimeSegmentStatsEntity> buildRows(String countryLeague,
			String team, String opponent, String ha, String matchId,
			Snapshot[] segmentEnd, int[] snapshotCount, boolean isHome) {

		List<TeamTimeSegmentStatsEntity> list = new ArrayList<>(SEGMENTS.length);
		Snapshot base = null; // 直前の時間帯の最後（null = キックオフ）

		for (int seg = 0; seg < SEGMENTS.length; seg++) {
			TeamTimeSegmentStatsEntity e = new TeamTimeSegmentStatsEntity();
			e.setDataCategory(countryLeague);
			e.setTeamName(team);
			e.setOpponentTeamName(opponent);
			e.setHa(ha);
			e.setMatchId(trimOrNull(matchId));
			e.setTimeSegment(SEGMENTS[seg]);
			e.setSegmentOrder(seg);
			e.setSnapshotCount(snapshotCount[seg]);

			Snapshot end = segmentEnd[seg];
			if (end != null) {
				applyValues(e, end, base, isHome);
				base = end;
			}
			list.add(e);
		}
		return list;
	}

	/**
	 * 1時間帯分の値（base → end の増分など）を Entity に設定する。
	 */
	private void applyValues(TeamTimeSegmentStatsEntity e, Snapshot end, Snapshot base, boolean isHome) {
		BookDataEntity eRow = end.row;
		BookDataEntity bRow = (base == null) ? null : base.row;

		// 得点・失点（スコアの増分）
		Function<BookDataEntity, String> ownScore = isHome ? BookDataEntity::getHomeScore : BookDataEntity::getAwayScore;
		Function<BookDataEntity, String> oppScore = isHome ? BookDataEntity::getAwayScore : BookDataEntity::getHomeScore;
		e.setGoalFor(diffInt(eRow, bRow, ownScore));
		e.setGoalAgainst(diffInt(eRow, bRow, oppScore));

		// 期待値（小数の増分）
		e.setExp(diffDouble(eRow, bRow, pick(isHome, BookDataEntity::getHomeExp, BookDataEntity::getAwayExp)));
		e.setInGoalExp(diffDouble(eRow, bRow,
				pick(isHome, BookDataEntity::getHomeInGoalExp, BookDataEntity::getAwayInGoalExp)));

		// ポゼッション（逆算）
		e.setPossession(segmentPossession(end, base,
				pick(isHome, BookDataEntity::getHomeBallPossesion, BookDataEntity::getAwayBallPossesion)));

		// 回数（増分）
		e.setShootAll(diffInt(eRow, bRow, pick(isHome, BookDataEntity::getHomeShootAll, BookDataEntity::getAwayShootAll)));
		e.setShootIn(diffInt(eRow, bRow, pick(isHome, BookDataEntity::getHomeShootIn, BookDataEntity::getAwayShootIn)));
		e.setShootOut(diffInt(eRow, bRow, pick(isHome, BookDataEntity::getHomeShootOut, BookDataEntity::getAwayShootOut)));
		e.setShootBlocked(diffInt(eRow, bRow,
				pick(isHome, BookDataEntity::getHomeShootBlocked, BookDataEntity::getAwayShootBlocked)));
		e.setBigChance(diffInt(eRow, bRow, pick(isHome, BookDataEntity::getHomeBigChance, BookDataEntity::getAwayBigChance)));
		e.setCornerKick(diffInt(eRow, bRow, pick(isHome, BookDataEntity::getHomeCornerKick, BookDataEntity::getAwayCornerKick)));
		e.setBoxShootIn(diffInt(eRow, bRow, pick(isHome, BookDataEntity::getHomeBoxShootIn, BookDataEntity::getAwayBoxShootIn)));
		e.setBoxShootOut(diffInt(eRow, bRow,
				pick(isHome, BookDataEntity::getHomeBoxShootOut, BookDataEntity::getAwayBoxShootOut)));
		e.setGoalPost(diffInt(eRow, bRow, pick(isHome, BookDataEntity::getHomeGoalPost, BookDataEntity::getAwayGoalPost)));
		e.setGoalHead(diffInt(eRow, bRow, pick(isHome, BookDataEntity::getHomeGoalHead, BookDataEntity::getAwayGoalHead)));
		e.setKeeperSave(diffInt(eRow, bRow, pick(isHome, BookDataEntity::getHomeKeeperSave, BookDataEntity::getAwayKeeperSave)));
		e.setFreeKick(diffInt(eRow, bRow, pick(isHome, BookDataEntity::getHomeFreeKick, BookDataEntity::getAwayFreeKick)));
		e.setOffside(diffInt(eRow, bRow, pick(isHome, BookDataEntity::getHomeOffSide, BookDataEntity::getAwayOffSide)));
		e.setFoul(diffInt(eRow, bRow, pick(isHome, BookDataEntity::getHomeFoul, BookDataEntity::getAwayFoul)));
		e.setYellowCard(diffInt(eRow, bRow, pick(isHome, BookDataEntity::getHomeYellowCard, BookDataEntity::getAwayYellowCard)));
		e.setRedCard(diffInt(eRow, bRow, pick(isHome, BookDataEntity::getHomeRedCard, BookDataEntity::getAwayRedCard)));
		e.setSlowIn(diffInt(eRow, bRow, pick(isHome, BookDataEntity::getHomeSlowIn, BookDataEntity::getAwaySlowIn)));
		e.setBoxTouch(diffInt(eRow, bRow, pick(isHome, BookDataEntity::getHomeBoxTouch, BookDataEntity::getAwayBoxTouch)));
		e.setClearCount(diffInt(eRow, bRow, pick(isHome, BookDataEntity::getHomeClearCount, BookDataEntity::getAwayClearCount)));
		e.setDuelCount(diffInt(eRow, bRow, pick(isHome, BookDataEntity::getHomeDuelCount, BookDataEntity::getAwayDuelCount)));
		e.setInterceptCount(diffInt(eRow, bRow,
				pick(isHome, BookDataEntity::getHomeInterceptCount, BookDataEntity::getAwayInterceptCount)));

		// 成功数 / 試行数 / 成功率
		int[] t;
		t = diffTri(eRow, bRow, pick(isHome, BookDataEntity::getHomePassCount, BookDataEntity::getAwayPassCount));
		e.setPassSuccess(triSuccess(t));
		e.setPassTry(triTry(t));
		e.setPassRate(triRate(t));

		t = diffTri(eRow, bRow, pick(isHome, BookDataEntity::getHomeLongPassCount, BookDataEntity::getAwayLongPassCount));
		e.setLongPassSuccess(triSuccess(t));
		e.setLongPassTry(triTry(t));
		e.setLongPassRate(triRate(t));

		t = diffTri(eRow, bRow,
				pick(isHome, BookDataEntity::getHomeFinalThirdPassCount, BookDataEntity::getAwayFinalThirdPassCount));
		e.setFinalThirdPassSuccess(triSuccess(t));
		e.setFinalThirdPassTry(triTry(t));
		e.setFinalThirdPassRate(triRate(t));

		t = diffTri(eRow, bRow, pick(isHome, BookDataEntity::getHomeCrossCount, BookDataEntity::getAwayCrossCount));
		e.setCrossSuccess(triSuccess(t));
		e.setCrossTry(triTry(t));
		e.setCrossRate(triRate(t));

		t = diffTri(eRow, bRow, pick(isHome, BookDataEntity::getHomeTackleCount, BookDataEntity::getAwayTackleCount));
		e.setTackleSuccess(triSuccess(t));
		e.setTackleTry(triTry(t));
		e.setTackleRate(triRate(t));
	}

	// ===== 値の計算 =====

	private static Function<BookDataEntity, String> pick(boolean isHome,
			Function<BookDataEntity, String> home, Function<BookDataEntity, String> away) {
		return isHome ? home : away;
	}

	/** 整数の増分（end − base。base が null ならキックオフ=0。値が取れない場合は null、減少は 0） */
	private static Integer diffInt(BookDataEntity end, BookDataEntity base, Function<BookDataEntity, String> getter) {
		Double e = parseNumber(getter.apply(end));
		if (e == null) {
			return null;
		}
		double b = 0.0;
		if (base != null) {
			Double bv = parseNumber(getter.apply(base));
			if (bv == null) {
				return null;
			}
			b = bv;
		}
		long d = Math.round(e - b);
		return (int) Math.max(0L, d);
	}

	/** 小数の増分（小数2桁に丸め。減少は 0） */
	private static Double diffDouble(BookDataEntity end, BookDataEntity base, Function<BookDataEntity, String> getter) {
		Double e = parseNumber(getter.apply(end));
		if (e == null) {
			return null;
		}
		double b = 0.0;
		if (base != null) {
			Double bv = parseNumber(getter.apply(base));
			if (bv == null) {
				return null;
			}
			b = bv;
		}
		return round(Math.max(0.0, e - b), 2);
	}

	/**
	 * 時間帯のポゼッション（%）を逆算する。
	 * (P終了×T終了 − P開始×T開始) ÷ (T終了 − T開始)。T が進んでいない場合は終了時点の累計値。
	 */
	private static Double segmentPossession(Snapshot end, Snapshot base, Function<BookDataEntity, String> getter) {
		Double pe = parseNumber(getter.apply(end.row));
		if (pe == null) {
			return null;
		}
		double pb = 0.0;
		double tb = 0.0;
		if (base != null) {
			Double v = parseNumber(getter.apply(base.row));
			if (v == null) {
				return round(pe, 1);
			}
			pb = v;
			tb = base.minute;
		}
		double te = end.minute;
		if (te - tb < 0.5) {
			return round(pe, 1);
		}
		double p = (pe * te - pb * tb) / (te - tb);
		p = Math.max(0.0, Math.min(100.0, p));
		return round(p, 1);
	}

	/** "X% (成功/試行)" の増分 {成功, 試行}（取れない場合は null。減少は 0） */
	private static int[] diffTri(BookDataEntity end, BookDataEntity base, Function<BookDataEntity, String> getter) {
		int[] e = parseTri(getter.apply(end));
		if (e == null) {
			return null;
		}
		int[] b = { 0, 0 };
		if (base != null) {
			b = parseTri(getter.apply(base));
			if (b == null) {
				return null;
			}
		}
		return new int[] { Math.max(0, e[0] - b[0]), Math.max(0, e[1] - b[1]) };
	}

	private static Integer triSuccess(int[] t) {
		return t == null ? null : t[0];
	}

	private static Integer triTry(int[] t) {
		return t == null ? null : t[1];
	}

	/** 成功率（%）。試行 0 の場合は null */
	private static Double triRate(int[] t) {
		if (t == null || t[1] == 0) {
			return null;
		}
		return round(Math.min(100.0, t[0] * 100.0 / t[1]), 1);
	}

	private static int[] parseTri(String value) {
		if (value == null) {
			return null;
		}
		Matcher m = TRI_PATTERN.matcher(value);
		if (!m.matches()) {
			return null;
		}
		return new int[] { Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)) };
	}

	/** 数値変換（"%" は除去。空・変換不可は null） */
	private static Double parseNumber(String value) {
		if (value == null) {
			return null;
		}
		String s = value.replace("%", "").trim();
		if (s.isEmpty()) {
			return null;
		}
		try {
			double d = Double.parseDouble(s);
			return Double.isFinite(d) ? d : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static double round(double v, int digits) {
		double scale = Math.pow(10, digits);
		return Math.round(v * scale) / scale;
	}

	// ===== 時間帯の判定 =====

	/**
	 * 行を時間帯・経過分数に変換する（判定できない行は null）。
	 *
	 * @param row 行
	 * @param lastMinute それまでの最大経過分数（FIN 行の分数に使う）
	 */
	private Snapshot toSnapshot(BookDataEntity row, double lastMinute) {
		String time = row.getTime();
		if (time == null || time.isBlank()) {
			return null;
		}
		String s = time.trim();

		if (dev.common.constant.BookMakersCommonConst.FIN.equals(s)) {
			return new Snapshot(row, SEG_AT, Math.max(90.0, lastMinute));
		}
		if (BookMakersCommonConst.HALF_TIME.equals(s) || BookMakersCommonConst.FIRST_HALF_TIME.equals(s)) {
			return new Snapshot(row, SEG_41_45, 45.0);
		}

		try {
			if (s.contains("+")) {
				String[] parts = s.split("\\+", 2);
				String baseStr = parts[0].replaceAll("\\D+", "");
				String addStr = parts[1].replaceAll("\\D+", "");
				if (baseStr.isEmpty()) {
					return null;
				}
				int base = Integer.parseInt(baseStr);
				int add = addStr.isEmpty() ? 0 : Integer.parseInt(addStr);
				int seg = (base <= 45) ? SEG_41_45 : SEG_AT;
				return new Snapshot(row, seg, base + add);
			}

			String mStr = s.contains(":")
					? s.split(":", 2)[0].replaceAll("\\D+", "")
					: s.replaceAll("\\D+", "");
			if (mStr.isEmpty()) {
				return null;
			}
			int minute = Integer.parseInt(mStr);
			return new Snapshot(row, minuteToSegment(minute), minute);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static int minuteToSegment(int minute) {
		if (minute > 90) return SEG_AT;
		if (minute <= 10) return 0;
		if (minute <= 20) return 1;
		if (minute <= 30) return 2;
		if (minute <= 40) return 3;
		if (minute <= 45) return 4;
		if (minute <= 50) return 5;
		if (minute <= 60) return 6;
		if (minute <= 70) return 7;
		if (minute <= 80) return 8;
		return 9;
	}

	// ===== 共通 =====

	/** 通番の数値順に並べた新しいリスト（null 行は除外） */
	private static List<BookDataEntity> sortBySeq(List<BookDataEntity> rows) {
		List<BookDataEntity> sorted = new ArrayList<>();
		if (rows == null) {
			return sorted;
		}
		for (BookDataEntity e : rows) {
			if (e != null) {
				sorted.add(e);
			}
		}
		sorted.sort(Comparator.comparingLong(e -> seqToLong(e.getSeq())));
		return sorted;
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

	/**
	 * スナップショット（行・時間帯・経過分数）。
	 */
	private static final class Snapshot {
		private final BookDataEntity row;
		private final int segment;
		private final double minute;

		private Snapshot(BookDataEntity row, int segment, double minute) {
			this.row = row;
			this.segment = segment;
			this.minute = minute;
		}
	}
}