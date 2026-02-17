package dev.batch.bm_b007;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.common.AbstractJobBatchTemplate;
import dev.common.entity.AllLeagueMasterEntity;
import dev.common.getinfo.GetAllLeagueInfo;

/**
 * 全容リーグデータ登録バッチ実行クラス。
 * <p>
 * 国・リーグの全容マスタデータを取得し、登録ロジック（Transactional想定）を実行する。
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
@Service("B007")
public class AllLeagueMasterBatch extends AbstractJobBatchTemplate {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = AllLeagueMasterBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = AllLeagueMasterBatch.class.getName();

	/** エラーコード（運用ルールに合わせて変更） */
	private static final String ERROR_CODE = "BM_B007_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B007";

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

	/** 全容マスタ情報取得管理クラス */
	@Autowired
	private GetAllLeagueInfo getAllLeagueInfo;

	/** BM_M032統計分析ロジック */
	@Autowired
	private AllLeagueMasterStat allLeagueMasterStat;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doExecute(JobContext ctx) throws Exception {
		final String METHOD_NAME = "doExecute";
		// 全容マスタデータ情報を取得
		Map<String, List<AllLeagueMasterEntity>> listMap = this.getAllLeagueInfo.getData();

		// 登録(Transactional)
		for (Map.Entry<String, List<AllLeagueMasterEntity>> entry : listMap.entrySet()) {
			try {
				this.allLeagueMasterStat.masterStat(entry.getKey(), entry.getValue());
			} catch (Exception e) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
				continue;
			}
		}
	}
}
