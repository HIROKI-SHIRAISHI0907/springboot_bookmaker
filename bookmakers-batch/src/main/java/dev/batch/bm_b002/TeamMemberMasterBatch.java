package dev.batch.bm_b002;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.common.AbstractJobBatchTemplate;
import dev.common.entity.TeamMemberMasterEntity;
import dev.common.getinfo.GetTeamMemberInfo;

/**
 * 選手登録バッチ実行クラス。
 *
 * 選手CSVデータを取得し、登録ロジックを実行する。
 *
 * @author shiraishitoshio
 */
@Service("B002")
public class TeamMemberMasterBatch extends AbstractJobBatchTemplate {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamMemberMasterBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamMemberMasterBatch.class.getName();

	/** エラーコード */
	private static final String ERROR_CODE = "BM_B002_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B002";

	/**
	 * 今回データが「全件取得」であるかどうか
	 *
	 * true  : 引退判定まで行う
	 * false : 新規/更新/移籍のみ行い、引退判定はしない
	 *
	 * ※ 取得漏れの可能性があるなら false 推奨
	 */
	private static final boolean FULL_SNAPSHOT = false;

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

	/** 選手情報取得管理クラス */
	@Autowired
	private GetTeamMemberInfo getMemberInfo;

	/** 選手登録・更新ロジック */
	@Autowired
	private TeamMemberMasterStat teamMemberMasterStat;

	/**
	 * バッチ実行本体
	 */
	@Override
	protected void doExecute(JobContext ctx) throws Exception {

		Map<String, List<TeamMemberMasterEntity>> getMemberMap = this.getMemberInfo.getData();

		List<TeamMemberMasterEntity> allMembers = flattenMemberMap(getMemberMap);

		this.teamMemberMasterStat.execute(allMembers, FULL_SNAPSHOT);
	}

	/**
	 * Map<String, List<TeamMemberMasterEntity>> を1本の List に平坦化する
	 *
	 * @param memberMap 取得データ
	 * @return 全選手リスト
	 */
	private List<TeamMemberMasterEntity> flattenMemberMap(Map<String, List<TeamMemberMasterEntity>> memberMap) {

		if (memberMap == null || memberMap.isEmpty()) {
			return Collections.emptyList();
		}

		List<TeamMemberMasterEntity> result = new ArrayList<>();

		for (Map.Entry<String, List<TeamMemberMasterEntity>> entry : memberMap.entrySet()) {
			List<TeamMemberMasterEntity> list = entry.getValue();
			if (list == null || list.isEmpty()) {
				continue;
			}
			result.addAll(list);
		}

		return result;
	}
}
