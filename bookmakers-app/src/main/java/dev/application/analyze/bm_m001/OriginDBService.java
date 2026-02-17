package dev.application.analyze.bm_m001;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import dev.application.domain.repository.bm.BookDataRepository;
import dev.common.entity.DataEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M001起源データDB管理部品
 * @author shiraishitoshio
 *
 */
@Component
public class OriginDBService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = OriginDBService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = OriginDBService.class.getName();

	/** BookDataRepositoryレポジトリクラス */
	@Autowired
	private BookDataRepository bookDataRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * チェックメソッド
	 * @param chkEntities
	 * @param fillChar
	 */
	public List<DataEntity> selectInBatch(List<DataEntity> chkEntities,
			String fillChar) {
		final String METHOD_NAME = "selectInBatch";
		List<DataEntity> entities = new ArrayList<DataEntity>();
		for (DataEntity entity : chkEntities) {
			try {
				int count = this.bookDataRepository.findDataCount(entity);
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
	public int insertInBatch(List<DataEntity> insertEntities) {
		final String METHOD_NAME = "insertInBatch";
		final int BATCH_SIZE = 100;
		for (int i = 0; i < insertEntities.size(); i += BATCH_SIZE) {
			int end = Math.min(i + BATCH_SIZE, insertEntities.size());
			List<DataEntity> batch = insertEntities.subList(i, end);
			for (DataEntity entity : batch) {
				try {
					int result = this.bookDataRepository.insert(entity);
					if (result != 1) {
						String messageCd = "新規登録エラー";
						this.manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
						return -99;
					}
				} catch (DuplicateKeyException e) {
					String messageCd = "登録済みです";
					this.manageLoggerComponent.debugWarnLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd);
					// 重複は特に例外として出さない
					continue;
				} catch (DataIntegrityViolationException e) {
					String messageCd = "データの形式が合わないエラー";
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e);
					continue;
				}
			}
		}
		String messageCd = "BM_M001 登録件数: " + insertEntities.size();
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd);
		return 0;
	}

}
