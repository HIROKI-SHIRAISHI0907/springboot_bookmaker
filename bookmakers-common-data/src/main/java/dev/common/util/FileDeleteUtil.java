package dev.common.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

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
			logger.debugInfoLog(projectName, className, methodName,
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
					logger.debugInfoLog(projectName, className, methodName,
							"ファイル削除成功" + (title == null ? "" : " - " + title), p);
				} else {
					logger.debugInfoLog(projectName, className, methodName,
							"削除対象なし（既に無い）" + (title == null ? "" : " - " + title), p);
				}
			} catch (IOException e) {
				logger.debugErrorLog(projectName, className, methodName,
						"ファイル削除失敗" + (title == null ? "" : " - " + title), e, p);
			} catch (Exception e) {
				// 予期せぬ例外も握りつぶさずログ
				logger.debugErrorLog(projectName, className, methodName,
						"ファイル削除で予期せぬ例外" + (title == null ? "" : " - " + title), e, p);
			}
		}
	}
}
