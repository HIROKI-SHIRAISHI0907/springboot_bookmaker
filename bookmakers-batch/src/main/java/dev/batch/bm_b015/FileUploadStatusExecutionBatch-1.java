package dev.batch.bm_b015;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.common.AbstractJobBatchTemplate;
import dev.batch.repository.bm.FileUploadStatusBatchRepository;
import dev.common.constant.MessageCdConst;

/**
 * ファイルアップロードステータス変更バッチ実行クラス。
 * <p>
 * aws-s3-upload-check-csv バケットの uploaded/ にあるアップロードファイル
 * （file_upload_status_manage が判定前のもの）を DB チェック・反映し、
 * </p>
 * <ul>
 *   <li>ステータスの振り分け（判定前 → 判定中 → 正常終了 / 異常終了）</li>
 *   <li>最終ファイル格納先（success/ または error/ に {名前}_error.zip）</li>
 * </ul>
 * <p>を決める（処理本体は {@link FileUploadStatusExecution}）。</p>
 *
 * <p><b>実行方式</b></p>
 * <ul>
 *   <li>開始/終了ログを必ず出力する</li>
 *   <li>例外は内部で捕捉し、debugErrorLog に例外を付与して出力する</li>
 *   <li>戻り値で成功/失敗を返却する（テンプレート側）</li>
 *   <li>処理するもの（判定前・判定中のまま止まっているもの）が無いときはスキップする（実行履歴を増やさない）</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Service("B015")
public class FileUploadStatusExecutionBatch extends AbstractJobBatchTemplate {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = FileUploadStatusExecutionBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = FileUploadStatusExecutionBatch.class.getName();

	/** エラーコード（運用ルールに合わせて変更） */
	private static final String ERROR_CODE = "BM_B015_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B015";

	/** オーバーライド */
	@Override
	protected String batchCode() {
		return BATCH_CODE;
	}

	@Override
	protected String errorCode() {
		return ERROR_CODE;
	}

	@Override
	protected String projectName() {
		return PROJECT_NAME;
	}

	@Override
	protected String className() {
		return CLASS_NAME;
	}

	/** ファイルアップロードステータス変更ロジック */
	@Autowired
	private FileUploadStatusExecution fileUploadStatusExecution;

	/** 状況管理 */
	@Autowired
	private FileUploadStatusBatchRepository fileUploadStatusBatchRepository;

	/**
	 * 判定前・判定中のまま止まっているものが無ければスキップする。
	 */
	@Override
	protected boolean shouldSkipExecution() {
		final String METHOD_NAME = "shouldSkipExecution";
		int targets = fileUploadStatusBatchRepository.countTargets(FileUploadBatchConst.STALE_PROCESSING_MINUTES);
		if (targets > 0) {
			return false;
		}
		// スキップするときは、どの DB を見て 0 件だったかをログに出す（接続先の取り違えを見つけるため）
		try {
			Map<String, Object> d = fileUploadStatusBatchRepository.diagnose();
			this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					"対象 0 件。接続先 db=" + d.get("db") + ", host=" + d.get("host") + ":" + d.get("port")
							+ ", user=" + d.get("db_user") + ", search_path=" + d.get("search_path")
							+ ", table=" + d.get("resolved_table") + " (schemas=" + d.get("table_schemas") + ")"
							+ ", status=" + d.get("status_counts"));
		} catch (Exception e) {
			this.manageLoggerComponent.debugWarnLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					ERROR_CODE, "接続先の確認に失敗しました: " + e.getMessage());
		}
		return true;
	}

	@Override
	protected String skipReason() {
		return "判定前のアップロードが無いためスキップします。 batchCode=" + batchCode();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doExecute(JobContext ctx) throws Exception {
		final String METHOD_NAME = "doExecute";
		try {
			this.fileUploadStatusExecution.execute();
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
			throw e;
		}
		endLog();
	}

	/**
	 * 終了ログ
	 */
	private void endLog() {
		final String METHOD_NAME = "endLog";
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
	}
}
