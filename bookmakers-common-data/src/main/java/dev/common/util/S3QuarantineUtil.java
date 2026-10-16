package dev.common.util;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;

/**
 * 読み込み・登録できない CSV（ゴミデータ）を S3 上で退避するユーティリティ（2026-10 追加）。
 *
 * <p>背景: 読めない CSV を残しておくと、Lambda が「.csv がある」と判定して ECS を起動し続け、
 * 毎回同じファイルで失敗する無駄な稼働になる。</p>
 *
 * <p>退避のしかた: 同じバケットの {@code error/<元のキー>.error} にコピーしてから、元のキーを削除する。</p>
 * <ul>
 *   <li>error/ フォルダに移すので、バケット直下を見ている Lambda には見えない。</li>
 *   <li>末尾も .error にするので、フォルダの中まで .csv を探す処理（Get*Info の一覧取得など）にも拾われない。</li>
 *   <li>中身はそのまま残るので、後から原因を調べられる。調べ終わったら手で消す。</li>
 * </ul>
 * <p>コピーに失敗した場合は元のファイルを消さない（データを失わないことを優先する）。</p>
 */
public final class S3QuarantineUtil {

	/** 退避先フォルダ */
	public static final String ERROR_PREFIX = "error/";

	/** 退避先の末尾（.csv のままにしない） */
	public static final String ERROR_SUFFIX = ".error";

	private S3QuarantineUtil() {
	}

	/**
	 * 退避先のキーを返す。例: future_20261017.csv → error/future_20261017.csv.error
	 *
	 * @param key 元のキー
	 * @return 退避先のキー
	 */
	public static String toQuarantineKey(String key) {
		String k = key == null ? "" : key;
		while (k.startsWith("/")) {
			k = k.substring(1);
		}
		return ERROR_PREFIX + k + ERROR_SUFFIX;
	}

	/**
	 * CSV を退避する（コピー → 元を削除）。
	 *
	 * @param bucket     バケット
	 * @param key        退避する CSV のキー
	 * @param reason     退避する理由（ログ用）
	 * @param s3Operator S3Operator
	 * @param logger     ログ管理クラス
	 * @param projectName ログ用
	 * @param className   ログ用
	 * @param methodName  ログ用
	 * @return true = 退避できた / false = 退避できなかった（元のファイルは残っている可能性あり）
	 */
	public static boolean quarantine(String bucket, String key, String reason,
			S3Operator s3Operator, ManageLoggerComponent logger,
			String projectName, String className, String methodName) {

		if (key == null || key.isBlank()) {
			return false;
		}

		String quarantineKey = toQuarantineKey(key);
		Path tmp = null;

		try {
			// 1) 元のファイルを一時ファイルに落とす
			try (InputStream is = s3Operator.download(bucket, key)) {
				tmp = Files.createTempFile("s3-quarantine-", ".tmp");
				Files.copy(is, tmp, StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception e) {
				logger.debugErrorLog(projectName, className, methodName,
						MessageCdConst.MCD00003E_EXECUTION_SKIP, e,
						"CSV退避失敗（ダウンロード）: bucket=" + bucket + ", key=" + key + "（元のファイルは残ります）");
				return false;
			}

			// 2) 退避先へ置く（失敗したら元は消さない）
			try {
				s3Operator.uploadFile(bucket, quarantineKey, tmp);
			} catch (Exception e) {
				logger.debugErrorLog(projectName, className, methodName,
						MessageCdConst.MCD00003E_EXECUTION_SKIP, e,
						"CSV退避失敗（退避先への保存）: bucket=" + bucket + ", key=" + key
								+ ", 退避先=" + quarantineKey + "（元のファイルは残ります）");
				return false;
			}

			// 3) 元のファイルを消す
			try {
				s3Operator.deleteObjects(bucket, Collections.singletonList(key));
			} catch (Exception e) {
				logger.debugErrorLog(projectName, className, methodName,
						MessageCdConst.MCD00003E_EXECUTION_SKIP, e,
						"CSV退避: 退避先には保存済みですが、元のファイルの削除に失敗しました: bucket=" + bucket
								+ ", key=" + key + ", 退避先=" + quarantineKey);
				return false;
			}

			// 退避したことはエラーとして残す（CloudWatch で気づけるように）
			logger.debugErrorLog(projectName, className, methodName,
					MessageCdConst.MCD00003E_EXECUTION_SKIP, null,
					"読み込み・登録できないCSVを退避しました: bucket=" + bucket + ", key=" + key
							+ " → " + quarantineKey + ", 理由=" + reason);
			return true;

		} finally {
			if (tmp != null) {
				try {
					Files.deleteIfExists(tmp);
				} catch (Exception ignore) {
					// 一時ファイルの削除失敗は無視
				}
			}
		}
	}

	/**
	 * 例外が「データが原因のDBエラー」か（同じデータで何度やり直しても失敗するもの）。
	 * 型の不一致・桁あふれ・NOT NULL 違反・日付の変換エラーなど（SQLSTATE 22xxx / 23xxx）は
	 * Spring が DataIntegrityViolationException に変換する。
	 * 接続断・タイムアウトなど一時的なエラーは false（退避せず次回もう一度読む）。
	 *
	 * @param e 例外
	 * @return true = データが原因
	 */
	public static boolean isDataError(Throwable e) {
		Throwable t = e;
		int depth = 0;
		while (t != null && depth < 20) {
			if (t instanceof org.springframework.dao.DataIntegrityViolationException) {
				return true;
			}
			t = t.getCause();
			depth++;
		}
		return false;
	}
}