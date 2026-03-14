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

	@Autowired
	private FutureDBService futureDBService; // master
	@Autowired
	private DataDBService dataDBService; // bm
	@Autowired
	private PathConfig config;
	@Autowired
	private S3Operator s3Operator;
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void finGettingStat(Map<String, List<DataEntity>> entities) throws Exception {
		final String METHOD_NAME = "finGettingStat";
		manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		List<String> insertPath = new ArrayList<>();

		// 1) bm/master両方のDB処理（ここが “同一JTAトランザクション” に参加）
		for (Map.Entry<String, List<DataEntity>> map : entities.entrySet()) {
			String filePath = map.getKey();
			String fillChar = "ファイル名: " + filePath;

			List<DataEntity> entList = map.getValue();
			for (DataEntity ent : entList) {
				insertPath.add(filePath);
				if (ent.getTimes() == null || ent.getTimes().isEmpty())
					continue;
				// 手動フラグを設定
				ent.setAddManualFlg("1");

				DataEntity insertEntities = dataDBService.selectInBatch(ent);
				dataDBService.insertInBatchOrThrow(insertEntities);
			}

			for (DataEntity ent : entList) {
				FutureEntity fe = buildFutureEntity(ent);
				List<FutureEntity> list = List.of(fe);

				List<FutureEntity> insertEntities = futureDBService.selectInBatch(list, fillChar);
				futureDBService.insertInBatchOrThrow(insertEntities);
			}
		}

		// 2) afterCommitでS3削除（＝DBコミット成功後だけ消す）
		String bucket = config.getS3BucketsOutputs();
		org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
				new org.springframework.transaction.support.TransactionSynchronization() {
					@Override
					public void afterCommit() {
						FileDeleteUtil.deleteS3Files(
								insertPath,
								bucket,
								s3Operator,
								manageLoggerComponent,
								PROJECT_NAME,
								CLASS_NAME,
								METHOD_NAME,
								"OUTPUTS_STATS");
					}
				});

		manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
	}

	/**
	 * エンティティ設定
	 * @param ent
	 * @return
	 */
	private FutureEntity buildFutureEntity(DataEntity ent) {
		FutureEntity entity = new FutureEntity();
		entity.setGameTeamCategory(ent.getDataCategory());
		entity.setFutureTime(DateUtil.minus90Minutes(ent.getRecordTime())); // 90分前の日付
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
		return entity;
	}

}