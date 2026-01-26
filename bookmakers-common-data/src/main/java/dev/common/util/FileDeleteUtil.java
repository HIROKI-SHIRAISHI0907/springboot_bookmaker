package dev.common.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;

/**
 * ファイル削除共通処理
 */
public class FileDeleteUtil {

	/** コンストラクタ生成禁止 */
	private FileDeleteUtil() {
	}

	/**
	 * 指定されたファイルパス一覧を削除し、結果をログ出力する。
	 *
	 * @param paths 削除対象（Stringのフルパス）
	 * @param logger ログ管理
	 * @param projectName PROJECT_NAME
	 * @param className CLASS_NAME
	 * @param methodName 呼び出し元メソッド名
	 * @param title ログの見出し（例： "TEAM_MEMBER CSV"）
	 */
	public static void deleteFiles(
			Collection<String> paths,
			ManageLoggerComponent logger,
			String projectName,
			String className,
			String methodName,
			String title) {
		if (paths == null || paths.isEmpty()) {
			String messageCd = MessageCdConst.MCD00017I_NO_FILE_DELETED;
			logger.debugInfoLog(projectName, className, methodName, messageCd,
					"削除対象ファイルなし" + (title == null ? "" : " - " + title));
			return;
		}

		for (String p : paths) {
			if (p == null || p.isBlank()) {
				continue;
			}
			Path path = Paths.get(p);
			try {
				boolean deleted = Files.deleteIfExists(path);
				if (deleted) {
					String messageCd = MessageCdConst.MCD00016I_FILE_DELETED;
					logger.debugInfoLog(projectName, className, methodName, messageCd,
							"ファイル削除成功" + (title == null ? "" : " - " + title), p);
				} else {
					String messageCd = MessageCdConst.MCD00017I_NO_FILE_DELETED;
					logger.debugInfoLog(projectName, className, methodName, messageCd,
							"削除対象なし（既に無い）" + (title == null ? "" : " - " + title) + "," + p);
				}
			} catch (IOException e) {
				String messageCd = MessageCdConst.MCD00021E_FILE_DELETED_FAILED;
				logger.debugErrorLog(projectName, className, methodName, messageCd,
						e, "ファイル削除失敗" + (title == null ? "" : " - " + title), p);
			} catch (Exception e) {
				// 予期せぬ例外も握りつぶさずログ
				String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
				logger.debugErrorLog(projectName, className, methodName, messageCd,
						e, "ファイル削除で予期せぬ例外" + (title == null ? "" : " - " + title) + "," + p);
			}
		}
	}
}
