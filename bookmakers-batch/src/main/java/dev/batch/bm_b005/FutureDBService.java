package dev.batch.bm_b005;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.master.FutureMasterRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.FutureEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_B005未来データDB管理部品
 * @author shiraishitoshio
 *
 */
@Component
public class FutureDBService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = FutureDBService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = FutureDBService.class.getName();

	/** FutureRepositoryレポジトリクラス */
	@Autowired
	private FutureMasterRepository futureRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * チェックメソッド
	 * @param chkEntities
	 * @param fillChar
	 */
	public List<FutureEntity> selectInBatch(List<FutureEntity> chkEntities, String fillChar) {
		List<FutureEntity> entities = new ArrayList<>();
		for (FutureEntity entity : chkEntities) {
			int count = futureRepository.findDataCount(entity);
			if (count == 0)
				entities.add(entity);
		}
		return entities;
	}

	/**
	 * 登録メソッド
	 * @param insertEntities
	 */
	@Transactional(transactionManager = "masterTxManager", rollbackFor = Exception.class)
	public void insertInBatchOrThrow(List<FutureEntity> insertEntities) throws Exception {
		final String METHOD_NAME = "insertInBatchOrThrow";

		final int BATCH_SIZE = 10;
		for (int i = 0; i < insertEntities.size(); i += BATCH_SIZE) {
			int end = Math.min(i + BATCH_SIZE, insertEntities.size());
			List<FutureEntity> batch = insertEntities.subList(i, end);

			for (FutureEntity entity : batch) {
				try {
					int result = futureRepository.insert(entity);
					if (result != 1) {
						throw new Exception("master insert failed. result=" + result);
					}
				} catch (DuplicateKeyException e) {
					// 重複は成功扱い（現状踏襲）
					manageLoggerComponent.debugWarnLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00002W_DUPLICATION_WARNING);
				}
			}
		}
	}
}