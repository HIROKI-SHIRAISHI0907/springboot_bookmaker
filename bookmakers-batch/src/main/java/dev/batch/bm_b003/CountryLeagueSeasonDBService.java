package dev.batch.bm_b003;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import dev.batch.repository.master.CountryLeagueSeasonMasterBatchRepository;
import dev.common.constant.MessageCdConst;
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

	/** BM_BATCH_NUMBER */
	private static final String BM_NUMBER = "BM_B003";

	/** CountryLeagueSeasonMasterRepositoryレポジトリクラス */
	@Autowired
	private CountryLeagueSeasonMasterBatchRepository countryLeagueSeasonMasterRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * チェックメソッド
	 * @param chkEntities
	 */
	public List<CountryLeagueSeasonMasterEntity> selectInBatch(List<CountryLeagueSeasonMasterEntity> chkEntities) {
		final String METHOD_NAME = "selectInBatch";
		List<CountryLeagueSeasonMasterEntity> entities = new ArrayList<CountryLeagueSeasonMasterEntity>();
		for (CountryLeagueSeasonMasterEntity entity : chkEntities) {
			try {
				int count = this.countryLeagueSeasonMasterRepository.findDataCount(entity);
				if (count == 0) {
					entities.add(entity);
				}
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, "DB接続エラー");
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
					// シーズン年を元にシーズン開始日と終了日を埋める
					String[] years = SeasonDateBuilder.convertSeasonYear(entity.getSeasonYear());
					String startDate = SeasonDateBuilder.buildDate(years[0], entity.getStartSeasonDate());
					String endDate = SeasonDateBuilder.buildDate(years[1], entity.getEndSeasonDate());
					entity.setStartSeasonDate(startDate);
					entity.setEndSeasonDate(endDate);
					int result = this.countryLeagueSeasonMasterRepository.insert(entity);
					if (result != 1) {
						String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
						this.manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
						return 9;
					}
				} catch (DuplicateKeyException e) {
					String messageCd = MessageCdConst.MCD00002W_DUPLICATION_WARNING;
					this.manageLoggerComponent.debugWarnLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd);
					// 重複は特に例外として出さない
					continue;
				} catch (Exception e) {
					String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e);
					return 9;
				}
			}
		}

		String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 登録件数: " + insertEntities.size());
		return 0;
	}

}
