package dev.application.analyze.bm_m001;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.common.entity.DataEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * 起源データ動作クラス
 * @author shiraishitoshio
 *
 */
@Component
public class OriginWorker {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = OriginStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = OriginStat.class.getSimpleName();

	@Autowired
	private OriginDBService originDBService;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean processOne(String filePath, List<DataEntity> list) throws Exception {
		final String METHOD = "processOne";

		String fillChar = "ファイル名: " + filePath;
		try {
			List<DataEntity> insertEntities = originDBService.selectInBatch(list, fillChar);
			int result = this.originDBService.insertInBatch(insertEntities);
			if (result == 9) {
				throw new Exception("新規登録エラー");
			}
			return true; // 成功
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME,
					CLASS_NAME, METHOD, "処理失敗", e, filePath);
			// ここで例外を投げるとこのREQUIRES_NEWトランザクションはロールバックされる
			throw new Exception("システムエラー", e);
		}
	}
}
