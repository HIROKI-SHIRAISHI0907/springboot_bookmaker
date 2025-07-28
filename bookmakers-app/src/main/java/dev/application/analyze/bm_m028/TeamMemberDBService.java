package dev.application.analyze.bm_m028;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import dev.application.domain.repository.TeamMemberMasterRepository;
import dev.common.entity.TeamMemberMasterEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M028選手データDB管理部品
 * @author shiraishitoshio
 *
 */
@Component
public class TeamMemberDBService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamMemberDBService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamMemberDBService.class.getSimpleName();

	/** TeamMemberMasterRepositoryレポジトリクラス */
	@Autowired
	private TeamMemberMasterRepository teamMemberMasterRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * チェックメソッド
	 * @param chkEntities
	 * @param fillChar
	 */
	public List<List<TeamMemberMasterEntity>> selectInBatch() {
		final String METHOD_NAME = "selectInBatch";
		List<List<TeamMemberMasterEntity>> entities = new ArrayList<List<TeamMemberMasterEntity>>();
		try {
			List<TeamMemberMasterEntity> entity = this.teamMemberMasterRepository.findData();
			entities.add(entity);
		} catch (Exception e) {
			String messageCd = "DB接続エラー";
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e);
			throw e;
		}
		return entities;
	}

	/**
	 * 登録メソッド
	 * @param insertEntities
	 */
	public int insertInBatch(List<TeamMemberMasterEntity> insertEntities) {
		final String METHOD_NAME = "insertInBatch";
		final int BATCH_SIZE = 100;
		for (int i = 0; i < insertEntities.size(); i += BATCH_SIZE) {
			int end = Math.min(i + BATCH_SIZE, insertEntities.size());
			List<TeamMemberMasterEntity> batch = insertEntities.subList(i, end);
			for (TeamMemberMasterEntity entity : batch) {
				try {
					int result = this.teamMemberMasterRepository.insert(entity);
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
		String messageCd = "BM_M028 登録件数: " + insertEntities.size();
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd);
		return 0;
	}

	/**
	 * 更新メソッド
	 * @param chkEntities
	 * @param fillChar
	 */
	public int updateInBatch(List<TeamMemberMasterEntity> chkEntities,
			String fillChar) {
		final String METHOD_NAME = "updateInBatch";
		List<TeamMemberMasterEntity> entities = new ArrayList<TeamMemberMasterEntity>();
		for (TeamMemberMasterEntity entity : chkEntities) {
			try {
				int count = this.teamMemberMasterRepository.update(entity);
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
		return 0;
	}

}
