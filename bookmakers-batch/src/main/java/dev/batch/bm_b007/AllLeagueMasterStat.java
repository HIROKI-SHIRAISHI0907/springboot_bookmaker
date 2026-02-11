package dev.batch.bm_b007;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.batch.interf.AllMasterEntityIF;
import dev.common.constant.MessageCdConst;
import dev.common.entity.AllLeagueMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.FileDeleteUtil;

/**
 * all_league_masterロジック
 * @author shiraishitoshio
 *
 */
@Component
public class AllLeagueMasterStat implements AllMasterEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = AllLeagueMasterStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = AllLeagueMasterStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "ALL_LEAGUE_MASTER";

	/** CountryLeagueDBService部品 */
	@Autowired
	private AllLeagueDBService allLeagueDBService;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void masterStat(String file,
			List<AllLeagueMasterEntity> entities) throws Exception {
		final String METHOD_NAME = "masterStat";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		List<String> insertPath = new ArrayList<String>();
		// 今後の全容マスタ情報を登録する
		for (AllLeagueMasterEntity entity : entities) {
			try {
				AllLeagueMasterEntity insertEntities = this.allLeagueDBService
						.selectInBatch(entity);
				if (insertEntities == null) continue;
				int result = this.allLeagueDBService.insertInBatch(insertEntities);
				if (result == 9) {
					String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
					throw new Exception(messageCd);
				}
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
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
				"ALL_LEAGUE_MASTER");

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}
}
