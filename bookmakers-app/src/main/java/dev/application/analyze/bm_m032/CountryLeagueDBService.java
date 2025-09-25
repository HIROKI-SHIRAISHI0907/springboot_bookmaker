package dev.application.analyze.bm_m032;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.CountryLeagueMasterRepository;
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
	public List<CountryLeagueMasterEntity> selectInBatch(List<CountryLeagueMasterEntity> chkEntities
			) {
		final String METHOD_NAME = "selectInBatch";
		List<CountryLeagueMasterEntity> entities = new ArrayList<CountryLeagueMasterEntity>();
		for (CountryLeagueMasterEntity entity : chkEntities) {
			try {
				String country = entity.getCountry();
				String league = entity.getLeague();
				String team = entity.getTeam();
				List<CountryLeagueMasterEntity> master = this.countryLeagueMasterRepository.findByCountryLeague(country, league, team);
				if (master.isEmpty()) {
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
	public int insertInBatch(List<CountryLeagueMasterEntity> insertEntities) {
		final String METHOD_NAME = "insertInBatch";
		final int BATCH_SIZE = 100;
		for (int i = 0; i < insertEntities.size(); i += BATCH_SIZE) {
			int end = Math.min(i + BATCH_SIZE, insertEntities.size());
			List<CountryLeagueMasterEntity> batch = insertEntities.subList(i, end);
			for (CountryLeagueMasterEntity entity : batch) {
				try {
					int result = this.countryLeagueMasterRepository.insert(entity);
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
		String messageCd = "BM_M032 登録件数: " + insertEntities.size();
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd);
		return 0;
	}

}
