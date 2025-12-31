package dev.application.main.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.application.analyze.bm_m032.CountryLeagueMasterStat;
import dev.application.analyze.common.util.BatchResultConst;
import dev.application.analyze.interf.BatchIF;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.getstatinfo.GetTeamMasterInfo;
import dev.common.logger.ManageLoggerComponent;

/**
 * マスタ登録バッチ実行クラス
 * @author shiraishitoshio
 *
 */
@Service
public class MasterBatch implements BatchIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = MasterBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = MasterBatch.class.getSimpleName();

	/**
	 * マスタ情報取得管理クラス
	 */
	@Autowired
	private GetTeamMasterInfo getTeamMasterInfo;

	/** BM_M032統計分析ロジック */
	@Autowired
	private CountryLeagueMasterStat countryLeagueMasterStat;

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

		// マスタデータ情報を取得
		List<List<CountryLeagueMasterEntity>> getMemberList = this.getTeamMasterInfo.getData();
		// BM_M032登録(Transactional)
		try {
			this.countryLeagueMasterStat.masterStat(getMemberList);
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
