package dev.batch.bm_b010;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.bm_b005.FutureDBService;
import dev.batch.interf.FinGettingEntityIF;
import dev.common.config.PathConfig;
import dev.common.entity.DataEntity;
import dev.common.entity.FutureEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.common.util.DateUtil;
import dev.common.util.FileDeleteUtil;

/**
 * FinGettingStat登録ロジック
 * @author shiraishitoshio
 *
 */
@Service
public class FinGettingStat implements FinGettingEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = FinGettingStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = FinGettingStat.class.getName();

	/** FutureDBService部品 */
	@Autowired
	private FutureDBService futureDBService;

	/** DataDBService部品 */
	@Autowired
	private DataDBService dataDBService;

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
	 * @throws Exception
	 */
	@Override
	@Transactional
	public void finGettingStat(Map<String, List<DataEntity>> entities) throws Exception {
		final String METHOD_NAME = "finGettingStat";
		// ログ出力
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		List<String> insertPath = new ArrayList<String>();
		// FinGettingStatを登録する
		for (Map.Entry<String, List<DataEntity>> map : entities.entrySet()) {
			String filePath = map.getKey();
			String fillChar = "ファイル名: " + filePath;
			// リアルタイムデータ
			List<DataEntity> entList = map.getValue();
			for (DataEntity ent : entList) {
				insertPath.add(filePath);
				// 終了済が入っていないデータはスキップ
				if (ent.getTimes() == null || "".equals(ent.getTimes())) continue;
				try {
					DataEntity insertEntities = this.dataDBService.selectInBatch(ent);
					int result = this.dataDBService.insertInBatch(insertEntities);
					if (result == 9) {
						String messageCd = "新規登録エラー";
						throw new Exception(messageCd);
					}
				} catch (Exception e) {
					String messageCd = "システムエラー";
					throw new Exception(messageCd, e);
				}
			}

			// 未来データに詰め直す
			for (DataEntity ent : entList) {
				List<FutureEntity> insertEntity = new ArrayList<FutureEntity>();
				FutureEntity entity = new FutureEntity();
				entity.setGameTeamCategory(ent.getDataCategory());
				entity.setFutureTime(DateUtil.
						minus90Minutes(ent.getRecordTime())); // 90分前の日付
				entity.setHomeRank(ent.getHomeRank());
				entity.setAwayRank(ent.getAwayRank());
				entity.setHomeTeamName(ent.getHomeTeamName());
				entity.setAwayTeamName(ent.getAwayTeamName());
				entity.setHomeMaxGettingScorer(ent.getHomeMaxGettingScorer());
				entity.setAwayMaxGettingScorer(ent.getAwayMaxGettingScorer());
				entity.setHomeTeamHomeScore(ent.getHomeTeamHomeScore());
				entity.setAwayTeamHomeScore(ent.getAwayTeamHomeScore());
				entity.setHomeTeamHomeLost(ent.getHomeTeamHomeLost());
				entity.setAwayTeamHomeLost(ent.getAwayTeamHomeLost());
				entity.setHomeTeamAwayScore(ent.getHomeTeamAwayScore());
				entity.setAwayTeamAwayScore(ent.getAwayTeamAwayScore());
				entity.setHomeTeamAwayLost(ent.getHomeTeamAwayLost());
				entity.setAwayTeamAwayLost(ent.getAwayTeamAwayLost());
				entity.setGameLink(ent.getGameLink());
				entity.setDataTime(DateUtil.getSysDate()); // 登録日付
				entity.setStartFlg("1"); // 開始済
				insertEntity.add(entity);
				try {
					List<FutureEntity> insertEntities = this.futureDBService.selectInBatch(insertEntity, fillChar);
					int result = this.futureDBService.insertInBatch(insertEntities);
					if (result == 9) {
						String messageCd = "新規登録エラー";
						throw new Exception(messageCd);
					}
				} catch (Exception e) {
					String messageCd = "システムエラー";
					throw new Exception(messageCd, e);
				}
			}
		}

		// 途中で例外が起きなければmatchKey限定のファイルを削除する
		String bucket = config.getS3BucketsOutputs(); // バケット名取得
		FileDeleteUtil.deleteS3Files(
				insertPath,
				bucket,
				s3Operator,
				manageLoggerComponent,
				PROJECT_NAME,
				CLASS_NAME,
				METHOD_NAME,
				"FUTURE");

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
	}

}
