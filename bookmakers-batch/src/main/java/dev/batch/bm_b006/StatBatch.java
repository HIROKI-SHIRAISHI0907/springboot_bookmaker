package dev.batch.bm_b006;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import dev.application.main.service.CoreStat;
import dev.batch.common.AbstractJobBatchTemplate;
import dev.common.entity.BookDataEntity;
import dev.common.getinfo.GetStatInfo;

/**
 * 統計分析バッチ実行クラス。
 * <p>
 * 統計データ用に生成した情報を取得し、登録ロジック（Transactional想定）を実行する。
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
@ConditionalOnProperty(name="batch.mode", havingValue="worker")
public class StatBatch extends AbstractJobBatchTemplate {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = StatBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = StatBatch.class.getSimpleName();

	/** エラーコード（運用ルールに合わせて変更） */
	private static final String ERROR_CODE = "BM_B006_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B006";

	/** オーバーライド */
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

	/** 統計生成情報取得管理クラス */
	@Autowired
	private GetStatInfo getStatInfo;

	/** CoreStat部品 */
	@Autowired
	private CoreStat coreStat;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doExecute(JobContext ctx) throws Exception {
		// シーケンス情報を取得

		// リアルタイムデータ情報を取得
		Map<String, Map<String, List<BookDataEntity>>> stat = this.getStatInfo.getData(null, null);

		// 統計データ登録(Transactional)
		this.coreStat.execute(stat);

		// シーケンス情報を更新
	}
}
