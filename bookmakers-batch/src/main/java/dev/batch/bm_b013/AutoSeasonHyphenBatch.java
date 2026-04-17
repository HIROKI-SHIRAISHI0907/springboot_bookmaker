package dev.batch.bm_b013;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.common.AbstractJobBatchTemplate;

/**
 * シーズン終了時ハイフン処理実行クラス。
 * <p>
 * country_league_season_masterの「終了日」がシステム日時を超えた時、自動でハイフンにする処理を実行する。<br>
 * 尚,このクラスは毎日0:10に実行する
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
@Service("B013")
public class AutoSeasonHyphenBatch extends AbstractJobBatchTemplate {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = AutoSeasonHyphenBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = AutoSeasonHyphenBatch.class.getName();

	/** エラーコード（運用ルールに合わせて変更） */
	private static final String ERROR_CODE = "BM_B013_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B013";

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

	/** AutoSeasonHyphenStatクラス */
	@Autowired
	private AutoSeasonHyphenStat autoSeasonHyphenStat;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doExecute(JobContext ctx) throws Exception {
		// AutoSeasonHyphenStat(Transactional)
		this.autoSeasonHyphenStat.execute();
	}
}
