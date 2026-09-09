package dev.batch.bm_b010;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.batch.interf.FinGettingEntityIF;
import dev.batch.repository.bm.BookDataRepository;
import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.DataEntity;
import dev.common.getinfo.PutOriginBackUpInfo;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.common.util.FileDeleteUtil;

/**
 * FinGettingStat登録ロジック
 *
 * @author shiraishitoshio
 *
 */
@Service
public class FinGettingStat implements FinGettingEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = FinGettingStat.class
			.getProtectionDomain().getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = FinGettingStat.class.getName();

	/** JSON生成に利用するObjectMapper */
	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private SeqKeyBatchService seqKeyService;

	@Autowired
	private DataCategoryBatchService dataCategoryBatchService;

	@Autowired
	private DataDBService dataDBService;

	@Autowired
	private BookDataRepository bookDataRepository;

	@Autowired
	private PathConfig config;

	@Autowired
	private S3Operator s3Operator;

	@Autowired
	private PutOriginBackUpInfo putOriginBackUpInfo;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void finGettingStat(Map<String, List<DataEntity>> entities) throws Exception {

		final String METHOD_NAME = "finGettingStat";

		manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME,
				CLASS_NAME,
				METHOD_NAME);

		/*
		 * DB登録を行ったCSVのパス
		 *
		 * DBコミット後、
		 * バックアップ成功したCSVのみS3から削除する。
		 */
		List<String> insertPath = new ArrayList<>();

		/*
		 * 終了済みデータが既に登録済みのため、
		 * DB登録をスキップしたCSVのパス
		 *
		 * バックアップは行わず、
		 * DBコミット後に直接S3から削除する。
		 */
		Set<String> directDeletePath = new HashSet<>();

		/*
		 * JSONに出力するデータ
		 */
		Map<String, String> mapList = new HashMap<>();

		/*
		 * 1) bm/master両方のDB処理
		 *
		 * この処理はトランザクション内で実施する。
		 */
		for (Map.Entry<String, List<DataEntity>> map : entities.entrySet()) {

			String filePath = map.getKey();
			List<DataEntity> entList = map.getValue();

			for (DataEntity ent : entList) {

				String teamNames = ent.getHomeTeamName()
						+ "-"
						+ ent.getAwayTeamName();

				mapList.put(
						ent.getDataCategory(),
						teamNames);

				if (ent.getTimes() == null
						|| ent.getTimes().isEmpty()) {

					// 終了済が未設定なら手動設定
					ent.setTimes(BookMakersCommonConst.FIN);
				}

				/*
				 * 終了済かつ、同一home/awayチーム名の組み合わせで
				 * 既に終了済データが登録済みの場合は、
				 * 同じ試合(match_id)の重複スナップショットとみなしてスキップする。
				 */
				if (BookMakersCommonConst.FIN.equals(ent.getTimes())
						&& bookDataRepository.findFinCount(ent) > 0) {

					manageLoggerComponent.debugInfoLog(
							PROJECT_NAME,
							CLASS_NAME,
							METHOD_NAME,
							null,
							"終了済データが既に登録済みのためスキップします。"
									+ " home=" + ent.getHomeTeamName()
									+ ", away=" + ent.getAwayTeamName()
									+ ", matchId=" + ent.getMatchId()
									+ ", filePath=" + filePath);

					/*
					 * DB登録・バックアップは行わず、
					 * DBコミット後に直接S3から削除する。
					 *
					 * Setなので、同じCSVに複数のDataEntityが存在しても
					 * filePathは1件だけ保持される。
					 */
					directDeletePath.add(filePath);

					continue;
				}

				/*
				 * DB登録対象となったCSVを保持する。
				 *
				 * 同じCSVに複数のDataEntityが存在する場合は
				 * 重複して登録される可能性があるが、
				 * 後続のバックアップ処理側で扱えるため
				 * ここではListのままとする。
				 */
				insertPath.add(filePath);

				/*
				 * 通番を発番
				 */
				ent.setSeqKey(
						seqKeyService.create(
								ent.getHomeTeamName(),
								ent.getAwayTeamName(),
								ent.getMatchId()));

				/*
				 * データカテゴリの再設定
				 */
				String dataCategory = dataCategoryBatchService.create(
						ent.getHomeTeamName(),
						ent.getAwayTeamName(),
						ent.getDataCategory());

				ent.setDataCategory(dataCategory);

				/*
				 * 手動フラグを設定
				 */
				ent.setAddManualFlg("1");

				/*
				 * DB登録
				 */
				DataEntity insertEntities = dataDBService.selectInBatch(ent);

				dataDBService.insertInBatchOrThrow(insertEntities);
			}
		}

		/*
		 * 2) 取得済み終了データ保存
		 */
		String outputBucket = config.getS3BucketsOutputsFin();

		final String jsonFolder = config.getB008JsonFolder();

		final String jsonPath = jsonFolder + "b010_fin_getting_data_list.json";

		final Path jsonFilePath = Paths.get(jsonPath);

		final String s3Key = "list/" + jsonFilePath.getFileName().toString();

		/*
		 * 既存のS3上のJSONがあれば読み込んで
		 * 今回分とマージする。
		 */
		Map<String, String> mergedMap = mergeWithExisting(
				outputBucket,
				s3Key,
				mapList);

		/*
		 * JSONファイル作成
		 */
		Files.createDirectories(
				jsonFilePath.getParent());

		makeJson(
				jsonPath,
				mergedMap);

		/*
		 * JSONをS3へアップロード
		 */
		upload(
				outputBucket,
				s3Key,
				jsonFilePath);

		/*
		 * 3) DBコミット後の処理
		 *
		 * DBコミットが成功した場合のみ実行する。
		 */
		String bucket = config.getS3BucketsOutputsFin();

		TransactionSynchronizationManager.registerSynchronization(
				new TransactionSynchronization() {

					@Override
					public void afterCommit() {

						/*
						 * 3-1) DB登録したCSVをバックアップする。
						 */
						PutOriginBackUpInfo.BackupResult backupResult = putOriginBackUpInfo.backup(
								entities,
								insertPath);

						/*
						 * バックアップに失敗したCSVがある場合は、
						 * バックアップ対象についてはS3削除を行わない。
						 */
						if (!backupResult.getFailedLocalPaths().isEmpty()) {

							manageLoggerComponent.debugErrorLog(
									PROJECT_NAME,
									CLASS_NAME,
									METHOD_NAME,
									MessageCdConst.MCD00003E_EXECUTION_SKIP,
									null,
									String.format(
											"バックアップ格納に失敗したCSVがあるため、"
													+ "バックアップ対象CSVのS3削除を中止します。"
													+ " backupSucceeded=%d,"
													+ " backupFailed=%d,"
													+ " backupFailedPaths=%s",
											backupResult
													.getSucceededLocalPaths()
													.size(),
											backupResult
													.getFailedLocalPaths()
													.size(),
											backupResult
													.getFailedLocalPaths()));
						}

						/*
						 * 3-2) バックアップ成功したCSVのみ削除する。
						 */
						if (!backupResult
								.getSucceededLocalPaths()
								.isEmpty()) {

							FileDeleteUtil.deleteS3Files(
									backupResult
											.getSucceededLocalPaths(),
									bucket,
									s3Operator,
									manageLoggerComponent,
									PROJECT_NAME,
									CLASS_NAME,
									METHOD_NAME,
									"OUTPUTS_FIN_STATS");
						}

						/*
						 * 3-3) 終了済みデータが既に登録済みで
						 *      DB登録をスキップしたCSVを直接削除する。
						 *
						 *      このCSVはバックアップしない。
						 */
						if (!directDeletePath.isEmpty()) {

							manageLoggerComponent.debugInfoLog(
									PROJECT_NAME,
									CLASS_NAME,
									METHOD_NAME,
									null,
									"終了済みデータが既に登録済みのため、"
											+ "バックアップせずS3から直接削除します。"
											+ " filePaths=" + directDeletePath);

							FileDeleteUtil.deleteS3Files(
									new ArrayList<>(directDeletePath),
									bucket,
									s3Operator,
									manageLoggerComponent,
									PROJECT_NAME,
									CLASS_NAME,
									METHOD_NAME,
									"OUTPUTS_FIN_STATS");
						}
					}
				});

		manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME,
				CLASS_NAME,
				METHOD_NAME);
	}

	/**
	 * 指定のパスへ
	 * {@code b010_fin_getting_data_list.json} を作成する。
	 *
	 * <p>
	 * 出力形式はpretty printとし、
	 * Python側が読みやすい形式で保存する。
	 * </p>
	 *
	 * @param jsonPath
	 *            作成先JSONパス（ファイルパス）
	 * @param countryLeagueMap
	 *            国をキー、リーグ集合を値とするマップ
	 * @throws StreamWriteException
	 *             JSON書き込みに失敗した場合
	 * @throws DatabindException
	 *             変換に失敗した場合
	 * @throws IOException
	 *             ファイルI/Oで失敗した場合
	 */
	private void makeJson(
			String jsonPath,
			Map<String, String> countryLeagueMap)
			throws StreamWriteException, DatabindException, IOException {

		this.objectMapper
				.writerWithDefaultPrettyPrinter()
				.writeValue(
						new File(jsonPath),
						countryLeagueMap);
	}

	/**
	 * upload
	 */
	private void upload(
			String bucket,
			String key,
			Path file) {

		final String METHOD_NAME = "upload";

		try {

			s3Operator.uploadFile(
					bucket,
					key,
					file);

		} catch (Exception e) {

			String messageCd = MessageCdConst.MCD00023E_S3_UPLOAD_FAILED;

			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					e,
					"bucket: " + bucket
							+ ", key: " + key
							+ ", file: " + file);
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME,
				CLASS_NAME,
				METHOD_NAME,
				null,
				"bucket: " + bucket
						+ ", key: " + key
						+ ", file: " + file);
	}

	/**
	 * S3上に既存のjsonがあれば読み込み、
	 * 今回分のmapとマージ（追記）する。
	 *
	 * 同じキー（dataCategory）が既に存在する場合は、
	 * 今回分の値で上書きする。
	 *
	 * @param bucket
	 *            バケット名
	 * @param key
	 *            S3キー
	 *            （例: list/b010_fin_getting_data_list.json）
	 * @param newMap
	 *            今回分のマップ
	 * @return マージ後のマップ
	 *         （既存が無ければnewMapをそのまま返す）
	 */
	private Map<String, String> mergeWithExisting(
			String bucket,
			String key,
			Map<String, String> newMap) {

		final String METHOD_NAME = "mergeWithExisting";

		List<String> existingKeys = s3Operator.listKeys(
				bucket,
				key);

		if (existingKeys == null
				|| !existingKeys.contains(key)) {

			/*
			 * 既存が無ければ今回分のみ
			 */
			return newMap;
		}

		try {

			String existingJson = s3Operator.downloadTextUtf8(
					bucket,
					key);

			Map<String, String> existingMap = objectMapper.readValue(
					existingJson,
					objectMapper
							.getTypeFactory()
							.constructMapType(
									Map.class,
									String.class,
									String.class));

			/*
			 * 既存データに今回分を追記。
			 * 同じキーは今回分で上書きする。
			 */
			existingMap.putAll(newMap);

			return existingMap;

		} catch (Exception e) {

			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION,
					e,
					"bucket: " + bucket
							+ ", key: " + key);

			/*
			 * 既存分の読み込みに失敗した場合は
			 * 今回分のみで続行する。
			 */
			return newMap;
		}
	}
}