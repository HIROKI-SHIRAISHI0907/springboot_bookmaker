package dev.batch.bm_b003;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import dev.batch.repository.master.CountryLeagueSeasonMasterRepository;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M029シーズンデータDB管理部品
 * @author shiraishitoshio
 *
 */
@Component
public class CountryLeagueSeasonDBService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CountryLeagueSeasonDBService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CountryLeagueSeasonDBService.class.getSimpleName();

	/** 有効フラグ */
	private static final String VALID_FLG_0 = "0";

	/** CountryLeagueSeasonMasterRepositoryレポジトリクラス */
	@Autowired
	private CountryLeagueSeasonMasterRepository countryLeagueSeasonMasterRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * チェックメソッド
	 * @param chkEntities
	 */
	public List<CountryLeagueSeasonMasterEntity> selectInBatch(List<CountryLeagueSeasonMasterEntity> chkEntities
			) {
		final String METHOD_NAME = "selectInBatch";
		List<CountryLeagueSeasonMasterEntity> entities = new ArrayList<CountryLeagueSeasonMasterEntity>();
		for (CountryLeagueSeasonMasterEntity entity : chkEntities) {
			try {
				int count = this.countryLeagueSeasonMasterRepository.findDataCount(entity);
				if (count == 0) {
					entities.add(entity);
				}
			} catch (Exception e) {
				String messageCd = "DB接続エラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
				throw e;
			}
		}
		return entities;
	}

	/**
	 * 登録メソッド
	 * @param insertEntities
	 */
	public int insertInBatch(List<CountryLeagueSeasonMasterEntity> insertEntities) {
		final String METHOD_NAME = "insertInBatch";
		final int BATCH_SIZE = 100;
		for (int i = 0; i < insertEntities.size(); i += BATCH_SIZE) {
			int end = Math.min(i + BATCH_SIZE, insertEntities.size());
			List<CountryLeagueSeasonMasterEntity> batch = insertEntities.subList(i, end);
			for (CountryLeagueSeasonMasterEntity entity : batch) {
				try {
					// 有効フラグを埋める
					entity.setValidFlg(VALID_FLG_0);
					int result = this.countryLeagueSeasonMasterRepository.insert(entity);
					if (result != 1) {
						String messageCd = "新規登録エラー";
						this.manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
						return 9;
					}
				} catch (DuplicateKeyException e) {
					String messageCd = "登録済みです";
					this.manageLoggerComponent.debugWarnLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd);
					// 重複は特に例外として出さない
					continue;
				} catch (Exception e) {
					String messageCd = "システムエラー";
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e);
					return 9;
				}
			}
		}
		String messageCd = "BM_M028 登録件数: " + insertEntities.size();
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd);
		return 0;
	}

}
