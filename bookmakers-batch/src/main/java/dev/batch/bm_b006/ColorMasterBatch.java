package dev.batch.bm_b006;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.constant.BatchConstant;
import dev.batch.interf.BatchIF;
import dev.batch.interf.jobExecControlIF;
import dev.batch.util.JobIdUtil;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.getinfo.GetTeamInfo;
import dev.common.logger.ManageLoggerComponent;

/**
 * 履歴登録バッチ実行クラス。
 * <p>
 * シーズンが終了したデータに対して履歴テーブルに登録する処理を実行する。
 * </p>
 *
 * <p><b>実行方式</b></p>
 * <ul>
 *   <li>開始/終了ログを必ず出力する</li>
 *   <li>例外は内部で捕捉し、debugErrorLog に例外を付与して出力する</li>
 *   <li>戻り値で成功/失敗を返却する</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Service("B006")
public class ColorMasterBatch implements BatchIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ColorMasterBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ColorMasterBatch.class.getSimpleName();

	/** エラーコード（運用ルールに合わせて変更） */
	private static final String ERROR_CODE = "BM_B006_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B006";

	/** マスタ情報取得管理クラス */
	@Autowired
	private GetTeamInfo getTeamMasterInfo;

	/** ColorMasterStat部品 */
	@Autowired
	private ColorMasterStat colorMasterStat;

	/** ジョブ実行制御 */
	@Autowired
	private jobExecControlIF jobExecControl;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * バッチ処理を実行する。
	 *
	 * @return
	 * <ul>
	 *   <li>{@link BatchConstant#BATCH_SUCCESS}：正常終了</li>
	 *   <li>{@link BatchConstant#BATCH_ERROR}：異常終了</li>
	 * </ul>
	 */
	@Override
	public int execute() {
		final String METHOD_NAME = "execute";
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// jobId採番（B006-xxxxx）
		String jobId = JobIdUtil.generate(BATCH_CODE);
		boolean jobInserted = false;
		try {
			// マスタデータ情報を取得
			Map<String, List<CountryLeagueMasterEntity>> listMap = this.getTeamMasterInfo.getData();

			// 色登録(Transactional)
			for (Map.Entry<String, List<CountryLeagueMasterEntity>> entry : listMap.entrySet()) {
				try {
					this.colorMasterStat.masterStat(entry.getKey(), entry.getValue());
				} catch (Exception e) {
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
					continue;
				}
			}
			return BatchConstant.BATCH_SUCCESS;

		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
			return BatchConstant.BATCH_ERROR;

		} finally {
			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		}
	}
}
