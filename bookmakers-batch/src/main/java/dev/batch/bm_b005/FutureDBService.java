package dev.batch.bm_b005;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

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
	private static final String CLASS_NAME = FutureDBService.class.getSimpleName();

	/** BM_BATCH_NUMBER */
	private static final String BM_NUMBER = "BM_B005";

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
				String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
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
		final int BATCH_SIZE = 10;
		for (int i = 0; i < insertEntities.size(); i += BATCH_SIZE) {
			int end = Math.min(i + BATCH_SIZE, insertEntities.size());
			List<FutureEntity> batch = insertEntities.subList(i, end);
			for (FutureEntity entity : batch) {
				try {
					int result = this.futureRepository.insert(entity);
					if (result != 1) {
						String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
						this.manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
						return 9;
					}
				} catch (DuplicateKeyException e) {
					String messageCd = MessageCdConst.MCD00002W_DUPLICATION_WARNING;
					this.manageLoggerComponent.debugWarnLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd);
					// 重複は特に例外として出さない
					continue;
				}
			}
		}

		String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 登録件数: 1件");
		return 0;
	}

}
