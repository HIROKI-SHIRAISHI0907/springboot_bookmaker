package dev.batch.bm_b004;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.batch.interf.MasterEntityIF;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.FileDeleteUtil;

/**
 * country_league_masterロジック
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
	private static final String EXEC_MODE = "COUNTRY_LEAGUE_MASTER";

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
	public void masterStat(String file,
			List<CountryLeagueMasterEntity> entities) throws Exception {
		final String METHOD_NAME = "masterStat";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		List<String> insertPath = new ArrayList<String>();
		// 今後のチーム情報を登録する
		for (CountryLeagueMasterEntity entity : entities) {
			try {
				CountryLeagueMasterEntity insertEntities = this.countryLeagueDBService
						.selectInBatch(entity);
				if (insertEntities == null) continue;
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
		// ファイル追加
		insertPath.add(file);

		// 途中で例外が起きなければ全てのファイルを削除する
		FileDeleteUtil.deleteFiles(
				insertPath,
				manageLoggerComponent,
				PROJECT_NAME,
				CLASS_NAME,
				METHOD_NAME,
				"TEAM_MASTER");

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}
}
