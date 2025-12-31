package dev.application.analyze.bm_m032;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.interf.MasterEntityIF;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M032統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
public class CountryLeagueMasterStat implements MasterEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CountryLeagueMasterStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CountryLeagueMasterStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M032_COUNTRY_LEAGUE_MASTER";

	/** CountryLeagueDBService部品 */
	@Autowired
	private CountryLeagueDBService countryLeagueDBService;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void masterStat(List<List<CountryLeagueMasterEntity>> entities) throws Exception {
		final String METHOD_NAME = "masterStat";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 今後のチーム情報を登録する
		for (List<CountryLeagueMasterEntity> entity : entities) {
			try {
				List<CountryLeagueMasterEntity> insertEntities = this.countryLeagueDBService
						.selectInBatch(entity);
				int result = this.countryLeagueDBService.insertInBatch(insertEntities);
				if (result == 9) {
					String messageCd = "新規登録エラー";
					throw new Exception(messageCd);
				}
			} catch (Exception e) {
				String messageCd = "システムエラー";
				throw new Exception(messageCd, e);
			}
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}
}
