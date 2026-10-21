package dev.batch.error;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.bm.AnalyzeErrorMatchBatchRepository;
import dev.batch.repository.bm.SeqCounterBatchRepository;
import dev.common.error.AnalyzeErrorMatchEntity;

/**
 * analyze_error_match への書き込み（batch 用。{@link AnalyzeErrorRecorder} から呼ぶ）。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * application 側の AnalyzeErrorWriter と同じ。呼び出し元とは<b>別のトランザクション（REQUIRES_NEW）</b>で記録する。
 * </p>
 * <ul>
 *   <li>seq は「&lt;発生年&gt;-&lt;6桁枝番&gt;」（seq_counter の table_name = analyze_error_match。application と同じ番号列を使う）。</li>
 *   <li>同じエラーが既にあれば seq を使い回し、採番しない。</li>
 *   <li>トランザクションは bm DB（bmTxManager）。analyze_error_match・seq_counter は bm DB にある前提。</li>
 * </ul>
 * <p>
 * 例外は呼び出し元に投げる（握りつぶしは {@link AnalyzeErrorRecorder} で行う）。
 * </p>
 */
@Service
public class AnalyzeErrorWriter {

	/** 採番単位のテーブル名 */
	static final String TABLE_NAME = "analyze_error_match";

	/** エラー内容の最大文字数 */
	private static final int MAX_MESSAGE = 2000;

	/** 発生年は日本時間で決める */
	private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

	@Autowired
	private AnalyzeErrorMatchBatchRepository analyzeErrorMatchBatchRepository;

	@Autowired
	private SeqCounterBatchRepository seqCounterBatchRepository;

	/**
	 * 記録する。
	 *
	 * @param entity 記録内容（seq は設定不要）
	 */
	@Transactional(transactionManager = "bmTxManager", propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void write(AnalyzeErrorMatchEntity entity) {
		entity.setBmNumber(nvl(entity.getBmNumber()));
		entity.setErrorType(nvl(entity.getErrorType()));
		entity.setCountry(nvl(entity.getCountry()));
		entity.setLeague(nvl(entity.getLeague()));
		entity.setDataCategory(nvl(entity.getDataCategory()));
		entity.setHomeTeamName(nvl(entity.getHomeTeamName()));
		entity.setAwayTeamName(nvl(entity.getAwayTeamName()));
		entity.setErrorField(nvl(entity.getErrorField()));
		entity.setErrorValue(cut(entity.getErrorValue(), MAX_MESSAGE));
		entity.setErrorMessage(cut(entity.getErrorMessage(), MAX_MESSAGE));

		String seq = this.analyzeErrorMatchBatchRepository.findSeq(
				entity.getBmNumber(), entity.getErrorType(), entity.getCountry(), entity.getLeague(),
				entity.getDataCategory(), entity.getHomeTeamName(), entity.getAwayTeamName(), entity.getErrorField());
		if (seq == null) {
			String year = String.valueOf(LocalDate.now(JST).getYear());
			long no = this.seqCounterBatchRepository.nextNumber(TABLE_NAME, year);
			seq = year + "-" + String.format("%06d", no);
		}
		entity.setSeq(seq);
		this.analyzeErrorMatchBatchRepository.upsert(entity);
	}

	/**
	 * その試合（ホーム・アウェー）の未対応エラーを対応済みにする（別トランザクション）。
	 *
	 * @return 更新件数
	 */
	@Transactional(transactionManager = "bmTxManager", propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public int resolveByTeams(String bmNumber, String homeTeamName, String awayTeamName) {
		return this.analyzeErrorMatchBatchRepository.resolveByTeams(nvl(bmNumber), nvl(homeTeamName), nvl(awayTeamName));
	}

	/**
	 * BM の未対応エラー（突き合わせ用。seq・種別・試合・補足だけ）。
	 */
	public List<AnalyzeErrorMatchEntity> findUnresolvedRows(String bmNumber) {
		List<AnalyzeErrorMatchEntity> rows = this.analyzeErrorMatchBatchRepository.findUnresolvedRows(nvl(bmNumber));
		return rows == null ? new java.util.ArrayList<>() : rows;
	}

	/**
	 * 指定した seq の未対応エラーを対応済みにする（別トランザクション。1000 件ずつ）。
	 *
	 * @return 更新件数
	 */
	@Transactional(transactionManager = "bmTxManager", propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public int resolveBySeqs(String bmNumber, List<String> seqs, String note) {
		if (seqs == null || seqs.isEmpty()) {
			return 0;
		}
		int n = 0;
		for (int from = 0; from < seqs.size(); from += 1000) {
			List<String> part = seqs.subList(from, Math.min(from + 1000, seqs.size()));
			n += this.analyzeErrorMatchBatchRepository.resolveBySeqs(nvl(bmNumber), part, note);
		}
		return n;
	}

	/**
	 * BM の未対応エラーの試合キー（{@link #teamKey} の形式）。
	 */
	public Set<String> findUnresolvedTeamKeys(String bmNumber) {
		Set<String> keys = new HashSet<>();
		List<AnalyzeErrorMatchEntity> rows = this.analyzeErrorMatchBatchRepository.findUnresolvedTeamKeys(nvl(bmNumber));
		if (rows != null) {
			for (AnalyzeErrorMatchEntity r : rows) {
				keys.add(teamKey(r.getHomeTeamName(), r.getAwayTeamName()));
			}
		}
		return keys;
	}

	/**
	 * 自動解決の判定に使う試合キー（NFKC 正規化・前後空白除去。SQL の resolveByTeams と同じ比較）。
	 */
	public static String teamKey(String homeTeamName, String awayTeamName) {
		return norm(homeTeamName) + "\u0000" + norm(awayTeamName);
	}

	private static String norm(String s) {
		return s == null ? "" : java.text.Normalizer.normalize(s.trim(), java.text.Normalizer.Form.NFKC);
	}

	private static String nvl(String s) {
		return s == null ? "" : s.trim();
	}

	private static String cut(String s, int max) {
		return (s == null || s.length() <= max) ? s : s.substring(0, max);
	}
}
