package dev.application.analyze.bm_m034;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.common.error.AnalyzeErrorInfo;
import dev.application.analyze.common.service.AbstractSeasonResolvingWriter;
import dev.application.analyze.common.service.SeqNumberingService;
import dev.application.domain.repository.bm.MatchTeamSnapshotFactRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M034 登録処理（match_team_snapshot_fact）。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * {@link MatchTeamSnapshotFactStat} が作った1試合分のスナップショット（時点数 × 2行）に、シーズン・国・リーグ・seq を設定し、
 * (data_seq, ha) をキーにまとめて UPSERT する。
 * 同じ試合のデータが何度流れてきても（試合中に毎回全時点が届いても）、既存の時点は上書き、新しい時点だけ追加される。
 * </p>
 * <ol>
 *   <li>シーズンを決める（取得できなければ何も保存せずに SeasonNotResolvedException）。</li>
 *   <li>既存行の seq を引き、同じ (data_seq, ha) はその seq を使い回す。新しい行の分だけまとめて採番する
 *       （{@link SeqNumberingService#nextSeqBlock}。seq_counter の更新は1回）。</li>
 *   <li>200 行ずつまとめて UPSERT する。</li>
 * </ol>
 *
 * <h2>トランザクション</h2>
 * <p>1試合＝1トランザクション（REQUIRES_NEW）。旧実装は1行ごとにトランザクションを分けていた。</p>
 *
 * <h2>シーズンが取得できない試合</h2>
 * <p>
 * 【変更】シーズンの取得は共通の親クラス AbstractSeasonResolvingWriter で行う。取得できない試合は analyze_error_match に
 * 記録してから SeasonNotResolvedException を投げる（何も保存しない）。Stat はこの例外を捕まえてその試合だけスキップする。
 * </p>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>採番の待ち</b>: 同じシーズンの採番は seq_counter の同じ行を更新するため、新しい時点を含む試合の保存は1試合ずつ順番になる。</li>
 *   <li><b>同時実行で同じ試合を処理した場合</b>: 後の処理の番号が欠番になる（行の重複は起きない）。</li>
 *   <li><b>シーズンは処理日基準</b>（SeasonResolverIF の実装による）。</li>
 *   <li><b>データ量</b>: 1試合 約100〜200時点 × 2行。年間で数百万行になるので、必要ならシーズンでパーティション分割する。</li>
 * </ul>
 */
@Service
public class MatchTeamSnapshotFactWriter extends AbstractSeasonResolvingWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = MatchTeamSnapshotFactWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = MatchTeamSnapshotFactWriter.class.getName();

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M034";

	/** 採番単位のテーブル名 */
	private static final String TABLE_NAME = "match_team_snapshot_fact";

	/** 1回の UPSERT の行数 */
	private static final int BATCH_SIZE = 200;

	/** 1回の既存 seq 検索の件数 */
	private static final int SELECT_SIZE = 500;

	@Autowired
	private MatchTeamSnapshotFactRepository matchTeamSnapshotFactRepository;

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
	 * 1試合分のスナップショットを UPSERT する。
	 *
	 * @param country 国
	 * @param league リーグ
	 * @param rows 1試合分（dataSeq・ha・team・opponent 設定済み。同じ (dataSeq, ha) は後勝ち）
	 * @return 保存件数
	 * @throws SeasonNotResolvedException シーズンが取得できない場合（何も保存しない）
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public int saveMatch(String country, String league, List<MatchTeamSnapshotFactEntity> rows) {
		final String METHOD_NAME = "saveMatch";
		if (rows == null || rows.isEmpty()) {
			return 0;
		}

		// 今回の行（キー重複は後勝ち）
		Map<String, MatchTeamSnapshotFactEntity> current = new LinkedHashMap<>();
		for (MatchTeamSnapshotFactEntity r : rows) {
			if (r == null || r.getDataSeq() == null || isBlank(r.getHa())
					|| isBlank(r.getTeam()) || isBlank(r.getOpponent())) {
				continue;
			}
			current.put(key(r.getDataSeq(), r.getHa()), r);
		}
		if (current.isEmpty()) {
			return 0;
		}

		// DB 書き込みの前にシーズンを決める（取得できなければ何も保存せずに例外）
		MatchTeamSnapshotFactEntity firstRow = current.values().iterator().next();
		boolean home = "H".equals(firstRow.getHa());
		String season = resolveSeason(country, league, AnalyzeErrorInfo.match(null,
				home ? firstRow.getTeam() : firstRow.getOpponent(),
				home ? firstRow.getOpponent() : firstRow.getTeam())
				.matchId(firstRow.getMatchId()));

		// 既存行の seq
		Set<Long> seqSet = new LinkedHashSet<>();
		for (MatchTeamSnapshotFactEntity r : current.values()) {
			seqSet.add(r.getDataSeq());
		}
		List<Long> dataSeqs = new ArrayList<>(seqSet);
		Map<String, String> existingSeq = new HashMap<>();
		for (int from = 0; from < dataSeqs.size(); from += SELECT_SIZE) {
			List<MatchTeamSnapshotFactEntity> found = this.matchTeamSnapshotFactRepository
					.findSeqByDataSeqs(dataSeqs.subList(from, Math.min(from + SELECT_SIZE, dataSeqs.size())));
			if (found != null) {
				for (MatchTeamSnapshotFactEntity e : found) {
					if (e != null && e.getSeq() != null) {
						existingSeq.put(key(e.getDataSeq(), e.getHa()), e.getSeq());
					}
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

		List<MatchTeamSnapshotFactEntity> toSave = new ArrayList<>(current.size());
		for (Map.Entry<String, MatchTeamSnapshotFactEntity> e : current.entrySet()) {
			MatchTeamSnapshotFactEntity r = e.getValue();
			String seq = existingSeq.get(e.getKey());
			if (seq == null) {
				seq = newSeqs.get(newIdx++);
			}
			r.setSeq(seq);
			r.setSeason(season);
			r.setCountry(country);
			r.setLeague(league);
			toSave.add(r);
		}

		MatchTeamSnapshotFactEntity first = toSave.get(0);
		String fillChar = "シーズン: " + season + ", 国: " + country + ", リーグ: " + league
				+ ", チーム: " + first.getTeam() + ", 対戦: " + first.getOpponent() + ", matchId: " + first.getMatchId();

		int saved = 0;
		for (int from = 0; from < toSave.size(); from += BATCH_SIZE) {
			List<MatchTeamSnapshotFactEntity> part = toSave.subList(from, Math.min(from + BATCH_SIZE, toSave.size()));
			int result = this.matchTeamSnapshotFactRepository.upsertBatch(part);
			if (result != part.size()) {
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00007E_INSERT_FAILED,
						part.size(), result, fillChar);
			}
			saved += result;
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME,
				MessageCdConst.MCD00005I_INSERT_SUCCESS,
				BM_NUMBER + " 登録/更新: " + saved + "件（うち新規採番: " + newCount + "件） (" + fillChar + ")");
		return saved;
	}

	private static String key(Long dataSeq, String ha) {
		return dataSeq + "\u0000" + ha;
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

}
