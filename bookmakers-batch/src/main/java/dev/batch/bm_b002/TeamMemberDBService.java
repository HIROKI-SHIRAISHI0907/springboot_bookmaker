package dev.batch.bm_b002;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.batch.repository.master.TeamMemberMasterBatchRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.TeamMemberMasterEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_B002 選手データDB管理部品
 * @author shiraishitoshio
 */
@Component
public class TeamMemberDBService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamMemberDBService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamMemberDBService.class.getName();

	/** BM_BATCH_NUMBER */
	private static final String BM_NUMBER = "BM_B002";

	/** バッチサイズ */
	private static final int BATCH_SIZE = 100;

	/** TeamMemberMasterRepositoryレポジトリクラス */
	@Autowired
	private TeamMemberMasterBatchRepository teamMemberMasterRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 全件取得
	 * 既存呼び出しとの互換性のため List<List<TeamMemberMasterEntity>> で返却
	 *
	 * @return 1要素目に全件Listを格納した二重List
	 */
	public List<List<TeamMemberMasterEntity>> selectInBatch() {
		final String METHOD_NAME = "selectInBatch";
		List<List<TeamMemberMasterEntity>> entities = new ArrayList<List<TeamMemberMasterEntity>>();
		try {
			List<TeamMemberMasterEntity> entityList = this.teamMemberMasterRepository.selectAll();
			if (entityList == null) {
				entityList = Collections.emptyList();
			}
			entities.add(entityList);

		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, "DB接続エラー");
			throw e;
		}
		return entities;
	}

	/**
	 * 一括登録
	 *
	 * @param insertEntities 登録対象
	 * @return 0:正常 / 9:異常
	 */
	public int insertInBatch(List<TeamMemberMasterEntity> insertEntities) {
		final String METHOD_NAME = "insertInBatch";

		if (insertEntities == null || insertEntities.isEmpty()) {
			String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					BM_NUMBER + " 登録対象なし");
			return 0;
		}

		int inserted = 0;

		for (int i = 0; i < insertEntities.size(); i += BATCH_SIZE) {
			int end = Math.min(i + BATCH_SIZE, insertEntities.size());
			List<TeamMemberMasterEntity> batch = insertEntities.subList(i, end);

			for (TeamMemberMasterEntity entity : batch) {
				try {
					int result = this.teamMemberMasterRepository.insert(entity);
					if (result == 1) {
						inserted++;
					} else {
						String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
						this.manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null,
								"新規登録エラー(result=" + result + ", member=" + safe(entity.getMember()) + ")");
						return 9;
					}
				} catch (Exception e) {
					String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
							"登録失敗 member=" + safe(entity.getMember()) + ", team=" + safe(entity.getTeam()));
					return 9;
				}
			}
		}

		String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 登録件数: " + inserted + "件");
		return 0;
	}

	/**
	 * 一括更新
	 *
	 * @param chkEntities 更新対象
	 * @param fillChar 呼び出し元識別用文字列
	 * @return 0:正常 / 9:異常
	 */
	public int updateInBatch(List<TeamMemberMasterEntity> chkEntities, String fillChar) {
		final String METHOD_NAME = "updateInBatch";

		if (chkEntities == null || chkEntities.isEmpty()) {
			String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					BM_NUMBER + " 更新対象なし (" + safe(fillChar) + ")");
			return 0;
		}

		int updated = 0;
		int notFound = 0;

		for (int i = 0; i < chkEntities.size(); i += BATCH_SIZE) {
			int end = Math.min(i + BATCH_SIZE, chkEntities.size());
			List<TeamMemberMasterEntity> batch = chkEntities.subList(i, end);

			for (TeamMemberMasterEntity entity : batch) {
				try {
					int count = this.teamMemberMasterRepository.updateById(entity);
					if (count == 1) {
						updated++;
					} else if (count == 0) {
						// 更新対象なし（id不一致など）
						notFound++;
					} else {
						String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
						this.manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null,
								"更新件数異常(count=" + count + ", id=" + safe(entity.getId()) + ", member=" + safe(entity.getMember()) + ")");
						return 9;
					}
				} catch (Exception e) {
					String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
							"更新失敗 id=" + safe(entity.getId()) + ", member=" + safe(entity.getMember()) + ", fillChar=" + safe(fillChar));
					return 9;
				}
			}
		}

		String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 更新件数: " + updated + "件 / 更新対象なし: " + notFound + "件 (" + safe(fillChar) + ")");
		return 0;
	}

	/**
	 * null安全化
	 */
	private String safe(String value) {
		return value == null ? "" : value;
	}

	/**
	 * null安全化
	 */
	private String safe(Integer value) {
		return value == null ? "0" : String.valueOf(value);
	}
}
