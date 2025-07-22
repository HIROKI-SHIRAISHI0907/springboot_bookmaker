package dev.application.analyze.bm_m022;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import dev.application.domain.repository.FutureRepository;
import dev.common.entity.FutureEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M022未来データ管理部品
 * @author shiraishitoshio
 *
 */
@Component
public class FutureDBService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = FutureDBService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = FutureDBService.class.getSimpleName();

	/** FutureRepositoryレポジトリクラス */
	@Autowired
	private FutureRepository futureRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * チェックメソッド
	 * @param chkEntities
	 * @param fillChar
	 */
	public List<FutureEntity> selectInBatch(List<FutureEntity> chkEntities,
			String fillChar) {
		final String METHOD_NAME = "selectInBatch";
		List<FutureEntity> entities = new ArrayList<FutureEntity>();
		for (FutureEntity entity : chkEntities) {
			try {
				int count = this.futureRepository.findDataCount(entity);
				if (count == 0) {
					entities.add(entity);
				}
			} catch (Exception e) {
				String messageCd = "DB接続エラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, fillChar);
				throw e;
			}
		}
		return entities;
	}

	/**
	 * 登録メソッド
	 * @param insertEntities
	 */
	public int insertInBatch(List<FutureEntity> insertEntities) {
		final String METHOD_NAME = "insertInBatch";
		final int BATCH_SIZE = 100;
		for (int i = 0; i < insertEntities.size(); i += BATCH_SIZE) {
			int end = Math.min(i + BATCH_SIZE, insertEntities.size());
			List<FutureEntity> batch = insertEntities.subList(i, end);
			for (FutureEntity entity : batch) {
				try {
					int result = this.futureRepository.insert(entity);
					if (result != 1) {
						String messageCd = "新規登録エラー";
						this.manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
						return 9;
					}
				} catch (DuplicateKeyException e) {
					String messageCd = "登録済みです";
					this.manageLoggerComponent.debugWarnLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd);
					// 重複は特に例外として出さない
					continue;
				}
			}
		}
		String messageCd = "BM_M022 登録件数: " + insertEntities.size();
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd);
		return 0;
	}

}
