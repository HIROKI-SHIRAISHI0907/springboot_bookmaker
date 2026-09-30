package dev.application.analyze.common.error;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;

/**
 * 統計処理で登録できなかった試合を analyze_error_match に記録する（全 BM 共通）。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * シーズンが取得できない等で試合を登録できなかったとき、「どの BM で・どの試合が・なぜ」登録できなかったかを記録する。
 * 画面（今後作成）で一覧・対応済みの管理をする。
 * </p>
 * <ul>
 *   <li><b>業務処理を止めない</b>: 記録に失敗しても例外は投げず、ログに出すだけ。</li>
 *   <li><b>別トランザクション</b>: 呼び出し元の保存がロールバックされても記録は残る（{@link AnalyzeErrorWriter}）。</li>
 *   <li><b>同じエラーは1行にまとめる</b>: 同じ BM・種別・試合なら発生回数を +1 する（ストリーミングで何度流れても行が増えない）。</li>
 *   <li><b>解決したら自動で対応済み</b>: 問題が直って同じ試合が正常に登録できたら、{@link #resolve} で対応済み（resolved_by = 'AUTO'）にする。
 *       行は消さない（何が起きていたかの履歴として残す）。</li>
 * </ul>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>記録のたびに DB に書く</b>: シーズンが無いリーグの試合が大量に流れると、その分だけ書き込みが増える
 *       （1試合1行・発生回数の更新のみなので、行数は試合数まで）。</li>
 *   <li><b>記録用テーブルが無い</b>（DDL 未実行）と、毎回ログにエラーが出る（業務処理は続く）。</li>
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
	 * 登録できなかった試合を記録する（失敗しても例外は投げない）。
	 *
	 * @param bmNumber BM 番号（例: BM_M004）
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
	 * 正常に登録できた試合の未対応エラーを対応済みにする（失敗しても例外は投げない）。
	 *
	 * @param bmNumber BM 番号
	 * @param info 試合の情報（国・リーグ・ホーム・アウェー）
	 */
	public void resolve(String bmNumber, AnalyzeErrorInfo info) {
		final String METHOD_NAME = "resolve";
		if (info == null) {
			return;
		}
		try {
			int n = this.analyzeErrorWriter.resolve(bmNumber, info.getCountry(), info.getLeague(),
					info.getHomeTeamName(), info.getAwayTeamName());
			if (n > 0) {
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
						bmNumber + " 登録できたため未対応エラーを対応済みにしました: " + n + "件 (" + info + ")");
			}
		} catch (RuntimeException ex) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, ex,
					"analyze_error_match の自動解決に失敗（処理は続行）: bm=" + bmNumber + ", " + info);
		}
	}

	/**
	 * BM の未対応エラーの試合キー（取得に失敗したら空。例外は投げない）。
	 *
	 * @param bmNumber BM 番号
	 * @return キー（{@link AnalyzeErrorWriter#key} の形式）
	 */
	public Set<String> findUnresolvedKeys(String bmNumber) {
		final String METHOD_NAME = "findUnresolvedKeys";
		try {
			return this.analyzeErrorWriter.findUnresolvedKeys(bmNumber);
		} catch (RuntimeException ex) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, ex,
					"analyze_error_match の未対応キーの取得に失敗（自動解決はしない）: bm=" + bmNumber);
			return Collections.emptySet();
		}
	}

	private static String stackTrace(Throwable t) {
		StringWriter sw = new StringWriter();
		t.printStackTrace(new PrintWriter(sw));
		String s = sw.toString();
		return s.length() <= MAX_TRACE ? s : s.substring(0, MAX_TRACE);
	}
}