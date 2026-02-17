package dev.batch.bm_b004;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.common.AbstractJobBatchTemplate;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.getinfo.GetTeamInfo;

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
public class CountryLeagueMasterBatch extends AbstractJobBatchTemplate {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CountryLeagueMasterBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CountryLeagueMasterBatch.class.getName();

	/** エラーコード（運用ルールに合わせて変更） */
	private static final String ERROR_CODE = "BM_B004_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B004";

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

	/** マスタ情報取得管理クラス */
	@Autowired
	private GetTeamInfo getTeamMasterInfo;

	/** BM_M032統計分析ロジック */
	@Autowired
	private CountryLeagueMasterStat countryLeagueMasterStat;

	/** BM_M032統計分析ロジック */
	@Autowired
	private ColorMasterStat colorMasterStat;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doExecute(JobContext ctx) throws Exception {
		final String METHOD_NAME = "doExecute";
		// マスタデータ情報を取得
		Map<String, List<CountryLeagueMasterEntity>> listMap = this.getTeamMasterInfo.getData();

		// 登録(Transactional)
		for (Map.Entry<String, List<CountryLeagueMasterEntity>> entry : listMap.entrySet()) {
			try {
				this.countryLeagueMasterStat.masterStat(entry.getKey(), entry.getValue());
				this.colorMasterStat.masterStat(entry.getKey(), entry.getValue());
			} catch (Exception e) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
				continue;
			}
		}
	}
}
