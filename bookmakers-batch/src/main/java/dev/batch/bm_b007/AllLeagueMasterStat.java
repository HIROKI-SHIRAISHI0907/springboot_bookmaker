package dev.batch.bm_b007;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.entity.AllLeagueMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.common.util.FileDeleteUtil;

/**
 * all_league_masterロジック
 * @author shiraishitoshio
 *
 */
@Component
@Transactional
public class AllLeagueMasterStat {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = AllLeagueMasterStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = AllLeagueMasterStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "ALL_LEAGUE";

	/** AllLeagueDBService部品 */
	@Autowired
	private AllLeagueDBService allLeagueDBService;

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
	 * 実行
	 * @param fileName 取得元ファイル名（削除対象）
	 * @param entities 登録対象
	 * @throws Exception 例外
	 */
	public void allLeagueStat(String fileName, List<AllLeagueMasterEntity> entities) throws Exception {
		final String METHOD_NAME = "calcStat";

		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		List<String> insertPath = new ArrayList<>();

		try {
			List<AllLeagueMasterEntity> insertTargets =
					this.allLeagueDBService.selectInBatch(entities);

			int insertResult = this.allLeagueDBService.insertInBatch(insertTargets);
			if (insertResult == 9) {
				String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
				throw new Exception(messageCd);
			}

			// CSVに存在した country + league は、差分有無に関係なくモーダル表示対象へ戻す
			int initialFlgResetResult =
					this.allLeagueDBService.resetInitialFlgByIncomingTargets(entities);
			if (initialFlgResetResult == 9) {
				String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
				throw new Exception(messageCd);
			}

			if (hasMeaningfulValue(fileName)) {
				insertPath.add(fileName);
			}

		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			throw new Exception(messageCd, e);
		} finally {
			try {
				String bucket = config.getS3BucketsTeamSeasonDateData();

				FileDeleteUtil.deleteS3Files(
						insertPath,
						bucket,
						s3Operator,
						manageLoggerComponent,
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						"ALL_LEAGUE_MASTER");
			} finally {
				this.manageLoggerComponent.debugEndInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME);
				this.manageLoggerComponent.clear();
			}
		}
	}

	private boolean hasMeaningfulValue(String value) {
		if (value == null) {
			return false;
		}
		return !value.trim().isEmpty();
	}
}
