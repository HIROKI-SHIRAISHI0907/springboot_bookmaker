package dev.application.analyze.bm_m022;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M022_FUTURE";

	/** FutureDBService部品 */
	@Autowired
	private FutureDBService futureDBService;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void futureStat(Map<String, List<FutureEntity>> entities) {
		final String METHOD_NAME = "calcStat";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
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
					this.manageLoggerComponent.createSystemException(
							PROJECT_NAME,
							CLASS_NAME,
							METHOD_NAME,
							messageCd,
							null);
				}
				insertPath.add(filePath);
			} catch (Exception e) {
				String messageCd = "システムエラー";
				this.manageLoggerComponent.createSystemException(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						messageCd,
						e);
			}
		}

		// 途中で例外が起きなければ全てのファイルを削除する

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

}
