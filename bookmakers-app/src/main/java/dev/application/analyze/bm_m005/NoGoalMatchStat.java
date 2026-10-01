package dev.application.analyze.bm_m005;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.common.error.AnalyzeFieldChecker;
import dev.application.analyze.common.util.BookMakersCommonConst;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M005統計分析ロジック（無得点試合）
 *
 * <h2>何を導出するクラスか</h2>
 * <p>
 * <b>試合終了時のスコアが 0-0 だった試合</b>について、3つの時点のスナップショット（BookDataEntity の全項目）を
 * no_goal_match_stats に保存する。無得点試合の特徴（シュート数・期待値・ポゼッションなど）を後から分析するためのデータ。
 * </p>
 * <ul>
 *   <li>START: 試合開始時（通番が最小の行）</li>
 *   <li>HT: ハーフタイム（時間がハーフタイムの行。複数あれば通番が最大の行。なければ保存しない）</li>
 *   <li>END: 試合終了時（通番が最大の行＝試合終了（FIN）行）</li>
 * </ul>
 *
 * <h3>対象の判定</h3>
 * <ol>
 *   <li>通番の数値順に並べ、最後の行の時間が試合終了（FIN）の試合だけを対象にする（試合途中は次回以降に処理）。</li>
 *   <li>最後の行（FIN）のスコアがホーム・アウェーとも 0 の試合だけを保存する。</li>
 * </ol>
 *
 * <h3>保存方法</h3>
 * <p>
 * 1試合分（最大3行）を {@link NoGoalMatchWriter#upsertMatch} で1トランザクションで UPSERT する。
 * シーズンと seq（「&lt;シーズン&gt;-&lt;枝番&gt;」）は Writer で設定する。
 * 同じ試合を再処理しても行は増えず上書きされる（冪等）。
 * シーズンが取得できない国,リーグの試合は、その試合だけスキップして次に進む（その他の例外は処理全体を止める）。
 * </p>
 *
 * <h2>修正履歴</h2>
 * <ul>
 *   <li><b>無得点の判定を「行」から「試合」に変更</b>: 以前はスコア 0-0 の行だけを残していたため、
 *       途中で得点が入った試合も「得点前までの行」が無得点試合として保存されていた。</li>
 *   <li><b>試合終了（FIN）の試合だけを対象に</b>: 以前は試合途中のデータも保存し、データが流れてくるたびに同じ行が重複登録されていた。</li>
 *   <li><b>INSERT → 1試合1トランザクションの UPSERT</b>（Writer）。seq は元データの通番ではなく seq_counter で採番。</li>
 *   <li><b>時点（snapshot_type）を保存</b>: 行が1つしかない試合で同じ行が3回登録される問題も解消（同じ行は1回だけ）。</li>
 *   <li>通番を数値として比較（文字列比較だと "10" &lt; "9" になる）。スコアは前後の空白を除いて判定。</li>
 *   <li>parallelStream + synchronizedList を廃止（不要な並列化で登録順が毎回変わっていた）。</li>
 *   <li>「無得点データなし」のログのためだけに全行をリストに展開していた処理を、件数のログに変更。</li>
 *   <li>入力・リスト・行が null の場合の NullPointerException を解消。</li>
 * </ul>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>ハーフタイムの行がない試合</b>: HT は保存しない（START と END の2行）。</li>
 *   <li><b>START が試合開始直後とは限らない</b>: データ取得が途中から始まった試合では、最初に取れた時点になる。</li>
 *   <li><b>FIN 行のスコアが空・数値以外</b>: 0-0 と判断できないため対象外（ログに件数を出す）。</li>
 *   <li><b>延長戦・PK戦</b>: FIN 行のスコアで判定するため、データの持ち方によっては延長の得点を含む/含まない。</li>
 *   <li><b>シーズンは処理日基準</b>。同じ組み合わせの試合がシーズン内に複数回ある場合も、ラウンド番号で別の試合として保存する（Writer 参照）。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Component
public class NoGoalMatchStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = NoGoalMatchStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = NoGoalMatchStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M005_NO_GOAL_MATCH_DATA";

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M005";

	/** BookDataToNoGoalMatchMapperマッパークラス */
	@Autowired
	private BookDataToNoGoalMatchMapper bookDataToNoGoalMatchMapper;

	/** 登録処理 */
	@Autowired
	private NoGoalMatchWriter noGoalMatchWriter;

	/** ログ管理クラス */
	/** 使えない項目の記録（どの項目でスキップしたか） */
	@Autowired
	private AnalyzeFieldChecker analyzeFieldChecker;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 試合終了済みで最終スコアが 0-0 の試合ごとに、START / HT / END のスナップショットを UPSERT する。
	 *
	 * @param entities 国,リーグ → 試合キー → スナップショット行 のマップ（null 可）
	 */
	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";

		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		int matchCount = 0;
		int notFinishedCount = 0;
		int scoredCount = 0;
		int invalidCount = 0;
		int seasonSkipCount = 0;
		int savedMatchCount = 0;

		// シーズンのキャッシュは Writer 側（スレッド単位）。前回の残りを使わないよう開始時にも破棄する
		this.noGoalMatchWriter.clearSeasonCache();
		try {
			if (entities == null || entities.isEmpty()) {
				debugLog(METHOD_NAME, BM_NUMBER + " 入力データなし");
				return;
			}

			for (Entry<String, Map<String, List<BookDataEntity>>> outerEntry : entities.entrySet()) {
				Map<String, List<BookDataEntity>> matchMap = outerEntry.getValue();
				if (matchMap == null || matchMap.isEmpty()) {
					continue;
				}

				for (Entry<String, List<BookDataEntity>> matchEntry : matchMap.entrySet()) {
					matchCount++;
					String matchKey = matchEntry.getKey();

					List<BookDataEntity> sorted = sortBySeq(matchEntry.getValue());
					if (sorted.isEmpty()) {
						invalidCount++;
						continue;
					}

					BookDataEntity end = sorted.get(sorted.size() - 1);
					if (!dev.common.constant.BookMakersCommonConst.FIN.equals(trimOrNull(end.getTime()))) {
						// 試合途中: 終了後のデータが届いたときに処理する
						notFinishedCount++;
						continue;
					}

					Integer homeScore = parseScore(end.getHomeScore());
					Integer awayScore = parseScore(end.getAwayScore());
					if (homeScore == null || awayScore == null) {
						invalidCount++;
						debugLog(METHOD_NAME, BM_NUMBER + " 最終スコアが判定できないためスキップ: matchKey=" + matchKey
								+ ", " + setLoggerFillChar(end));
						// どの項目が原因かを analyze_error_match に記録（画面で確認できるように）
						this.analyzeFieldChecker.checkTeamsAndScore(BM_NUMBER, outerEntry.getKey(), end);
						continue;
					}
					if (homeScore != 0 || awayScore != 0) {
						scoredCount++;
						continue;
					}
					if (trimOrNull(end.getHomeTeamName()) == null || trimOrNull(end.getAwayTeamName()) == null
							|| trimOrNull(end.getGameTeamCategory()) == null) {
						invalidCount++;
						debugLog(METHOD_NAME, BM_NUMBER + " 国リーグ・チーム名なし: matchKey=" + matchKey);
						// どの項目が原因かを analyze_error_match に記録（画面で確認できるように）
						this.analyzeFieldChecker.checkTeamsAndScore(BM_NUMBER, outerEntry.getKey(), end);
						continue;
					}

					List<NoGoalMatchStatisticsEntity> rows = buildRows(sorted, end);
					try {
						this.noGoalMatchWriter.upsertMatch(rows);
						savedMatchCount++;
					} catch (NoGoalMatchWriter.SeasonNotResolvedException e) {
						// シーズン不明の国,リーグ: 何も保存されていないので、この試合だけスキップ
						seasonSkipCount++;
						debugLog(METHOD_NAME, BM_NUMBER + " シーズン取得不可のためスキップ: matchKey=" + matchKey
								+ " (" + e.getMessage() + ")");
					}
				}
			}

			if (savedMatchCount == 0) {
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00008I_NO_SCORE_SKIP,
						BM_NUMBER + " 保存対象の無得点試合なし（対象試合数: " + matchCount + "）");
			}
		} finally {
			this.noGoalMatchWriter.clearSeasonCache();
			debugLog(METHOD_NAME, BM_NUMBER + " matchCount=" + matchCount
					+ ", savedMatchCount=" + savedMatchCount
					+ ", notFinishedCount=" + notFinishedCount
					+ ", scoredCount=" + scoredCount
					+ ", invalidCount=" + invalidCount
					+ ", seasonSkipCount=" + seasonSkipCount);
			this.manageLoggerComponent.debugEndInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();
		}
	}

	/**
	 * 1試合分の行（START / HT / END）を作る。同じ行を複数の時点で保存しない（END → HT → START の優先順）。
	 *
	 * @param sorted 通番の数値順に並んだ行
	 * @param end 試合終了（FIN）行
	 */
	private List<NoGoalMatchStatisticsEntity> buildRows(List<BookDataEntity> sorted, BookDataEntity end) {
		BookDataEntity start = sorted.get(0);
		BookDataEntity half = findHalfTime(sorted);

		List<NoGoalMatchStatisticsEntity> rows = new ArrayList<>(3);
		if (start != end && start != half) {
			rows.add(toEntity(start, NoGoalMatchStatisticsEntity.SNAPSHOT_START));
		}
		if (half != null && half != end) {
			rows.add(toEntity(half, NoGoalMatchStatisticsEntity.SNAPSHOT_HT));
		}
		rows.add(toEntity(end, NoGoalMatchStatisticsEntity.SNAPSHOT_END));
		return rows;
	}

	/**
	 * ハーフタイムの行を探す（複数あれば通番が最大の行。なければ null）。
	 */
	private static BookDataEntity findHalfTime(List<BookDataEntity> sorted) {
		BookDataEntity half = null;
		for (BookDataEntity e : sorted) {
			String t = trimOrNull(e.getTime());
			if (BookMakersCommonConst.HALF_TIME.equals(t) || BookMakersCommonConst.FIRST_HALF_TIME.equals(t)) {
				half = e;
			}
		}
		return half;
	}

	private NoGoalMatchStatisticsEntity toEntity(BookDataEntity book, String snapshotType) {
		NoGoalMatchStatisticsEntity e = this.bookDataToNoGoalMatchMapper.mapStruct(book);
		e.setSnapshotType(snapshotType);
		// キー項目は前後の空白を除いて揃える（再処理で別の行と判定されないように）
		e.setDataCategory(trimOrNull(e.getDataCategory()));
		e.setHomeTeamName(trimOrNull(e.getHomeTeamName()));
		e.setAwayTeamName(trimOrNull(e.getAwayTeamName()));
		return e;
	}

	/** スコアを整数に変換（空・数値以外は null） */
	private static Integer parseScore(String value) {
		String s = trimOrNull(value);
		if (s == null) {
			return null;
		}
		try {
			double d = Double.parseDouble(s);
			if (!Double.isFinite(d) || d != Math.rint(d)) {
				return null;
			}
			return (int) d;
		} catch (NumberFormatException e) {
			return null;
		}
	}

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
	 * 埋め字設定
	 */
	private String setLoggerFillChar(BookDataEntity entity) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("国,リーグ: ").append(entity.getGameTeamCategory()).append(", ");
		stringBuilder.append("ホームチーム: ").append(entity.getHomeTeamName()).append(", ");
		stringBuilder.append("アウェーチーム: ").append(entity.getAwayTeamName()).append(", ");
		stringBuilder.append("スコア: ").append(entity.getHomeScore()).append("-").append(entity.getAwayScore());
		return stringBuilder.toString();
	}
}
