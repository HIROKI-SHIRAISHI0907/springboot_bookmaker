package dev.application.analyze.bm_m028;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.entity.TeamMemberMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import jakarta.annotation.PostConstruct;

/**
 * team_member_masterのbeanロジック
 * @author shiraishitoshio
 *
 */
@Component
public class BmM028TeamMemberMasterBean {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = BmM028TeamMemberMasterBean.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = BmM028TeamMemberMasterBean.class.getSimpleName();

	/** TeamMemberDBService部品 */
	@Autowired
	private TeamMemberDBService teamMemberDBService;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent loggerComponent;

	/** 件数取得 */
	private Map<String, TeamMemberMasterEntity> teamMemberMap;

	/** 初期化 */
	@PostConstruct
	public void init() {
		final String METHOD_NAME = "init";
		// hashデータを取得
		Map<String, TeamMemberMasterEntity> map =
				new HashMap<String, TeamMemberMasterEntity>();
		try {
			List<List<TeamMemberMasterEntity>> list =
				this.teamMemberDBService.selectInBatch();
			for (List<TeamMemberMasterEntity> listTmp : list) {
				for (TeamMemberMasterEntity subMap : listTmp) {
					String member = subMap.getMember();
					map.put(member, subMap);
				}
			}
		} catch (Exception e) {
			String fillChar = (e.getMessage() != null) ? e.getMessage() : null;
			this.loggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, e, fillChar);
			this.loggerComponent.createBusinessException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					null,
					null);
		}

		this.teamMemberMap = map;
	}

	/**
	 * メンバーリスト
	 * @return Map<String, TeamMemberMasterEntity>
	 */
	public Map<String, TeamMemberMasterEntity> getMemberMap() {
		return this.teamMemberMap;
	}
}
