package dev.batch.bm_b004;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.master.TeamColorMasterRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_B006色データDB管理部品
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
	private static final String CLASS_NAME = ColorDBService.class.getName();

	/** BM_BATCH_NUMBER */
	private static final String BM_NUMBER = "BM_B006";

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
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
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
				String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
				return 9;
			}
		} catch (DuplicateKeyException e) {
			String messageCd = MessageCdConst.MCD00002W_DUPLICATION_WARNING;
			this.manageLoggerComponent.debugWarnLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd);
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

	/**
	 * チームカラーをチームマスタ（country_league_master の del_flg = '0'）に合わせる（2026-11-24 追加。web の画面と同じ SQL）。
	 * リーグから外れた・昇格降格前のリーグの行は無効にして履歴に残し、昇格降格したチームは同じリンクの前のリーグの色を引き継ぐ。
	 *
	 * @return 手順ごとの件数
	 */
	public String syncWithTeamMaster() {
		return "fillLinkExact=" + this.teamColorMasterRepository.syncFillLinkExact()
				+ ", fillLinkByTeam=" + this.teamColorMasterRepository.syncFillLinkByTeam()
				+ ", deactivateNotInMaster=" + this.teamColorMasterRepository.syncDeactivateNotInMaster()
				+ ", deactivateDuplicates=" + this.teamColorMasterRepository.syncDeactivateDuplicates()
				+ ", reactivate=" + this.teamColorMasterRepository.syncReactivate()
				+ ", insertMissing=" + this.teamColorMasterRepository.syncInsertMissing()
				+ ", carryColors=" + this.teamColorMasterRepository.syncCarryColors();
	}
}
