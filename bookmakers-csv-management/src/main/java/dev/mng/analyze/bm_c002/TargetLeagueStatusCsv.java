package dev.mng.analyze.bm_c002;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.common.logger.ManageLoggerComponent;
import dev.mng.domain.repository.TargetLeagueStatusRepository;

/**
 * BM_C002CSVロジック
 * @author shiraishitoshio
 *
 */
@Component
@Transactional
public class TargetLeagueStatusCsv {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TargetLeagueStatusCsv.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TargetLeagueStatusCsv.class.getSimpleName();

	/** TargetLeagueStatusRepositoryレポジトリクラス */
	@Autowired
	private TargetLeagueStatusRepository targetLeagueStatusRepository;

	/** ログ管理 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * ステータス新規登録・更新
	 */
	public void updateStatus(CsvMngInputDTO inputDTO) {
		final String METHOD_NAME = "updateStatus";
		if (inputDTO == null || inputDTO.getSubInfo() == null || inputDTO.getSubInfo().isEmpty()) {
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, "updateStatus", null, "対象なし");
			return;
		}

		int totalAffected = 0;
		int insertAffected = 0;
		int updateAffected = 0;
		int noUpdateAffected = 0;

		for (SubInfo subInfo : inputDTO.getSubInfo()) {
			String country = subInfo.getCountry();
			String league = subInfo.getLeague();
			String status = subInfo.getStatus();

			List<TargetLeagueStatusEntity> resultList = this.targetLeagueStatusRepository.findByData(country, league);
			if (!resultList.isEmpty()) {
				String exStatus = resultList.get(0).getStatus();
				if (!exStatus.equals(status)) {
					String exId = resultList.get(0).getId();
					int result = this.targetLeagueStatusRepository.updateById(exId, status);
					if (result != 1) {
						String messageCd = "更新エラー";
						this.manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
						this.manageLoggerComponent.createSystemException(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
					}
					this.manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, "更新件数", null, "BM_C002 更新件数: 1件");
					updateAffected++;
				} else {
					noUpdateAffected++;
				}
			} else {
				TargetLeagueStatusEntity ent = new TargetLeagueStatusEntity();
				ent.setCountry(country);
				ent.setLeague(league);
				ent.setStatus(status);
				int result = this.targetLeagueStatusRepository.insert(ent);
				if (result != 1) {
					String messageCd = "新規登録エラー";
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
					this.manageLoggerComponent.createSystemException(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
				}
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, "登録件数", null, "BM_C002 登録件数: 1件");
				insertAffected++;
			}
			totalAffected++;
		}

		String messageCd = "全体更新件数";
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null,
				"BC_C002 対象件数: " + totalAffected + "件, 全体登録件数: "
						+ insertAffected + "件, 全体更新件数: " + updateAffected + "件, "
						+ "全体失敗件数: " + noUpdateAffected + "件");
	}

}
