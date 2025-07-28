package dev.application.main.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.application.analyze.bm_m029.CountryLeagueSeasonMasterStat;
import dev.application.analyze.common.util.BatchResultConst;
import dev.application.analyze.interf.BatchIF;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.getstatinfo.GetSeasonInfo;
import dev.common.logger.ManageLoggerComponent;

/**
 * シーズン登録バッチ実行クラス
 * @author shiraishitoshio
 *
 */
@Service
public class SeasonBatch implements BatchIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = SeasonBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = SeasonBatch.class.getSimpleName();

	/**
	 * シーズン情報取得管理クラス
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

		// 選手CSVデータ情報を取得
		Map<String, List<CountryLeagueSeasonMasterEntity>> getSeasonMap = this.getSeasonInfo.getData();
		// BM_M029登録(Transactional)
		try {
			this.countryLeagueSeasonMasterStat.seasonStat(getSeasonMap);
		} catch (Exception e) {
			// エラー
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, e.getMessage(), e);
			return BatchResultConst.BATCH_ERR;
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		return BatchResultConst.BATCH_OK;
	}

}
