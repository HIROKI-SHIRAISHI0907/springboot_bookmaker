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
 *
 * <p>2026-10 修正</p>
 * <ul>
 *   <li>削除するバケットを、読み込み元（GetAllLeagueInfo）と同じ {@code getS3BucketsAllLeagueData()} にした。
 *       以前は {@code getS3BucketsTeamSeasonDateData()}（シーズン用のバケット）から削除しようとしていたため、
 *       バケットが別だと all_league_master.csv が削除されずに残っていた。</li>
 *   <li>{@link #deleteProcessedFile(String)} を追加: 登録対象が 0 件（ヘッダのみ）のファイルを、B007 から削除だけ行う。</li>
 * </ul>
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

		boolean succeeded = false;
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

			succeeded = true;

		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			throw new Exception(messageCd, e);
		} finally {
			try {
				// 成功した場合だけ削除する（失敗したファイルは次回もう一度読む）
				if (succeeded) {
					deleteProcessedFile(fileName, METHOD_NAME);
				}
			} finally {
				this.manageLoggerComponent.debugEndInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME);
				this.manageLoggerComponent.clear();
			}
		}
	}

	/**
	 * 処理済みのファイルを S3 から削除する（2026-10 追加）。
	 * 登録対象が 0 件（ヘッダのみ）のファイルは、B007 からこれだけを呼ぶ。
	 *
	 * @param fileName 削除するファイル（S3 キー）
	 */
	public void deleteProcessedFile(String fileName) {
		deleteProcessedFile(fileName, "deleteProcessedFile");
	}

	private void deleteProcessedFile(String fileName, String methodName) {
		if (!hasMeaningfulValue(fileName)) {
			return;
		}
		List<String> insertPath = new ArrayList<>();
		insertPath.add(fileName);

		// 読み込み元（GetAllLeagueInfo）と同じバケット
		String bucket = config.getS3BucketsAllLeagueData();

		FileDeleteUtil.deleteS3Files(
				insertPath,
				bucket,
				s3Operator,
				manageLoggerComponent,
				PROJECT_NAME,
				CLASS_NAME,
				methodName,
				"ALL_LEAGUE_MASTER");
	}

	private boolean hasMeaningfulValue(String value) {
		if (value == null) {
			return false;
		}
		return !value.trim().isEmpty();
	}
}