package dev.application.analyze.common.error;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.common.service.SeqNumberingService;
import dev.application.domain.repository.bm.AnalyzeErrorMatchRepository;

/**
 * analyze_error_match への書き込み（{@link AnalyzeErrorRecorder} から呼ぶ）。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * 登録できなかった試合を、呼び出し元とは<b>別のトランザクション（REQUIRES_NEW）</b>で記録する。
 * 呼び出し元（各 Writer）の保存がロールバックされても、エラーの記録は残る。
 * </p>
 * <ul>
 *   <li>seq は「&lt;発生年&gt;-&lt;6桁枝番&gt;」（シーズンが取れないエラーも記録するため、シーズンではなく発生年で採番）。</li>
 *   <li>同じエラーが既にあれば seq を使い回し、採番しない。</li>
 * </ul>
 * <p>
 * 例外は呼び出し元に投げる。業務処理を止めないための握りつぶしは {@link AnalyzeErrorRecorder} で行う
 * （このクラスの中で握りつぶすと、SQL エラー後のトランザクションがコミットできず、別の例外になるため）。
 * </p>
 */
@Service
public class AnalyzeErrorWriter {

	/** 採番単位のテーブル名 */
	static final String TABLE_NAME = "analyze_error_match";

	/** エラー内容の最大文字数 */
	private static final int MAX_MESSAGE = 2000;

	@Autowired
	private AnalyzeErrorMatchRepository analyzeErrorMatchRepository;

	@Autowired
	private SeqNumberingService seqNumberingService;

	/**
	 * 記録する。
	 *
	 * @param entity 記録内容（seq は設定不要）
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
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

		String seq = this.analyzeErrorMatchRepository.findSeq(
				entity.getBmNumber(), entity.getErrorType(), entity.getCountry(), entity.getLeague(),
				entity.getDataCategory(), entity.getHomeTeamName(), entity.getAwayTeamName(), entity.getErrorField());
		if (seq == null) {
			seq = this.seqNumberingService.nextSeq(TABLE_NAME, String.valueOf(LocalDate.now().getYear()));
		}
		entity.setSeq(seq);
		this.analyzeErrorMatchRepository.upsert(entity);
	}

	/**
	 * 正常に登録できた試合の未対応エラーを対応済みにする（別トランザクション）。
	 *
	 * @return 更新件数
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public int resolve(String bmNumber, String country, String league, String homeTeamName, String awayTeamName) {
		return this.analyzeErrorMatchRepository.resolveByMatch(
				nvl(bmNumber), nvl(country), nvl(league), nvl(homeTeamName), nvl(awayTeamName));
	}

	/**
	 * BM の未対応エラーの試合キー（{@link #key} の形式）。
	 */
	public Set<String> findUnresolvedKeys(String bmNumber) {
		Set<String> keys = new HashSet<>();
		List<AnalyzeErrorMatchEntity> rows = this.analyzeErrorMatchRepository.findUnresolvedKeys(nvl(bmNumber));
		if (rows != null) {
			for (AnalyzeErrorMatchEntity r : rows) {
				keys.add(key(r.getCountry(), r.getLeague(), r.getHomeTeamName(), r.getAwayTeamName()));
			}
		}
		return keys;
	}

	/**
	 * 自動解決の判定に使う試合キー（空白・null は空文字として扱う。記録時と同じ正規化）。
	 */
	public static String key(String country, String league, String homeTeamName, String awayTeamName) {
		return nvl(country) + "\u0000" + nvl(league) + "\u0000" + nvl(homeTeamName) + "\u0000" + nvl(awayTeamName);
	}

	private static String nvl(String s) {
		return s == null ? "" : s.trim();
	}

	private static String cut(String s, int max) {
		return (s == null || s.length() <= max) ? s : s.substring(0, max);
	}
}
