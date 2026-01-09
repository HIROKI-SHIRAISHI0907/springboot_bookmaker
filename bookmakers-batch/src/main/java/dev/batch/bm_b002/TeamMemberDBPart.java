package dev.batch.bm_b002;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.constant.BatchConstant;
import dev.common.logger.ManageLoggerComponent;

/**
 * team_member_masterの登録・更新・論理削除（すでに対象マスタにデータが存在する前提）
 * @author shiraishitoshio
 *
 */
@Component
public class TeamMemberDBPart {

	/** 実行ログに出力するプロジェクト識別子（クラスの配置パス）。 */
	private static final String PROJECT_NAME = TeamMemberDBPart.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** 実行ログに出力するクラス名。 */
	private static final String CLASS_NAME = TeamMemberDBPart.class.getSimpleName();

	/** 運用向けのエラーコード。 */
	private static final String ERROR_CODE = "DBPART";

	/** B002バッチクラス。 */
	@Autowired
	private TeamMemberMasterBatch teamMemberMasterBatch;

	/** バッチ共通ログ出力を行う。 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 国リーグシーズンマスタ（終了日・関連CSV）を登録・更新・論理削除する。
	 * 取得したseason_data.csvデータが
	 * <p>
	 * 同一国異なるリーグ異なるパスor全て異なるなら登録
	 * </p>
	 * <p>
	 * 同一国同一パスかつリーグ名が異なるor同一国同一リーグかつ異なるパスなら登録
	 * また古い方のリーグ名は論理削除
	 * </p>
	 * <p>
	 * 同一国同一パス同一リーグ名なら更新
	 * </p>
	 */
	@Transactional
	public boolean dbOperation() {
		final String METHOD_NAME = "dbOperation";
		try {
			int result = this.teamMemberMasterBatch.execute();
			if (result == BatchConstant.BATCH_ERROR) {
				manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, null,
						"teamMemberMasterBatch execute failed");
				return false;
			}
			return true;

		} catch (Exception e) {
			manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
			return false;
		}
	}

}
