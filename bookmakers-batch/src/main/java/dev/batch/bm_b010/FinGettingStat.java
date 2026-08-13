package dev.batch.bm_b010;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.batch.bm_b005.FutureDBService;
import dev.batch.interf.FinGettingEntityIF;
import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.DataEntity;
import dev.common.entity.FutureEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.common.util.DateStatHelper;
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

	/** JSON 生成に利用する ObjectMapper。 */
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private SeqKeyBatchService seqKeyService;
	@Autowired
	private DataCategoryBatchService dataCategoryBatchService;
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
		Map<String, String> mapList = new HashMap<>();

		// 1) bm/master両方のDB処理（ここが “同一JTAトランザクション” に参加）
		for (Map.Entry<String, List<DataEntity>> map : entities.entrySet()) {
			String filePath = map.getKey();
			String fillChar = "ファイル名: " + filePath;

			List<DataEntity> entList = map.getValue();
			for (DataEntity ent : entList) {
				insertPath.add(filePath);
				// 通番を発番
				ent.setSeqKey(seqKeyService.create(ent.getHomeTeamName(),
						ent.getAwayTeamName(), ent.getMatchId()));
				// データカテゴリの再設定
				ent.setDataCategory(dataCategoryBatchService.create(ent.getHomeTeamName(),
						ent.getAwayTeamName(), ent.getDataCategory()));
				if (ent.getTimes() == null || ent.getTimes().isEmpty()) {
					// 終了済が未設定なら手動設定
					ent.setTimes(BookMakersCommonConst.FIN);
				}

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

				String teamNames = ent.getHomeTeamName() + "-" + ent.getAwayTeamName();
			    mapList.put(ent.getDataCategory(), teamNames);
			}

		}

		// 2) 取得済み終了データ保存
		String outputBucket = config.getS3BucketsOutputsFin();

		final String jsonFolder = config.getB008JsonFolder(); // /tmp/json/
		final String jsonPath = jsonFolder + "b010_fin_getting_data_list.json";
		final Path jsonFilePath = Paths.get(jsonPath);
		final String s3Key = "list/" + jsonFilePath.getFileName().toString();

		// リクエストで受け取ったデータをObjectMapperで変換
		Files.createDirectories(jsonFilePath.getParent());
		makeJson(jsonPath, mapList);

		// upload
		upload(outputBucket, s3Key, jsonFilePath);

		// 3) afterCommitでS3削除（＝DBコミット成功後だけ消す）
		String bucket = config.getS3BucketsOutputsFin();
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
								"OUTPUTS_FIN_STATS");
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
		entity.setFutureTime(resolveFutureTime(ent));
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

	/**
	 * futureTime の決定
	 *
	 * 優先順位:
	 * 1. 当時の試合時間があればその値を使用
	 *    - 例: "14.07.2026 07:00"
	 *    - DB投入用に "yyyy-MM-dd HH:mm:ss" 形式へ正規化
	 * 2. 無ければ従来どおり recordTime の120分前を使用
	 */
	private String resolveFutureTime(DataEntity ent) {
		// なければ当時の時間を取得
		String originalMatchTime = ent.getAtThatTimes();

		if (originalMatchTime != null && !originalMatchTime.isBlank()) {
			String normalized = DateStatHelper.toDateTimeText(originalMatchTime);
			if (normalized != null && !normalized.isBlank()) {
				return normalized;
			}
		}
		// 記録時間
		return DateUtil.minus120Minutes(ent.getRecordTime());
	}

	/**
	 * 指定のパスへ {@code b010_fin_getting_data_list.json} を作成する。
	 *
	 * <p>
	 * 出力形式は pretty print とし、Python 側が読みやすい形式で保存する。
	 * </p>
	 *
	 * @param jsonPath 作成先JSONパス（ファイルパス）
	 * @param countryLeagueMap 国をキー、リーグ集合を値とするマップ
	 * @throws StreamWriteException JSON書き込みに失敗した場合
	 * @throws DatabindException    変換に失敗した場合
	 * @throws IOException          ファイルI/Oで失敗した場合
	 */
	private void makeJson(String jsonPath, Map<String, String> countryLeagueMap)
			throws StreamWriteException, DatabindException, IOException {
		this.objectMapper.writerWithDefaultPrettyPrinter()
				.writeValue(new File(jsonPath), countryLeagueMap);
	}

	/** upload */
	private void upload(String bucket, String key, Path file) {
		final String METHOD_NAME = "upload";
		try {
			s3Operator.uploadFile(bucket, key, file);
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00023E_S3_UPLOAD_FAILED;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
					"bucket: " + bucket + ", key: " + key + ", file: " + file);
		}

		this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, null,
				"bucket: " + bucket + ", key: " + key + ", file: " + file);
	}

}