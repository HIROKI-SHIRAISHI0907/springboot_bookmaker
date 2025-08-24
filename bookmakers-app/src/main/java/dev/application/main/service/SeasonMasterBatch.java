package dev.application.main.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.application.analyze.bm_m029.CountryLeagueSeasonMasterStat;
import dev.application.analyze.common.util.BatchResultConst;
import dev.application.analyze.interf.BatchIF;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.getstatinfo.GetSeasonInfo;
import dev.common.logger.ManageLoggerComponent;

/**
 * チーム登録バッチ実行クラス
 * @author shiraishitoshio
 *
 */
@Service
public class SeasonMasterBatch implements BatchIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = SeasonMasterBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = SeasonMasterBatch.class.getSimpleName();

	/**
	 * チーム情報取得管理クラス
	 */
	@Autowired
	private GetSeasonInfo getSeasonInfo;

	/** BM_M029統計分析ロジック */
	@Autowired
	private CountryLeagueSeasonMasterStat countryLeagueSeasonMasterStat;

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

		// 選手XLSXデータ情報を取得
		List<CountryLeagueSeasonMasterEntity> getMemberList = this.getSeasonInfo.getData();
		// BM_M029登録(Transactional)
		try {
			this.countryLeagueSeasonMasterStat.seasonStat(getMemberList);
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
