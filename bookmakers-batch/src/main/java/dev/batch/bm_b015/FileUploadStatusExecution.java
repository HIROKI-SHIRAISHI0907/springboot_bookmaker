package dev.batch.bm_b015;

import java.io.InputStream;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.batch.bm_b015.UploadCsvUtil.CsvReadException;
import dev.batch.bm_b015.UploadCsvUtil.CsvTable;
import dev.batch.bm_b015.UploadCsvUtil.OutFile;
import dev.batch.repository.bm.FileUploadStatusBatchRepository;
import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;

/**
 * ファイルアップロードの DB チェック・反映と、ステータス・最終格納先の振り分け（B015）
 *
 * <ol>
 *   <li>判定中のまま {@value FileUploadBatchConst#STALE_PROCESSING_MINUTES} 分以上止まっているものを異常終了にする
 *       （前の実行が途中で落ちたもの）。</li>
 *   <li>file_upload_status_manage の判定前（PENDING）を古い順に取り出す（1 回 {@value FileUploadBatchConst#MAX_PER_RUN} 件まで）。</li>
 *   <li>1 件ずつ「判定中（PROCESSING）」にしてから処理する（他の実行が先に取ったものは飛ばす）。
 *     <ol>
 *       <li>S3（aws-s3-upload-check-csv）の uploaded/… から zip を取り出し、中の CSV を読む。</li>
 *       <li>DB チェック・反映（{@link FileUploadReflectService}。1 行でもエラーなら反映しない。反映は 1 トランザクション）。</li>
 *       <li>OK → 同じ zip を success/… に置き、uploaded/ を消して「正常終了（SUCCESS）」。件数を記録する。</li>
 *       <li>NG → 「エラー詳細」列を足した {名前}_error.zip を error/… に置き、uploaded/ を消して
 *           「異常終了（ERROR・phase=DB）」。エラー行を記録する（画面からダウンロードできる）。</li>
 *       <li>zip を取り出せない → 「異常終了（ERROR・phase=STORE）」。</li>
 *     </ol></li>
 * </ol>
 * <p>1 件の失敗で他のアップロードの処理は止めない。状況管理の更新は反映のトランザクションとは別に、1 文ずつ確定する
 *    （このクラスには @Transactional を付けない。付けると状況管理の更新まで巻き戻るため）。</p>
 * @author shiraishitoshio
 */
@Component
public class FileUploadStatusExecution {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = FileUploadStatusExecution.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = FileUploadStatusExecution.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "FILE_UPLOAD_STATUS_EXECUTION";

	/** 状況管理 */
	@Autowired
	private FileUploadStatusBatchRepository statusRepository;

	/** DB チェック・反映 */
	@Autowired
	private FileUploadReflectService reflectService;

	/** 最終格納先 */
	@Autowired
	private FileUploadArchiveService archiveService;

	/** Config */
	@Autowired
	private PathConfig config;

	/** S3Operator */
	@Autowired
	private S3Operator s3Operator;

	/** エラー行の JSON */
	@Autowired
	private ObjectMapper objectMapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 実行
	 * @return 処理した件数（正常終了 + 異常終了）
	 */
	public int execute() throws Exception {
		final String METHOD_NAME = "execute";

		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		int done = 0;
		int success = 0;
		int error = 0;
		try {
			// 1. 判定中のまま止まっているもの
			int stale = statusRepository.failStaleProcessing(FileUploadBatchConst.STALE_PROCESSING_MINUTES,
					FileUploadBatchConst.MSG_STALE, FileUploadBatchConst.BATCH_USER);
			if (stale > 0) {
				this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00099I_LOG, "判定中のまま止まっていたものを異常終了にしました: " + stale + " 件");
			}

			// 2. 判定前
			List<FileUploadStatusRow> targets = statusRepository.findPending(FileUploadBatchConst.MAX_PER_RUN);
			if (targets.isEmpty()) {
				this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP, "判定前のアップロードはありません。");
				return 0;
			}

