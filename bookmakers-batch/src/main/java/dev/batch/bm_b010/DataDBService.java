package dev.batch.bm_b010;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.bm.BookDataRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.DataEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * DataDBService管理部品(「終了済」データとして追加登録用)
 * @author shiraishitoshio
 *
 */
@Component
@Transactional
public class DataDBService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = DataDBService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = DataDBService.class.getName();

	/** BookDataRepositoryレポジトリクラス */
	@Autowired
	private BookDataRepository bookDataRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * チェックメソッド
	 * @param chkEntities
	 */
	public DataEntity selectInBatch(DataEntity chkEntities) {
		// ここは読み取りだけなのでTx不要（必要なら readOnly で付けてもOK）
		int chk = bookDataRepository.findFinCount(chkEntities);
		return (chk == 0) ? chkEntities : null;
	}

	/**
	 * 登録メソッド
	 * @param insertEntities
	 */
	@Transactional(transactionManager = "bmTxManager", rollbackFor = Exception.class)
	public void insertInBatchOrThrow(DataEntity insertEntities) throws Exception {
		final String METHOD_NAME = "insertInBatchOrThrow";

		if (insertEntities != null)
			return;

		try {
			int result = bookDataRepository.insert(insertEntities);
			if (result != 1) {
				throw new Exception("bm insert failed. result=" + result);
			}
		} catch (DuplicateKeyException e) {
			// 重複は成功扱いにしたいなら握る（現状踏襲）
			String messageCd = MessageCdConst.MCD00002W_DUPLICATION_WARNING;
			manageLoggerComponent.debugWarnLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd);
		}
	}
}
