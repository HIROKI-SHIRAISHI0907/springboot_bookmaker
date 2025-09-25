package dev.application.analyze.bm_m028;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.domain.repository.CountryLeagueMasterRepository;
import dev.common.entity.CountryLeagueMasterEntity;
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

	/** CountryLeagueMasterRepositoryレポジトリクラス */
	@Autowired
	private CountryLeagueMasterRepository countryLeagueMasterRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent loggerComponent;

	/** 件数取得 */
	private Map<String, TeamMemberMasterEntity> teamMemberMap;

	/** 件数取得 */
	private Map<String, List<String>> teamMap;

	/** 初期化 */
	@PostConstruct
	public void init() {
		final String METHOD_NAME = "init";
		// hashデータを取得
		Map<String, TeamMemberMasterEntity> map = new HashMap<String, TeamMemberMasterEntity>();
		try {
			List<List<TeamMemberMasterEntity>> list = this.teamMemberDBService.selectInBatch();
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

		// hashデータを取得
		Map<String, List<String>> team = new HashMap<String, List<String>>();
		List<CountryLeagueMasterEntity> master = this.countryLeagueMasterRepository.findData();
		for (CountryLeagueMasterEntity entity : master) {
			String country = entity.getCountry();
			String league = entity.getLeague();
			String teams = entity.getTeam();
			// スキップ
			if ("ドジャース（大谷 翔平)".equals(teams) || "レイカーズ".equals(teams)
					|| "インテル・マイアミCF".equals(teams)) {
				continue;
			}
			String key = country + "-" + league;
			team.computeIfAbsent(key, k -> new ArrayList<>()).add(teams);
		}
		this.teamMap = team;
	}

	/**
	 * メンバーリスト
	 * @return Map<String, TeamMemberMasterEntity>
	 */
	public Map<String, TeamMemberMasterEntity> getMemberMap() {
		return this.teamMemberMap;
	}

	/**
	 * マスタリスト
	 * @return Map<String, List<String>>
	 */
	public Map<String, List<String>> getTeamMap() {
		return this.teamMap;
	}
}
