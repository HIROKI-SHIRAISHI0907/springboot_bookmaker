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
import dev.common.s3.S3Operator;
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

	/** CountryLeagueSeasonDBService部品 */
	@Autowired
	private CountryLeagueSeasonDBService countryLeagueSeasonDBService;

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
	public void seasonStat(List<CountryLeagueSeasonMasterEntity> entities) throws Exception {
		final String METHOD_NAME = "calcStat";

		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		List<String> insertPath = new ArrayList<>();

		try {
			CountryLeagueSeasonDBService.SeasonUpsertPlan plan =
					this.countryLeagueSeasonDBService.selectInBatch(entities);

			int insertResult = this.countryLeagueSeasonDBService.insertInBatch(plan.getInsertEntities());
			if (insertResult == 9) {
				String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
				throw new Exception(messageCd);
			}

			int updateResult = this.countryLeagueSeasonDBService.updateInBatch(plan.getUpdateEntities());
			if (updateResult == 9) {
				String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
				throw new Exception(messageCd);
			}

			insertPath.add("season_data.csv");

		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			throw new Exception(messageCd, e);
		}

		String bucket = config.getS3BucketsTeamSeasonDateData();

		FileDeleteUtil.deleteS3Files(
				insertPath,
				bucket,
				s3Operator,
				manageLoggerComponent,
				PROJECT_NAME,
				CLASS_NAME,
				METHOD_NAME,
				"SEASON_MASTER");

		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}
}
