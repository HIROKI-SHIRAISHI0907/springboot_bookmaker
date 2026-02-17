package dev.batch.bm_b003;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.common.AbstractJobBatchTemplate;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.getinfo.GetSeasonInfo;

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
public class CountryLeagueSeasonMasterBatch extends AbstractJobBatchTemplate {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CountryLeagueSeasonMasterBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CountryLeagueSeasonMasterBatch.class.getName();

	/** エラーコード（運用ルールに合わせて変更） */
	private static final String ERROR_CODE = "BM_B003_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B003";

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

	/** チーム情報取得管理クラス */
	@Autowired
	private GetSeasonInfo getSeasonInfo;

	/** BM_M029統計分析ロジック */
	@Autowired
	private CountryLeagueSeasonMasterStat countryLeagueSeasonMasterStat;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doExecute(JobContext ctx) throws Exception {
		// チームデータ情報を取得
		List<CountryLeagueSeasonMasterEntity> list = this.getSeasonInfo.getData();
		// BM_M029登録(Transactional)
		this.countryLeagueSeasonMasterStat.seasonStat(list);
	}
}
