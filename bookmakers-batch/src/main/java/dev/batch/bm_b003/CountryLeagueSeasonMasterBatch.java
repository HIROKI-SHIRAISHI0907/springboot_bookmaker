package dev.batch.bm_b003;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.interf.BatchIF;
import dev.batch.interf.jobExecControlIF;
import dev.batch.util.JobIdUtil;
import dev.common.constant.BatchConstant;
import dev.common.constant.MessageCdConst;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.getinfo.GetSeasonInfo;
import dev.common.logger.ManageLoggerComponent;

/**
 * シーズン情報登録バッチ実行クラス。
 * <p>
 * シーズンデータを取得し、登録ロジック（Transactional想定）を実行する。
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
@Service("B003")
public class CountryLeagueSeasonMasterBatch implements BatchIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CountryLeagueSeasonMasterBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CountryLeagueSeasonMasterBatch.class.getSimpleName();

	/** エラーコード（運用ルールに合わせて変更） */
	private static final String ERROR_CODE = "BM_B003_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B003";

	/** チーム情報取得管理クラス */
	@Autowired
	private GetSeasonInfo getSeasonInfo;

	/** BM_M029統計分析ロジック */
	@Autowired
	private CountryLeagueSeasonMasterStat countryLeagueSeasonMasterStat;

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

		// jobId採番（B003-xxxxx）
		String jobId = JobIdUtil.generate(BATCH_CODE);
		boolean jobInserted = false;
		try {
			// 0: QUEUED（受付）
			boolean started = jobExecControl.jobStart(jobId, BATCH_CODE);
			if (!started) {
				this.manageLoggerComponent.debugWarnLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE,
						"jobStart failed (duplicate or insert error). jobId=" + jobId);
				return BatchConstant.BATCH_ERROR;
			}
			jobInserted = true;

			// チームデータ情報を取得
			List<CountryLeagueSeasonMasterEntity> list = this.getSeasonInfo.getData();

			// BM_M029登録(Transactional)
			this.countryLeagueSeasonMasterStat.seasonStat(list);

			String messageCd = MessageCdConst.MCD00015I_BATCH_ACCEPTED;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					BATCH_CODE + " accepted. jobId=" + jobId);

			return BatchConstant.BATCH_SUCCESS;

		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
			if (jobInserted) {
				try {
					jobExecControl.jobException(jobId);
				} catch (Exception ignore) {
				}
			}
			return BatchConstant.BATCH_ERROR;

		} finally {
			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		}
	}
}
