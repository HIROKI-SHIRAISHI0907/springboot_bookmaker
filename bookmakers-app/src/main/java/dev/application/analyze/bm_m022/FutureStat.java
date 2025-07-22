package dev.application.analyze.bm_m022;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.interf.FutureEntityIF;
import dev.common.entity.FutureEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M022未来データ登録ロジック
 * @author shiraishitoshio
 *
 */
@Service
public class FutureStat implements FutureEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = FutureStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = FutureStat.class.getSimpleName();

	/** FutureDBService部品 */
	@Autowired
	private FutureDBService futureDBService;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 * @throws Exception
	 */
	@Override
	@Transactional
	public void futureStat(Map<String, List<FutureEntity>> entities) throws Exception {
		final String METHOD_NAME = "futureStat";
		// ログ出力
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		List<String> insertPath = new ArrayList<String>();
		// 今後の対戦カードを登録する
		for (Map.Entry<String, List<FutureEntity>> map : entities.entrySet()) {
			String filePath = map.getKey();
			String fillChar = "ファイル名: " + filePath;
			try {
				List<FutureEntity> insertEntities = this.futureDBService.selectInBatch(map.getValue(), fillChar);
				int result = this.futureDBService.insertInBatch(insertEntities);
				if (result == 9) {
					String messageCd = "新規登録エラー";
					throw new Exception(messageCd);
				}
				insertPath.add(filePath);
			} catch (Exception e) {
				String messageCd = "システムエラー";
				throw new Exception(messageCd, e);
			}
		}

		// 途中で例外が起きなければ全てのファイルを削除する
		for (String path : insertPath) {
		    try {
		        Files.deleteIfExists(Paths.get(path));
		    } catch (IOException e) {
		        this.manageLoggerComponent.debugErrorLog(
		            PROJECT_NAME, CLASS_NAME, METHOD_NAME, "ファイル削除失敗", e, path);
		        // ここでは例外をthrowしないことで、DB登録は保持
		    }
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
	}

}
