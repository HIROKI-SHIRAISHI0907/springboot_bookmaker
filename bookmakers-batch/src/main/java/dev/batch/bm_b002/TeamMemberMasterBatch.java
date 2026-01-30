package dev.batch.bm_b002;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.common.AbstractJobBatchTemplate;
import dev.common.entity.TeamMemberMasterEntity;
import dev.common.getinfo.GetTeamMemberInfo;

/**
 * 選手登録バッチ実行クラス。
 * <p>
 * 選手CSVデータを取得し、登録ロジック（Transactional想定）を実行する。
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
@Service("B002")
public class TeamMemberMasterBatch extends AbstractJobBatchTemplate {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamMemberMasterBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamMemberMasterBatch.class.getSimpleName();

	/** エラーコード（運用ルールに合わせて変更） */
	private static final String ERROR_CODE = "BM_B002_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B002";

	/** オーバーライド */
	@Override protected String batchCode() { return BATCH_CODE; }
    @Override protected String errorCode() { return ERROR_CODE; }
    @Override protected String projectName() { return PROJECT_NAME; }
    @Override protected String className() { return CLASS_NAME; }

	/** 選手情報取得管理クラス */
	@Autowired
	private GetTeamMemberInfo getMemberInfo;

	/** BM_M028統計分析ロジック */
	@Autowired
	private TeamMemberMasterStat teamMemberMasterStat;

	/**
	 * {@inheritDoc}
	 */
	@Override
    protected void doExecute(JobContext ctx) throws Exception {
        Map<String, List<TeamMemberMasterEntity>> getMemberMap = this.getMemberInfo.getData();
        this.teamMemberMasterStat.teamMemberStat(getMemberMap);
    }
}
