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
 * <p>2026-10 修正</p>
 * <ul>
 *   <li>data_category の決定に match_id・記録時刻を渡す（5 引数版）。
 *       以前は 3 引数版で「現在時刻の前後 6 時間」の行しか見ておらず、前日のライブ行（ラウンド付き）を拾えなかった。
 *       その結果「日本: J3 リーグ」（ラウンドなし）や CSV そのままの「J3 リーグ」が登録されていた。</li>
 *   <li>「終了済が登録済みか」の判定を同じ試合（match_id）単位に変更（findFinCountForMatch）。
 *       以前は同じカードの全期間で見ていたため、翌シーズン以降の同じカードの終了済が登録されなかった。</li>
 * </ul>
 *
 * <p>2026-10 修正（ヘッダしかない CSV が S3 に残り続ける問題）</p>
 * <ul>
 *   <li>GetOriginFinInfo はヘッダのみのファイルを「キー → 空のリスト」で返す。
 *       そのファイルは登録・バックアップせず、DBコミット後に S3 から直接削除する（directDeletePath）。</li>
 *   <li>JSON に出すデータが 0 件のとき（全ファイルがヘッダのみ・登録済みスキップのみ）は、JSON の作成・アップロードを行わない。</li>
 *   <li>DB登録したファイルが 0 件のときは、バックアップを呼ばない。</li>
 * </ul>
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
		 * ヘッダのみ（登録対象 0 件）のファイル数
		 */
		int emptyFiles = 0;

		/*
		 * 1) bm/master両方のDB処理
		 *
		 * この処理はトランザクション内で実施する。
		 */
		for (Map.Entry<String, List<DataEntity>> map : entities.entrySet()) {

			String filePath = map.getKey();
			List<DataEntity> entList = map.getValue();

			/*
			 * ヘッダのみ（GetOriginFinInfo が空のリストで返す）:
			 * 登録・バックアップはせず、DBコミット後に直接S3から削除する。
			 */
			if (entList == null || entList.isEmpty()) {

				emptyFiles++;

				directDeletePath.add(filePath);

				manageLoggerComponent.debugInfoLog(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						null,
						"登録対象0件（ヘッダのみ）のため、登録せず削除対象にします。"
								+ " filePath=" + filePath);

				continue;
			}

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
				 * 終了済かつ、同じ試合（match_id。無ければ同じカードで記録時刻の前後1日）で
				 * 既に終了済データが登録済みの場合は、重複とみなしてスキップする。
				 */
				if (BookMakersCommonConst.FIN.equals(ent.getTimes())
						&& bookDataRepository.findFinCountForMatch(ent) > 0) {

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
					 */
					directDeletePath.add(filePath);

					continue;
				}

				/*
				 * DB登録対象となったCSVを保持する。
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
				 * match_id と記録時刻を渡し、同じ試合のライブ行（ラウンド付き）を優先して使う。
				 */
				String csvCategory = ent.getDataCategory();
				String dataCategory = dataCategoryBatchService.create(
						ent.getHomeTeamName(),
						ent.getAwayTeamName(),
						csvCategory,
						ent.getMatchId(),
						ent.getRecordTime());

				ent.setDataCategory(dataCategory);

				manageLoggerComponent.debugInfoLog(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						null,
						"data_category 決定: matchId=" + ent.getMatchId()
								+ ", csv=[" + csvCategory + "]"
								+ " -> [" + dataCategory + "]");

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

		if (emptyFiles > 0) {

			manageLoggerComponent.debugInfoLog(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					"登録対象0件（ヘッダのみ）のファイル: " + emptyFiles + "件（登録せず削除します）");
		}

		/*
		 * 2) 取得済み終了データ保存
		 *
		 * 今回分が 0 件（全ファイルがヘッダのみ等）のときは、JSON の更新は不要なので行わない。
		 */
		if (!mapList.isEmpty()) {

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
		}

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
						 * 3-1) / 3-2) はDB登録したCSVがある場合のみ
						 *      （ヘッダのみ・登録済みスキップだけの場合はバックアップ対象なし）
						 */
						if (!insertPath.isEmpty()) {

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
						}

						/*
						 * 3-3) 終了済みデータが既に登録済みでDB登録をスキップしたCSV、
						 *      およびヘッダのみ（登録対象 0 件）のCSVを直接削除する。
						 */
						if (!directDeletePath.isEmpty()) {

							manageLoggerComponent.debugInfoLog(
									PROJECT_NAME,
									CLASS_NAME,
									METHOD_NAME,
									null,
									"終了済みデータが登録済み、またはヘッダのみのため、"
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
	 * 指定のパスへ {@code b010_fin_getting_data_list.json} を作成する。
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
	 * S3上に既存のjsonがあれば読み込み、今回分のmapとマージ（追記）する。
	 * 同じキー（dataCategory）が既に存在する場合は、今回分の値で上書きする。
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

			return newMap;
		}
	}
}