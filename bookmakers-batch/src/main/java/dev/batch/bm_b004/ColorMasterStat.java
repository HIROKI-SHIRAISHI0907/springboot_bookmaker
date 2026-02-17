package dev.batch.bm_b004;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.interf.MasterEntityIF;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * team_color_masterロジック
 * @author shiraishitoshio
 *
 */
@Component
@Transactional
public class ColorMasterStat implements MasterEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ColorMasterStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ColorMasterStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M089_COLOR_MASTER";

	/** ColorDBService部品 */
	@Autowired
	private ColorDBService colorDBService;

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

		// 今後の色情報を登録する
		for (CountryLeagueMasterEntity entity : entities) {
			try {
				TeamColorMasterEntity insertEntities = this.colorDBService
						.selectInBatch(entity);
				if (insertEntities == null) continue;
				int result = this.colorDBService.insertInBatch(insertEntities);
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
