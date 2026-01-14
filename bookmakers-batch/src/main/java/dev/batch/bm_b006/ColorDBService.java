package dev.batch.bm_b006;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.master.TeamColorMasterRepository;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M032色データDB管理部品
 * @author shiraishitoshio
 *
 */
@Component
@Transactional
public class ColorDBService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ColorDBService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ColorDBService.class.getSimpleName();

	/** TeamColorMasterRepositoryレポジトリクラス */
	@Autowired
	private TeamColorMasterRepository teamColorMasterRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * チェックメソッド
	 * @param chkEntities
	 */
	public TeamColorMasterEntity selectInBatch(CountryLeagueMasterEntity chkEntities) {
		final String METHOD_NAME = "selectInBatch";
		try {
			String country = chkEntities.getCountry();
			String league = chkEntities.getLeague();
			String team = chkEntities.getTeam();
			TeamColorMasterEntity master = this.teamColorMasterRepository.findByCountryLeague(country, league, team);
			if (master == null) {
				TeamColorMasterEntity colorEntity = new TeamColorMasterEntity();
				colorEntity.setCountry(country);
				colorEntity.setLeague(league);
				colorEntity.setTeam(team);
				return colorEntity;
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
	public int insertInBatch(TeamColorMasterEntity insertEntities) {
		final String METHOD_NAME = "insertInBatch";
		try {
			int result = this.teamColorMasterRepository.insert(insertEntities);
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
