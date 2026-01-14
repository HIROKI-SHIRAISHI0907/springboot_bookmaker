package dev.batch.bm_b004;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.constant.BatchConstant;
import dev.batch.interf.BatchIF;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.getstatinfo.GetTeamMasterInfo;
import dev.common.logger.ManageLoggerComponent;

/**
 * マスタ登録バッチ実行クラス。
 * <p>
 * 国・リーグなどのマスタデータを取得し、登録ロジック（Transactional想定）を実行する。
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
@Service("B004")
public class MasterBatch implements BatchIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = MasterBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = MasterBatch.class.getSimpleName();

	/** エラーコード（運用ルールに合わせて変更） */
	private static final String ERROR_CODE = "BM_B004_ERROR";

	/** マスタ情報取得管理クラス */
	@Autowired
	private GetTeamMasterInfo getTeamMasterInfo;

	/** BM_M032統計分析ロジック */
	@Autowired
	private CountryLeagueMasterStat countryLeagueMasterStat;

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

		try {
			// マスタデータ情報を取得
			Map<String, List<CountryLeagueMasterEntity>> listMap = this.getTeamMasterInfo.getData();

			// 登録(Transactional)
			for (Map.Entry<String, List<CountryLeagueMasterEntity>> entry : listMap.entrySet()) {
				try {
					this.countryLeagueMasterStat.masterStat(entry.getKey(), entry.getValue());
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
