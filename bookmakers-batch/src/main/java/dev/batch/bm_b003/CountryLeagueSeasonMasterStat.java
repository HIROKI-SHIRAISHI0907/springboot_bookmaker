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
import dev.common.getinfo.GetSeasonInfo;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.common.util.FileDeleteUtil;

/**
 * country_league_season_masterロジック
 *
 * <p>2026-10 修正（ヘッダしかない CSV が S3 に残り続ける問題）</p>
 * <ul>
 *   <li>{@link #deleteProcessedFile()} を追加: 登録対象が 0 件（ヘッダのみ）の場合に、B003 から削除だけ行う。</li>
 *   <li>seasonStat の削除処理も同じメソッドを使う（削除するキーは GetSeasonInfo.SEASON_KEY にそろえた）。</li>
 * </ul>
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

			// CSVに存在した country + league は、差分有無に関係なくモーダル表示対象へ戻す
			int initialFlgResetResult = this.countryLeagueSeasonDBService.resetInitialFlgByIncomingTargets(entities);
			if (initialFlgResetResult == 9) {
				String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
				throw new Exception(messageCd);
			}

		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			throw new Exception(messageCd, e);
		}

		// 途中で例外が起きなければファイルを削除する
		deleteProcessedFile(METHOD_NAME);

		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * 処理済みの season_data.csv を S3 から削除する（2026-10 追加）。
	 * 登録対象が 0 件（ヘッダのみ）の場合は、B003 からこれだけを呼ぶ。
	 */
	public void deleteProcessedFile() {
		deleteProcessedFile("deleteProcessedFile");
	}

	private void deleteProcessedFile(String methodName) {
		List<String> insertPath = new ArrayList<>();
		insertPath.add(GetSeasonInfo.SEASON_KEY);

		String bucket = config.getS3BucketsTeamSeasonDateData();

		FileDeleteUtil.deleteS3Files(
				insertPath,
				bucket,
				s3Operator,
				manageLoggerComponent,
				PROJECT_NAME,
				CLASS_NAME,
				methodName,
				"SEASON_MASTER");
	}
}