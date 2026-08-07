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
 *
 * 国・リーグの全容マスタデータを取得し、登録ロジックを実行する。
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

	/** エラーコード */
	private static final String ERROR_CODE = "BM_B007_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B007";

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

	/** BM_B007統計分析ロジック */
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
		if (listMap.isEmpty()) {
			endLog();
			return;
		}

		// 登録
		for (Map.Entry<String, List<AllLeagueMasterEntity>> entry : listMap.entrySet()) {
			try {
				this.allLeagueMasterStat.allLeagueStat(entry.getKey(), entry.getValue());
			} catch (Exception e) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
				continue;
			}
		}

		endLog();
	}

	/**
	 * 終了ログ
	 */
	private void endLog() {
		final String METHOD_NAME = "endLog";
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
	}
}
