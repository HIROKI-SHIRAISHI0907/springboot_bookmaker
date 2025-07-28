package dev.application.analyze.bm_m029;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
	public void seasonStat(Map<String, List<CountryLeagueSeasonMasterEntity>> entities) throws Exception{
		final String METHOD_NAME = "calcStat";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		List<String> insertPath = new ArrayList<String>();
		// 今後の対戦カードを登録する
		for (Map.Entry<String, List<CountryLeagueSeasonMasterEntity>> map : entities.entrySet()) {
			String filePath = map.getKey();
			String fillChar = "ファイル名: " + filePath;
			try {
				List<CountryLeagueSeasonMasterEntity> insertEntities = this.countryLeagueSeasonDBService.
						selectInBatch(map.getValue(), fillChar);
				int result = this.countryLeagueSeasonDBService.insertInBatch(insertEntities);
				if (result == 9) {
					String messageCd = "新規登録エラー";
					throw new Exception(messageCd);
				}
				insertPath.add(filePath);
			} catch (Exception e) {
				String messageCd = "システムエラー";
				throw new Exception(messageCd, e);
			}
		}

		// 途中で例外が起きなければ全てのファイルを削除する
		for (String path : insertPath) {
		    try {
		        Files.deleteIfExists(Paths.get(path));
		    } catch (IOException e) {
		        this.manageLoggerComponent.debugErrorLog(
		            PROJECT_NAME, CLASS_NAME, METHOD_NAME, "ファイル削除失敗", e, path);
		        // ここでは例外をthrowしないことで、DB登録は保持
		    }
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

}
