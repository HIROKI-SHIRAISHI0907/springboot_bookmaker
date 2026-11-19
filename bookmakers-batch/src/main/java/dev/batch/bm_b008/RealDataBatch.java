package dev.batch.bm_b008;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.common.AbstractJobBatchTemplate;

/**
 * B008 開催中データ（リアルタイムデータ）登録バッチ（2026-11-20 bookmakers-app から移動）
 *
 * <p>以前は bookmakers-app の BmJobRunner（BM_JOB=B008 → OriginService）で動かしていた。
 *    統計（B006 など）は bookmakers-app、データの取り込みは bookmakers-batch に分けるため、こちらへ移した。</p>
 * <p>処理の中身は {@link OriginService} のまま（以前と同じ順番）。</p>
 * <ol>
 *   <li>BM_M001: 直近のリアルタイムデータ CSV を static_data に登録（OriginStat）</li>
 *   <li>BM_M029: リアルタイムデータの集計（RealDataProcessStat）</li>
 *   <li>中断・延期・中止・再開の検知（SuspensionStatusPublisher）</li>
 *   <li>未来データのフラグ更新（OriginFutureStartFlgService）</li>
 * </ol>
 *
 * @author shiraishitoshio
 */
@Service("B008")
public class RealDataBatch extends AbstractJobBatchTemplate {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = RealDataBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = RealDataBatch.class.getName();

	/** エラーコード */
	private static final String ERROR_CODE = "BM_B008_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B008";

	@Autowired
	private OriginService originService;

	@Override
	protected String batchCode() {
		return BATCH_CODE;
	}

	@Override
	protected String errorCode() {
		return ERROR_CODE;
	}

	@Override
	protected String projectName() {
		return PROJECT_NAME;
	}

	@Override
	protected String className() {
		return CLASS_NAME;
	}

	@Override
	protected void doExecute(JobContext ctx) throws Exception {
		final String METHOD_NAME = "doExecute";
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		try {
			int rc = this.originService.execute();
			if (rc != 0) {
				throw new IllegalStateException("OriginService が " + rc + " を返しました");
			}
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
			throw e;
		}
		this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
	}
}
