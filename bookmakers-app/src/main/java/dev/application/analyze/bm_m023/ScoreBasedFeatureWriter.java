package dev.application.analyze.bm_m023;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.common.error.AnalyzeErrorInfo;
import dev.application.analyze.common.service.AbstractSeasonResolvingWriter;
import dev.application.analyze.common.service.SeqNumberingService;
import dev.application.domain.repository.bm.ScoreBasedFeatureMatchStatsRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M023 / BM_M026 登録処理（score_based_feature_match_stats）。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * {@link ScoreBasedFeatureStat} が作った1試合分の明細（区分 × 特徴量、約200行）に、シーズン・国・リーグ・seq を設定し、
 * 試合単位で「最新の計算結果に置き換える」。
 * </p>
 * <ol>
 *   <li>シーズンを決める（取得できなければ何も保存せずに SeasonNotResolvedException）。</li>
 *   <li>試合の既存行の seq を引き、同じ（区分, 特徴量）の行はその seq を使い回す。新しい行の分だけまとめて採番する
 *       （{@link SeqNumberingService#nextSeqBlock}。seq_counter の更新は1回）。</li>
 *   <li>200 行ずつまとめて UPSERT する。</li>
 *   <li>今回の計算に無い既存行（スコアの区分が変わった場合など）を削除する。</li>
 * </ol>
 * <p>
 * 統計はビューが明細から計算するため、同じ試合を何度処理しても二重にならない
 * （旧実装は既存値に今回分を足し込んでいたため、同じ試合が流れてくるたびに二重に集計していた）。
 * </p>
 *
 * <h2>トランザクション</h2>
 * <p>
 * 1試合＝1トランザクション（REQUIRES_NEW）。途中で失敗するとその試合の変更と採番はすべてロールバックされる。
 * </p>
 *
 * <h2>シーズンが取得できない試合</h2>
 * <p>
 * 【変更】シーズンの取得は共通の親クラス AbstractSeasonResolvingWriter で行う。取得できない試合は analyze_error_match に
 * 記録してから SeasonNotResolvedException を投げる（何も保存しない）。Stat はこの例外を捕まえてその試合だけスキップする。
 * </p>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>採番の待ち</b>: 同じシーズンの採番は seq_counter の同じ行を更新するため、新規行を含む試合の保存は1試合ずつ順番になる。</li>
 *   <li><b>同時実行で同じ試合を処理した場合</b>: 後の処理の番号が欠番になる（行の重複は起きない）。</li>
 *   <li><b>シーズンは処理日基準</b>・<b>同じ組み合わせの試合がシーズン内に2試合ある場合は後の試合で上書き</b>。</li>
 * </ul>
 */
@Service
public class ScoreBasedFeatureWriter extends AbstractSeasonResolvingWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ScoreBasedFeatureWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ScoreBasedFeatureWriter.class.getName();

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M023";

	/** 採番単位のテーブル名 */
	private static final String TABLE_NAME = "score_based_feature_match_stats";

	/** 1回の UPSERT の行数 */
	private static final int BATCH_SIZE = 200;

	@Autowired
	private ScoreBasedFeatureMatchStatsRepository scoreBasedFeatureMatchStatsRepository;

	/** seq 採番（seq_counter） */
	@Autowired
	private SeqNumberingService seqNumberingService;

	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected String getBmNumber() {
		return BM_NUMBER;
	}

	/**
	 * 1試合分の明細を保存する（既存行は置き換え、今回無い行は削除）。
	 *
	 * @param country 国
	 * @param league リーグ
	 * @param homeTeamName ホームチーム
	 * @param awayTeamName アウェーチーム
	 * @param rows 1試合分（chkBody・feature 設定済み。同じ（chkBody, feature）は含めないこと）
	 * @throws SeasonNotResolvedException シーズンが取得できない場合（何も保存しない）
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void saveMatch(String country, String league, String homeTeamName, String awayTeamName,
			List<ScoreBasedFeatureMatchStatsEntity> rows) {
		final String METHOD_NAME = "saveMatch";
		String season = resolveSeason(country, league, AnalyzeErrorInfo.match(null, homeTeamName, awayTeamName)
				.matchId(rows == null || rows.isEmpty() || rows.get(0) == null ? null : rows.get(0).getMatchId()));
		String fillChar = "シーズン: " + season + ", 国: " + country + ", リーグ: " + league
				+ ", ホーム: " + homeTeamName + ", アウェー: " + awayTeamName;

		// 今回の行（キー重複は後勝ち）
		Map<String, ScoreBasedFeatureMatchStatsEntity> current = new LinkedHashMap<>();
		if (rows != null) {
			for (ScoreBasedFeatureMatchStatsEntity r : rows) {
				if (r == null || r.getChkBody() == null || r.getFeature() == null) {
					continue;
				}
				current.put(key(r.getChkBody(), r.getFeature()), r);
			}
		}

		// 既存行の seq
		Map<String, String> existingSeq = new HashMap<>();
		List<ScoreBasedFeatureMatchStatsEntity> existing = this.scoreBasedFeatureMatchStatsRepository
				.findSeqByMatchKey(season, country, league, homeTeamName, awayTeamName);
		if (existing != null) {
			for (ScoreBasedFeatureMatchStatsEntity e : existing) {
				if (e != null && e.getSeq() != null) {
					existingSeq.put(key(e.getChkBody(), e.getFeature()), e.getSeq());
				}
			}
		}

		// 新しい行の分だけまとめて採番
		int newCount = 0;
		for (String k : current.keySet()) {
			if (!existingSeq.containsKey(k)) {
				newCount++;
			}
		}
		List<String> newSeqs = this.seqNumberingService.nextSeqBlock(TABLE_NAME, season, newCount);
		int newIdx = 0;

		List<ScoreBasedFeatureMatchStatsEntity> toSave = new ArrayList<>(current.size());
		for (Map.Entry<String, ScoreBasedFeatureMatchStatsEntity> e : current.entrySet()) {
			ScoreBasedFeatureMatchStatsEntity r = e.getValue();
			String seq = existingSeq.get(e.getKey());
			if (seq == null) {
				seq = newSeqs.get(newIdx++);
			}
			r.setSeq(seq);
			r.setSeason(season);
			r.setCountry(country);
			r.setLeague(league);
			r.setHomeTeamName(homeTeamName);
			r.setAwayTeamName(awayTeamName);
			toSave.add(r);
		}

		// まとめて UPSERT
		int saved = 0;
		for (int from = 0; from < toSave.size(); from += BATCH_SIZE) {
			List<ScoreBasedFeatureMatchStatsEntity> part = toSave.subList(from, Math.min(from + BATCH_SIZE, toSave.size()));
			int result = this.scoreBasedFeatureMatchStatsRepository.upsertBatch(part);
			if (result != part.size()) {
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00011E_BULKINSERT_FAILED,
						part.size(), result, fillChar);
			}
			saved += result;
		}

		// 今回無い行を削除
		List<String> stale = new ArrayList<>();
		for (Map.Entry<String, String> e : existingSeq.entrySet()) {
			if (!current.containsKey(e.getKey())) {
				stale.add(e.getValue());
			}
		}
		int deleted = 0;
		for (int from = 0; from < stale.size(); from += BATCH_SIZE) {
			deleted += this.scoreBasedFeatureMatchStatsRepository
					.deleteBySeqs(stale.subList(from, Math.min(from + BATCH_SIZE, stale.size())));
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME,
				MessageCdConst.MCD00011I_BULKINSERT_SUCCESS,
				BM_NUMBER + " 登録/更新: " + saved + "件（うち新規採番: " + newCount + "件）, 削除: " + deleted
						+ "件 (" + fillChar + ")");
	}

	private static String key(String chkBody, String feature) {
		return chkBody + "\u0000" + feature;
	}

}
