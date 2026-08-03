package dev.batch.bm_b007;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import dev.batch.repository.master.AllLeagueMasterBatchRepository;
import dev.batch.repository.master.InitialMasterCsvRepository;
import dev.common.constant.MasterNameConstant;
import dev.common.constant.MessageCdConst;
import dev.common.entity.AllLeagueMasterEntity;
import dev.common.entity.InitialReadingMasterCsvEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * AllLeagueDBService管理部品
 * @author shiraishitoshio
 *
 */
@Component
public class AllLeagueDBService {

	/** バッチサイズ */
	private static final int BATCH_SIZE = 100;

	/** プロジェクト名 */
	private static final String PROJECT_NAME = AllLeagueDBService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = AllLeagueDBService.class.getName();

	/** BM_BATCH_NUMBER */
	private static final String BM_NUMBER = "BM_B007";

	/** AllLeagueMasterBatchRepositoryレポジトリクラス */
	@Autowired
	private AllLeagueMasterBatchRepository allLeagueMasterBatchRepository;

	@Autowired
	private InitialMasterCsvRepository initialMasterCsvRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * insert対象を返す
	 * @param entities 入力一覧
	 * @return insert対象一覧
	 */
	@Transactional(readOnly = true)
	public List<AllLeagueMasterEntity> selectInBatch(List<AllLeagueMasterEntity> entities) {
		final String METHOD_NAME = "selectInBatch";

		List<AllLeagueMasterEntity> insertEntities = new ArrayList<>();

		if (entities == null || entities.isEmpty()) {
			return insertEntities;
		}

		Set<String> processedKeys = new HashSet<>();

		try {
			for (AllLeagueMasterEntity entity : entities) {
				if (entity == null) {
					continue;
				}

				normalizeEntity(entity);

				String country = entity.getCountry();
				String league = entity.getLeague();

				if (!hasMeaningfulValue(country) || !hasMeaningfulValue(league)) {
					continue;
				}

				String key = country + "___" + league;
				if (!processedKeys.add(key)) {
					continue;
				}

				AllLeagueMasterEntity master = this.allLeagueMasterBatchRepository
						.findByCountryLeague(country, league);

				if (master == null) {
					insertEntities.add(entity);
				}
			}

		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, "DB接続エラー");
			throw e;
		}

