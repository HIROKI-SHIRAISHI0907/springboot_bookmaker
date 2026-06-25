package dev.batch.bm_b004;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.batch.interf.MasterEntityIF;
import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.common.util.FileDeleteUtil;

/**
 * country_league_masterロジック
 *
 * 仕様:
 * - 新規データは insert
 * - 既存データは必要時 update
 * - 新規/更新どちらも initial_reading_master_csv に反映
 *
 * @author shiraishitoshio
 */
@Component
public class CountryLeagueMasterStat implements MasterEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CountryLeagueMasterStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CountryLeagueMasterStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "COUNTRY_LEAGUE_MASTER";

	/** CountryLeagueDBService部品 */
	@Autowired
	private CountryLeagueDBService countryLeagueDBService;

	/** Config */
	@Autowired
	private PathConfig config;

	/** S3Operator */
	@Autowired
	private S3Operator s3Operator;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void masterStat(String file, List<CountryLeagueMasterEntity> entities) throws Exception {
		final String METHOD_NAME = "masterStat";

		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		List<String> insertPath = new ArrayList<>();

		try {
			CountryLeagueDBService.MasterUpsertPlan plan = this.countryLeagueDBService.selectInBatch(entities);

			int insertResult = this.countryLeagueDBService.insertInBatch(plan.getInsertEntities());
			if (insertResult == 9) {
				String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
				throw new Exception(messageCd);
			}

			int updateResult = this.countryLeagueDBService.updateInBatch(plan.getUpdateEntities());
			if (updateResult == 9) {
				String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
				throw new Exception(messageCd);
			}

		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			throw new Exception(messageCd, e);
		}

		insertPath.add(file);

		String bucket = config.getS3BucketsTeamData();
		FileDeleteUtil.deleteS3Files(
				insertPath,
				bucket,
				s3Operator,
				manageLoggerComponent,
				PROJECT_NAME,
				CLASS_NAME,
				METHOD_NAME,
				"TEAM_MASTER");

		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}
}
