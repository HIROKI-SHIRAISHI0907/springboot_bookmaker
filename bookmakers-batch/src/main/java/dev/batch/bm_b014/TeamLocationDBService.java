package dev.batch.bm_b014;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.batch.repository.master.TeamLocationRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.TeamLocationEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_B014 地理データDB管理部品
 * @author shiraishitoshio
 */
@Component
public class TeamLocationDBService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamLocationDBService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamLocationDBService.class.getName();

	/** BM_BATCH_NUMBER */
	private static final String BM_NUMBER = "BM_B014";

	/** TeamLocationRepositoryレポジトリクラス */
	@Autowired
	private TeamLocationRepository teamLocationRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 全件取得
	 * 既存呼び出しとの互換性のため List<List<TeamMemberMasterEntity>> で返却
	 *
	 * @return 1要素目に全件Listを格納した二重List
	 */
	public List<TeamLocationEntity> selectInBatch() {
		final String METHOD_NAME = "selectInBatch";
		List<TeamLocationEntity> entityList = new ArrayList<TeamLocationEntity>();
		try {
			entityList = this.teamLocationRepository.select();
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, "DB接続エラー");
			throw e;
		}
		return entityList;
	}

	/**
	 * 一括登録
	 *
	 * @param insertEntities 登録対象
	 * @param fillChar 呼び出し元識別用文字列
	 * @return 0:正常 / 9:異常
	 */
	public int insertInBatch(TeamLocationEntity insertEntities, String fillChar) {
		final String METHOD_NAME = "insertInBatch";

		if (insertEntities == null) {
			String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					BM_NUMBER + " 登録対象なし");
			return 0;
		}

		try {
			int result = this.teamLocationRepository.insert(insertEntities);
			if (result == 1) {
				// 何もしない
			} else {
				String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null,
						"新規登録エラー(result=" + result + ", fillChar=" + safe(fillChar) + ")");
				return 9;
			}
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
					"登録失敗 fillChar=" + safe(fillChar) + ")");
			return 9;
		}

		return 0;
	}

	/**
	 * 一括更新
	 *
	 * @param chkEntities 更新対象
	 * @param fillChar 呼び出し元識別用文字列
	 * @return 0:正常 / 9:異常
	 */
	public int updateInBatch(TeamLocationEntity chkEntities, String fillChar) {
		final String METHOD_NAME = "updateInBatch";

		if (chkEntities == null) {
			String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					BM_NUMBER + " 更新対象なし (" + safe(fillChar) + ")");
			return 0;
		}

		int count = 0;
		try {
			count = this.teamLocationRepository.updateById(chkEntities);
			if (count == 1 || count == 0) {
				// 何もしない
			} else {
				String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null,
						"更新件数異常(count=" + count + ", fillChar=" + safe(fillChar) + ")");
				return 9;
			}
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
					"更新失敗 fillChar=" + safe(fillChar));
			return 9;
		}

		return count;
	}

	/**
	 * null安全化
	 */
	private String safe(String value) {
		return value == null ? "" : value;
	}

}
