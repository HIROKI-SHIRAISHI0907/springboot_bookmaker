package dev.batch.bm_b002;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.common.AbstractJobBatchTemplate;
import dev.common.constant.MessageCdConst;
import dev.common.entity.TeamMemberMasterEntity;
import dev.common.getinfo.GetTeamMemberInfo;

/**
 * 選手登録バッチ実行クラス。
 *
 * 選手CSVデータを取得し、登録ロジックを実行する。
 *
 * <p>2026-10 修正（ヘッダしかない CSV が S3 に残り続ける問題）</p>
 * <ul>
 *   <li>GetTeamMemberInfo はヘッダのみのファイルも「キー → 空のリスト」で返す。</li>
 *   <li>全ファイルがヘッダのみ（登録する選手が 0 人）の場合は、登録処理はせずにファイルの削除だけ行う。</li>
 *   <li>選手がいる場合は、読めた全ファイル（ヘッダのみを含む）を削除対象として登録処理に渡す。</li>
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
		final String METHOD_NAME = "doExecute";

		Map<String, List<TeamMemberMasterEntity>> getMemberMap = this.getMemberInfo.getData();
		if (getMemberMap == null || getMemberMap.isEmpty()) {
			endLog();
			return;
		}

		// 読めた全ファイル（ヘッダのみを含む）
		List<String> fileKeys = new ArrayList<>(getMemberMap.keySet());
		List<TeamMemberMasterEntity> allMembers = flattenMemberMap(getMemberMap);

		if (allMembers.isEmpty()) {
			// 全ファイルがヘッダのみ: 登録はせず、ファイルの削除だけ行う（残すと毎回同じファイルを読み続ける）
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					"登録対象の選手が0人（全ファイルがヘッダのみ）のため、ファイルの削除のみ行います: " + fileKeys);
			this.teamMemberMasterStat.deleteProcessedFiles(fileKeys);
			endLog();
			return;
		}

		this.teamMemberMasterStat.execute(allMembers, FULL_SNAPSHOT, fileKeys);

		endLog();
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

	/**
	 * 終了ログ
	 */
	private void endLog() {
		final String METHOD_NAME = "endLog";
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
	}
}