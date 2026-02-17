package dev.batch.bm_b004;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.master.CountryLeagueMasterBatchRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * CountryLeagueDBService管理部品
 * @author shiraishitoshio
 *
 */
@Component
@Transactional
public class CountryLeagueDBService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CountryLeagueDBService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CountryLeagueDBService.class.getName();

	/** BM_BATCH_NUMBER */
	private static final String BM_NUMBER = "BM_B004";

	/** CountryLeagueMasterRepositoryレポジトリクラス */
	@Autowired
	private CountryLeagueMasterBatchRepository countryLeagueMasterRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * チェックメソッド
	 * @param chkEntities
	 */
	public CountryLeagueMasterEntity selectInBatch(CountryLeagueMasterEntity chkEntities) {
		final String METHOD_NAME = "selectInBatch";
		try {
			String country = chkEntities.getCountry();
			String league = chkEntities.getLeague();
			String team = chkEntities.getTeam();
			CountryLeagueMasterEntity master = this.countryLeagueMasterRepository.findByCountryLeague(country, league,
					team);
			if (master == null) {
				return chkEntities;
			}
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, "DB接続エラー");
			throw e;
		}
		return null;
	}

	/**
	 * 登録メソッド
	 * @param insertEntities
	 */
	public int insertInBatch(CountryLeagueMasterEntity insertEntities) {
		final String METHOD_NAME = "insertInBatch";
		try {
			int result = this.countryLeagueMasterRepository.insert(insertEntities);
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
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e);
			return 9;
		}

		String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 登録件数: 1件");
		return 0;
	}
}
