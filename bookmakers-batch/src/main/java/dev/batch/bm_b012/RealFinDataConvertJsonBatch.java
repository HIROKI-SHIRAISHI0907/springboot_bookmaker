package dev.batch.bm_b012;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.common.AbstractJobBatchTemplate;

/**
 * JSON変換用
 *
 * <ol>
 *   <li>前日の「終了済が無い試合」を fin/b008_fin_getting_data_N.json にする（{@link RealFinDataConvertJsonStat}）。</li>
 *   <li>前日・前々日に開始した試合の詳細ページ URL を b016_game_detail_path.json にする（{@link GameDetailPathJsonStat}。B016 試合詳細スクレイピングの入力）。</li>
 * </ol>
 * <p>2 つは独立しているので、片方が失敗してももう片方は実行する。どちらかが失敗したら最後に例外を投げる（バッチは異常終了）。</p>
 * @author shiraishitoshio
 *
 */
@Service("B012")
public class RealFinDataConvertJsonBatch extends AbstractJobBatchTemplate {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = RealFinDataConvertJsonBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = RealFinDataConvertJsonBatch.class.getName();

	/** エラーコード（運用ルールに合わせて変更） */
	private static final String ERROR_CODE = "BM_B012_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B012";

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

	/** RealDataConvertJsonStat */
	@Autowired
	private RealFinDataConvertJsonStat realFinDataConvertJsonStat;

	/** GameDetailPathJsonStat（B016 の入力 JSON） */
	@Autowired
	private GameDetailPathJsonStat gameDetailPathJsonStat;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doExecute(JobContext ctx) throws Exception {
		Exception failure = null;

		try {
			this.realFinDataConvertJsonStat.execute();
		} catch (Exception e) {
			failure = e;
		}

		try {
			this.gameDetailPathJsonStat.execute();
		} catch (Exception e) {
			if (failure == null) {
				failure = e;
			} else {
				failure.addSuppressed(e);
			}
		}

		if (failure != null) {
			throw failure;
		}
	}

}
