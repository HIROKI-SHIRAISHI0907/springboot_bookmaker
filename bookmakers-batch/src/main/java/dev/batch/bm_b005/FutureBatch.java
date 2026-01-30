package dev.batch.bm_b005;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.common.AbstractJobBatchTemplate;
import dev.common.entity.FutureEntity;
import dev.common.getinfo.GetFutureInfo;

/**
 * 未来統計用サービスクラス。
 * <p>
 * 未来試合情報（CSV等）を取得し、登録ロジック（Transactional想定）を実行する。
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
@Service("B005")
public class FutureBatch extends AbstractJobBatchTemplate {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = FutureBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = FutureBatch.class.getSimpleName();

	/** エラーコード（運用ルールに合わせて変更） */
	private static final String ERROR_CODE = "BM_B005_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B005";

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

	/** 未来情報取得管理クラス */
	@Autowired
	private GetFutureInfo getFutureInfo;

	/** BM_M022未来データ登録ロジック */
	@Autowired
	private FutureStat futureStat;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doExecute(JobContext ctx) throws Exception {
		// 未来CSVデータ情報を取得
		Map<String, List<FutureEntity>> getFutureMap = this.getFutureInfo.getData();
		// BM_M022登録(Transactional)
		this.futureStat.futureStat(getFutureMap);
	}
}