		return insertEntities;
	}

	/**
	 * 互換用単体版
	 */
	@Transactional(readOnly = true)
	public AllLeagueMasterEntity selectInBatch(AllLeagueMasterEntity chkEntities) {
		if (chkEntities == null) {
			return null;
		}
		List<AllLeagueMasterEntity> list = new ArrayList<>();
		list.add(chkEntities);

		List<AllLeagueMasterEntity> result = this.selectInBatch(list);
		return result.isEmpty() ? null : result.get(0);
	}

	/**
	 * 登録メソッド
	 * 1件ずつ独立トランザクションで保存し、失敗行はスキップする
	 * @param entities 登録対象一覧
	 * @return 0:正常 / 9:異常
	 */
	public int insertInBatch(List<AllLeagueMasterEntity> entities) {
		final String METHOD_NAME = "insertInBatch";

		if (entities == null || entities.isEmpty()) {
			return 0;
		}

		int successCount = 0;
		int skipCount = 0;

		try {
			for (int i = 0; i < entities.size(); i += BATCH_SIZE) {
				int end = Math.min(i + BATCH_SIZE, entities.size());
				List<AllLeagueMasterEntity> batch = entities.subList(i, end);

				for (AllLeagueMasterEntity entity : batch) {
					if (entity == null) {
						skipCount++;
						continue;
					}

					try {
						normalizeEntity(entity);

						if (!hasMeaningfulValue(entity.getCountry()) || !hasMeaningfulValue(entity.getLeague())) {
							skipCount++;
							continue;
						}

						executeInNewTransaction(() -> {
							int result = this.allLeagueMasterBatchRepository.insert(entity);
							if (result != 1) {
								throw new IllegalStateException("insert result != 1");
							}
							this.upsertInitialReadingTarget(entity);
						});

						successCount++;

					} catch (DuplicateKeyException e) {
						try {
							executeInNewTransaction(() -> this.upsertInitialReadingTarget(entity));
						} catch (Exception ex) {
							skipCount++;
							String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
							this.manageLoggerComponent.debugErrorLog(
									PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, ex,
									BM_NUMBER + " initial_reading_master_csv update failed. country="
											+ safe(entity.getCountry()) + ", league=" + safe(entity.getLeague()));
							continue;
						}

						String messageCd = MessageCdConst.MCD00002W_DUPLICATION_WARNING;
						this.manageLoggerComponent.debugWarnLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd);

					} catch (Exception e) {
						skipCount++;
						String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
						this.manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
								BM_NUMBER + " skip insert error. country="
										+ safe(entity.getCountry()) + ", league=" + safe(entity.getLeague()));
					}
				}
			}

			String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					BM_NUMBER + " 登録成功件数: " + successCount + "件, スキップ件数: " + skipCount + "件");

			return 0;

		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e);
			return 9;
		}
	}

	/**
	 * 互換用単体版
	 */
	public int insertInBatch(AllLeagueMasterEntity insertEntity) {
		if (insertEntity == null) {
			return 0;
		}
		List<AllLeagueMasterEntity> list = new ArrayList<>();
		list.add(insertEntity);
		return this.insertInBatch(list);
	}

	/**
	 * CSVに存在した country + league をモーダル表示対象に戻す
	 *
	 * @param entities 入力一覧
	 * @return 0:正常 / 9:異常
	 */
	public int resetInitialFlgByIncomingTargets(List<AllLeagueMasterEntity> entities) {
		final String METHOD_NAME = "resetInitialFlgByIncomingTargets";

		if (entities == null || entities.isEmpty()) {
			return 0;
		}

		Set<String> processedKeys = new HashSet<>();

		try {
			for (AllLeagueMasterEntity entity : entities) {
				if (entity == null) {
					continue;
				}

				normalizeEntity(entity);

				String country = entity.getCountry();
				String league = entity.getLeague();

				if (!hasMeaningfulValue(country) || !hasMeaningfulValue(league)) {
					continue;
				}

				String key = country + "___" + league;
				if (!processedKeys.add(key)) {
					continue;
				}

				int count = initialMasterCsvRepository.findCount(
						MasterNameConstant.ALL_LEAGUE_SCRAPE_MASTER,
						country,
						league);

				if (count <= 0) {
					InitialReadingMasterCsvEntity initialEntity = new InitialReadingMasterCsvEntity();
					initialEntity.setMasterName(MasterNameConstant.ALL_LEAGUE_SCRAPE_MASTER);
					initialEntity.setCountry(country);
					initialEntity.setLeague(league);
					initialEntity.setInitialFlg("0");

					int result = initialMasterCsvRepository.insert(initialEntity);
					if (result != 1) {
						return 9;
					}
				} else {
					initialMasterCsvRepository.updateInitialFlg(
							MasterNameConstant.ALL_LEAGUE_SCRAPE_MASTER,
							country,
							league,
							"0");
				}
			}

			return 0;

		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e);
			return 9;
		}
	}

	/**
	 * 互換用単体版
	 */
	public int resetInitialFlgByIncomingTarget(AllLeagueMasterEntity entity) {
		if (entity == null) {
			return 0;
		}
		List<AllLeagueMasterEntity> list = new ArrayList<>();
		list.add(entity);
		return this.resetInitialFlgByIncomingTargets(list);
	}

	/**
	 * initial_reading_master_csv を insert/update
	 */
	private void upsertInitialReadingTarget(AllLeagueMasterEntity entity) {
		if (entity == null) {
			return;
		}

		String country = trim(entity.getCountry());
		String league = trim(entity.getLeague());

		if (!hasMeaningfulValue(country) || !hasMeaningfulValue(league)) {
			return;
		}

		int count = initialMasterCsvRepository.findCount(
				MasterNameConstant.ALL_LEAGUE_SCRAPE_MASTER,
				country,
				league);

		if (count <= 0) {
			InitialReadingMasterCsvEntity initialEntity = new InitialReadingMasterCsvEntity();
			initialEntity.setMasterName(MasterNameConstant.ALL_LEAGUE_SCRAPE_MASTER);
			initialEntity.setCountry(country);
			initialEntity.setLeague(league);
			initialEntity.setInitialFlg("0");
			initialMasterCsvRepository.insert(initialEntity);
		} else {
			initialMasterCsvRepository.updateInitialFlg(
					MasterNameConstant.ALL_LEAGUE_SCRAPE_MASTER,
					country,
					league,
					"0");
		}
	}

	/**
	 * 1件単位の独立トランザクション実行
	 */
	private void executeInNewTransaction(ThrowingRunnable action) {
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		tx.executeWithoutResult(status -> {
			try {
				action.run();
			} catch (RuntimeException e) {
				status.setRollbackOnly();
				throw e;
			} catch (Exception e) {
				status.setRollbackOnly();
				throw new RuntimeException(e);
			}
		});
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private void normalizeEntity(AllLeagueMasterEntity entity) {
		if (entity == null) {
			return;
		}
		entity.setCountry(trim(entity.getCountry()));
		entity.setLeague(trim(entity.getLeague()));
	}

	private boolean hasMeaningfulValue(String value) {
		if (value == null) {
			return false;
		}
		String v = value.trim();
		if (v.isEmpty()) {
			return false;
		}
		return !("N/A".equalsIgnoreCase(v)
				|| "-".equals(v)
				|| "null".equalsIgnoreCase(v)
				|| "未定".equals(v));
	}

	private String trim(String value) {
		return value == null ? null : value.trim();
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}
}
