package dev.batch.bm_b003;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.interf.SeasonEntityIF;
import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.FileDeleteUtil;

/**
 * country_league_season_masterロジック
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
	private static final String CLASS_NAME = CountryLeagueSeasonMasterStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "COUNTRY_LEAGUE_SEASON";

	/** PathConfig */
	@Autowired
	private PathConfig pathConfig;

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

		List<String> insertPath = new ArrayList<String>();
		// 今後のチーム情報を登録する
		try {
			List<CountryLeagueSeasonMasterEntity> insertEntities = this.countryLeagueSeasonDBService
					.selectInBatch(entities);
			int result = this.countryLeagueSeasonDBService.insertInBatch(insertEntities);
			if (result == 9) {
				String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
				throw new Exception(messageCd);
			}
			insertPath.add(pathConfig.getTeamCsvFolder() + "season_data.csv");
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			throw new Exception(messageCd, e);
		}

		// 途中で例外が起きなければ全てのファイルを削除する
		FileDeleteUtil.deleteFiles(
				insertPath,
				manageLoggerComponent,
				PROJECT_NAME,
				CLASS_NAME,
				METHOD_NAME,
				"SEASON_MASTER");

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}
}
