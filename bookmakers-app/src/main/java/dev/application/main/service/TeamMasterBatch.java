package dev.application.main.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.application.analyze.bm_m028.TeamMemberMasterStat;
import dev.application.analyze.common.util.BatchResultConst;
import dev.application.analyze.interf.BatchIF;
import dev.common.entity.TeamMemberMasterEntity;
import dev.common.getstatinfo.GetMemberInfo;
import dev.common.logger.ManageLoggerComponent;

/**
 * 選手登録バッチ実行クラス
 * @author shiraishitoshio
 *
 */
@Service
public class TeamMasterBatch implements BatchIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamMasterBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamMasterBatch.class.getSimpleName();

	/**
	 * 選手情報取得管理クラス
	 */
	@Autowired
	private GetMemberInfo getMemberInfo;

	/** BM_M028統計分析ロジック */
	@Autowired
	private TeamMemberMasterStat teamMemberMasterStat;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int execute() throws Exception {
		final String METHOD_NAME = "execute";
		// ログ出力
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 選手CSVデータ情報を取得
		Map<String, List<TeamMemberMasterEntity>> getMemberMap = this.getMemberInfo.getData();
		// BM_M028登録(Transactional)
		try {
			this.teamMemberMasterStat.teamMemberStat(getMemberMap);
		} catch (Exception e) {
			// エラー
			return BatchResultConst.BATCH_ERR;
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		return BatchResultConst.BATCH_OK;
	}

}
