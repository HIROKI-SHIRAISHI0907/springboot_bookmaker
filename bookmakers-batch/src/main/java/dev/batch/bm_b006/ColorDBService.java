package dev.batch.bm_b006;

import java.util.ArrayList;
import java.util.List;

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
	public List<TeamColorMasterEntity> selectInBatch(List<CountryLeagueMasterEntity> chkEntities
			) {
		final String METHOD_NAME = "selectInBatch";
		List<TeamColorMasterEntity> entities = new ArrayList<TeamColorMasterEntity>();
		for (CountryLeagueMasterEntity entity : chkEntities) {
			try {
				String country = entity.getCountry();
				String league = entity.getLeague();
				String team = entity.getTeam();
				List<TeamColorMasterEntity> master = this.teamColorMasterRepository.findByCountryLeague(country, league, team);
				if (master.isEmpty()) {
					TeamColorMasterEntity colorEntity = new TeamColorMasterEntity();
					colorEntity.setCountry(country);
					colorEntity.setLeague(league);
					colorEntity.setTeam(team);
					entities.add(colorEntity);
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
	public int insertInBatch(List<TeamColorMasterEntity> insertEntities) {
		final String METHOD_NAME = "insertInBatch";
		final int BATCH_SIZE = 100;
		for (int i = 0; i < insertEntities.size(); i += BATCH_SIZE) {
			int end = Math.min(i + BATCH_SIZE, insertEntities.size());
			List<TeamColorMasterEntity> batch = insertEntities.subList(i, end);
			for (TeamColorMasterEntity entity : batch) {
				try {
					int result = this.teamColorMasterRepository.insert(entity);
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
