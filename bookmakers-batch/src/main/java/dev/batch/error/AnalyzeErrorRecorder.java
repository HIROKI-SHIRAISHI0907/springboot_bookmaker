package dev.batch.error;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.constant.MessageCdConst;
import dev.common.error.AnalyzeErrorInfo;
import dev.common.error.AnalyzeErrorMatchEntity;
import dev.common.error.AnalyzeErrorType;
import dev.common.logger.ManageLoggerComponent;

/**
 * 処理できなかった試合を analyze_error_match に記録する（batch 用。B011 CSV 作成など）。
 *
 * <h2>何をするクラスか</h2>
 * <p>application 側の AnalyzeErrorRecorder と同じ考え方。</p>
 * <ul>
 *   <li><b>業務処理を止めない</b>: 記録に失敗しても例外は投げず、ログに出すだけ。</li>
 *   <li><b>別トランザクション</b>（{@link AnalyzeErrorWriter}。REQUIRES_NEW）。</li>
 *   <li><b>同じエラーは1行にまとめる</b>: 同じ BM・種別・試合・原因の項目なら発生回数を +1。</li>
 *   <li><b>解決したら自動で対応済み</b>: その試合が正常に処理できたら {@link #resolveByTeams}（resolved_by = 'AUTO'）。</li>
 *   <li>並列スレッド（B011 の CSV 作成）から呼んでよい（状態を持たない）。</li>
 * </ul>
 */
@Component
public class AnalyzeErrorRecorder {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = AnalyzeErrorRecorder.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = AnalyzeErrorRecorder.class.getName();

	/** スタックトレースの最大文字数 */
	private static final int MAX_TRACE = 4000;

	@Autowired
	private AnalyzeErrorWriter analyzeErrorWriter;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 記録する（失敗しても例外は投げない）。
	 *
	 * @param bmNumber BM 番号（例: BM_B011）
	 * @param type エラー種別
	 * @param message エラー内容
	 * @param info 試合の情報（null 可）
	 * @param season シーズン（取得できていれば。null 可）
	 * @param cause 原因の例外（null 可）
	 */
	public void record(String bmNumber, AnalyzeErrorType type, String message, AnalyzeErrorInfo info,
			String season, Throwable cause) {
		final String METHOD_NAME = "record";
		try {
			AnalyzeErrorInfo i = info == null ? AnalyzeErrorInfo.empty() : info;
			AnalyzeErrorMatchEntity e = new AnalyzeErrorMatchEntity();
			e.setBmNumber(bmNumber);
			e.setErrorType((type == null ? AnalyzeErrorType.UNEXPECTED : type).name());
			e.setErrorMessage(message);
			e.setCountry(i.getCountry());
			e.setLeague(i.getLeague());
			e.setDataCategory(i.getDataCategory());
			e.setHomeTeamName(i.getHomeTeamName());
			e.setAwayTeamName(i.getAwayTeamName());
			e.setMatchId(i.getMatchId());
			e.setDetail(i.getDetail());
			e.setErrorField(i.getErrorField());
			e.setErrorValue(i.getErrorValue());
			e.setSeason(season);
			if (cause != null) {
				e.setExceptionClass(cause.getClass().getName());
				e.setStackTrace(stackTrace(cause));
			}
			this.analyzeErrorWriter.write(e);
		} catch (RuntimeException ex) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, ex,
					"analyze_error_match への記録に失敗（処理は続行）: bm=" + bmNumber + ", type=" + type
							+ ", message=" + message + ", " + info);
		}
	}

	/**
	 * 項目の値が原因で処理できなかった試合を記録する（失敗しても例外は投げない）。
	 * どの項目かは info に {@link AnalyzeErrorInfo#field} で設定しておくこと。
	 */
	public void recordField(String bmNumber, AnalyzeErrorType type, AnalyzeErrorInfo info) {
		String label = (type == null ? AnalyzeErrorType.UNEXPECTED : type).getLabel();
		String message = label + (info == null || !info.hasField() ? "" : ": " + info.getErrorValue());
		record(bmNumber, type, message, info, null, null);
	}

	/**
	 * 正常に処理できた試合（ホーム・アウェー）の未対応エラーを対応済みにする（失敗しても例外は投げない）。
	 *
	 * @return 対応済みにした件数（失敗したら 0）
	 */
	public int resolveByTeams(String bmNumber, String homeTeamName, String awayTeamName) {
		final String METHOD_NAME = "resolveByTeams";
		try {
			int n = this.analyzeErrorWriter.resolveByTeams(bmNumber, homeTeamName, awayTeamName);
			if (n > 0) {
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
						bmNumber + " 処理できたため未対応エラーを対応済みにしました: " + n + "件 (home=" + homeTeamName
								+ ", away=" + awayTeamName + ")");
			}
			return n;
		} catch (RuntimeException ex) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, ex,
					"analyze_error_match の自動解決に失敗（処理は続行）: bm=" + bmNumber + ", home=" + homeTeamName
							+ ", away=" + awayTeamName);
			return 0;
		}
	}

	/**
	 * BM の未対応エラーの試合キー（取得に失敗したら空。例外は投げない）。
	 *
	 * @return キー（{@link AnalyzeErrorWriter#teamKey} の形式）
	 */
	public Set<String> findUnresolvedTeamKeys(String bmNumber) {
		final String METHOD_NAME = "findUnresolvedTeamKeys";
		try {
			return this.analyzeErrorWriter.findUnresolvedTeamKeys(bmNumber);
		} catch (RuntimeException ex) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, ex,
					"analyze_error_match の未対応キーの取得に失敗（自動解決はしない）: bm=" + bmNumber);
			return Collections.emptySet();
		}
	}

	/**
	 * BM の未対応エラー（突き合わせ用）。取得に失敗したら空（例外は投げない）。
	 */
	public List<AnalyzeErrorMatchEntity> findUnresolvedRows(String bmNumber) {
		final String METHOD_NAME = "findUnresolvedRows";
		try {
			return this.analyzeErrorWriter.findUnresolvedRows(bmNumber);
		} catch (RuntimeException ex) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, ex,
					"analyze_error_match の未対応エラーの取得に失敗（自動解決はしない）: bm=" + bmNumber);
			return Collections.emptyList();
		}
	}

	/**
	 * 原因が解消した未対応エラーを対応済みにする（resolved_by = 'AUTO'。失敗しても例外は投げない）。
	 *
	 * @param bmNumber BM 番号
	 * @param seqs 対応済みにする seq
	 * @param note 理由（メモが空の行だけに入る）
	 * @return 対応済みにした件数（失敗したら 0）
	 */
	public int resolveBySeqs(String bmNumber, List<String> seqs, String note) {
		final String METHOD_NAME = "resolveBySeqs";
		if (seqs == null || seqs.isEmpty()) {
			return 0;
		}
		try {
			int n = this.analyzeErrorWriter.resolveBySeqs(bmNumber, seqs, note);
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					bmNumber + " 原因が解消したため未対応エラーを対応済みにしました: " + n + "件 (" + note + ")");
			return n;
		} catch (RuntimeException ex) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, ex,
					"analyze_error_match の自動解決に失敗（処理は続行）: bm=" + bmNumber + ", 件数=" + seqs.size());
			return 0;
		}
	}

	private static String stackTrace(Throwable t) {
		StringWriter sw = new StringWriter();
		t.printStackTrace(new PrintWriter(sw));
		String s = sw.toString();
		return s.length() <= MAX_TRACE ? s : s.substring(0, MAX_TRACE);
	}
}
