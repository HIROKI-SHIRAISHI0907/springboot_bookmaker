package dev.application.analyze.bm_m029;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.interf.SeasonEntityIF;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M029統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
@Transactional
public class CountryLeagueSeasonMasterStat implements SeasonEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CountryLeagueSeasonMasterStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CountryLeagueSeasonMasterStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M029_COUNTRY_LEAGUE_SEASON";

	/** CountryLeagueSeasonDBService部品 */
	@Autowired
	private CountryLeagueSeasonDBService countryLeagueSeasonDBService;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void seasonStat(List<CountryLeagueSeasonMasterEntity> entities) throws Exception {
		final String METHOD_NAME = "calcStat";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 今後のチーム情報を登録する
		try {
			List<CountryLeagueSeasonMasterEntity> insertEntities = this.countryLeagueSeasonDBService
					.selectInBatch(entities);
			int result = this.countryLeagueSeasonDBService.insertInBatch(insertEntities);
			if (result == 9) {
				String messageCd = "新規登録エラー";
				throw new Exception(messageCd);
			}
		} catch (Exception e) {
			String messageCd = "システムエラー";
			throw new Exception(messageCd, e);
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}
}
