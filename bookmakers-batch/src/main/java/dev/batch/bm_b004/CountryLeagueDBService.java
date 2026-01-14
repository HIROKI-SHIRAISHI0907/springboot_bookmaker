package dev.batch.bm_b004;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.master.CountryLeagueMasterRepository;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M032マスタデータDB管理部品
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
	private static final String CLASS_NAME = CountryLeagueDBService.class.getSimpleName();

	/** CountryLeagueMasterRepositoryレポジトリクラス */
	@Autowired
	private CountryLeagueMasterRepository countryLeagueMasterRepository;

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
			if (master.getId() == null) {
				return chkEntities;
			}
		} catch (Exception e) {
			String messageCd = "DB接続エラー";
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
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
		} catch (Exception e) {
			String messageCd = "システムエラー";
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e);
			return 9;
		}

		String messageCd = "登録件数: 1件";
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd);
		return 0;
	}

}