			// 3. 1 件ずつ
			for (FileUploadStatusRow row : targets) {
				if (statusRepository.claimProcessing(row.getId(), FileUploadBatchConst.MSG_PROCESSING,
						FileUploadBatchConst.BATCH_USER) != 1) {
					// 他の実行が先に取った
					continue;
				}
				done++;
				boolean ok;
				try {
					ok = processOne(row);
				} catch (Exception e) {
					// 想定外（状況管理の更新に失敗した等）。判定中のまま残れば次回以降に異常終了になる
					ok = false;
					this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
							"アップロードの処理で想定外のエラー id=" + row.getId() + ", file=" + row.getFileName());
				}
				if (ok) {
					success++;
				} else {
					error++;
				}
			}
			this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					String.format("ファイルアップロードの処理結果: 対象=%d, 処理=%d, 正常終了=%d, 異常終了=%d",
							targets.size(), done, success, error));
			return done;
		} finally {
			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();
		}
	}

	/**
	 * 1 件の処理
	 * @return true = 正常終了 / false = 異常終了
	 */
	private boolean processOne(FileUploadStatusRow row) throws Exception {
		final String METHOD_NAME = "processOne";
		long id = row.getId();
		String bucket = notEmpty(row.getS3Bucket()) ? row.getS3Bucket() : config.getS3BucketsUploadCheck();
		String key = row.getS3Key();
		int webTotal = row.getTotalCount() == null ? 0 : row.getTotalCount();

		this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"処理開始 id=" + id + ", master=" + row.getMasterName() + ", file=" + row.getFileName() + ", s3=" + bucket + "/" + key);

		// 1. zip を取り出す
		byte[] zip;
		try {
			if (!notEmpty(key)) {
				throw new IllegalStateException("s3_key が空です。");
			}
			try (InputStream in = s3Operator.download(bucket, key)) {
				zip = in.readAllBytes();
			}
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN, e, "アップロードファイルを取り出せません id=" + id);
			String message = String.format(FileUploadBatchConst.MSG_DOWNLOAD_FAILED, e.getClass().getSimpleName());
			statusRepository.markError(id, FileUploadBatchConst.PHASE_STORE, message, webTotal, 0, "[]",
					null, null, FileUploadBatchConst.BATCH_USER);
			return false;
		}

		// 2. CSV を読む → DB チェック・反映
		CsvTable table = null;
		FileUploadReflectService.Result result;
		try {
			table = UploadCsvUtil.readZip(zip);
			result = reflectService.reflect(row.getMasterName(), table);
		} catch (CsvReadException e) {
			result = new FileUploadReflectService.Result();
			result.addFileError(e.getMessage());
		}

		// 3. 結果に合わせて置き場所と状態を決める
		if (result.isSuccess()) {
			return finishSuccess(row, bucket, key, zip, result);
		}
		return finishError(row, bucket, key, table, result, webTotal);
	}

	/** 正常終了: success/ に置いて uploaded/ を消す */
	private boolean finishSuccess(FileUploadStatusRow row, String bucket, String key, byte[] zip,
			FileUploadReflectService.Result result) {
		final String METHOD_NAME = "finishSuccess";
		String message = String.format(FileUploadBatchConst.MSG_SUCCESS, result.getInsertCount(), result.getUpdateCount(),
				result.getDeleteCount(), result.getHideCount(), result.getShowCount());
		String storedKey = key;
		try {
			String successKey = archiveService.successKey(row);
			archiveService.put(bucket, successKey, zip, row, FileUploadBatchConst.STATUS_SUCCESS);
			storedKey = successKey;
			deleteUploadedQuietly(bucket, key, row.getId());
		} catch (Exception e) {
			// DB には反映済みなので正常終了のまま。uploaded/ に残った zip は状態が PENDING でないため再処理されない
			message = message + FileUploadBatchConst.MSG_SUCCESS_STORE_FAILED;
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00023E_S3_UPLOAD_FAILED, e, "success/ に置けません id=" + row.getId());
		}
		statusRepository.markSuccess(row.getId(), message, result.getTotalCount(), result.getInsertCount(),
				result.getUpdateCount(), result.getDeleteCount(), result.getHideCount(), result.getShowCount(),
				bucket, storedKey, FileUploadBatchConst.BATCH_USER);
		this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"正常終了 id=" + row.getId() + " " + message + " s3=" + bucket + "/" + storedKey);
		return true;
	}

	/** 異常終了: {名前}_error.zip を error/ に置いて uploaded/ を消す */
	private boolean finishError(FileUploadStatusRow row, String bucket, String key, CsvTable table,
			FileUploadReflectService.Result result, int webTotal) {
		final String METHOD_NAME = "finishError";
		int errorCount = result.getErrors().size();
		String message = String.format(result.isApplyFailed() ? FileUploadBatchConst.MSG_DB_APPLY_ERROR
				: FileUploadBatchConst.MSG_DB_CHECK_ERROR, errorCount);
		String errorKey = null;
		try {
			OutFile errorZip = UploadCsvUtil.buildErrorZip(row.getFileName(), table, result.getRowErrors(),
					result.getFileErrors());
			errorKey = archiveService.errorKey(row, errorZip.getFileName());
			archiveService.put(bucket, errorKey, errorZip.getBytes(), row, FileUploadBatchConst.STATUS_ERROR);
			deleteUploadedQuietly(bucket, key, row.getId());
		} catch (Exception e) {
			errorKey = null;
			message = message + FileUploadBatchConst.MSG_ERROR_FILE_STORE_FAILED;
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00023E_S3_UPLOAD_FAILED, e, "error/ に置けません id=" + row.getId());
		}
		int total = table == null ? webTotal : result.getTotalCount();
		statusRepository.markError(row.getId(), FileUploadBatchConst.PHASE_DB, message, total, errorCount,
				errorsJson(result.getErrors()), errorKey == null ? null : bucket, errorKey, FileUploadBatchConst.BATCH_USER);
		this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"異常終了 id=" + row.getId() + " " + message + (errorKey == null ? "" : " s3=" + bucket + "/" + errorKey));
		return false;
	}

	/** uploaded/ を消す（失敗しても状態は変えない。ログだけ） */
	private void deleteUploadedQuietly(String bucket, String key, long id) {
		final String METHOD_NAME = "deleteUploadedQuietly";
		try {
			archiveService.deleteUploaded(bucket, key);
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00003E_EXECUTION_SKIP, e, "uploaded/ の zip を消せません（残ったままになります）id=" + id + ", key=" + key);
		}
	}

	/** 状況管理に残すエラー行（JSON 配列。先頭 MAX_SAVED_ERRORS 件まで） */
	private String errorsJson(List<UploadRowError> errors) {
		List<UploadRowError> saved = errors.size() > FileUploadBatchConst.MAX_SAVED_ERRORS
				? errors.subList(0, FileUploadBatchConst.MAX_SAVED_ERRORS)
				: errors;
		try {
			return objectMapper.writeValueAsString(saved);
		} catch (Exception e) {
			return "[]";
		}
	}

	private static boolean notEmpty(String s) {
		return s != null && !s.isBlank();
	}
}
