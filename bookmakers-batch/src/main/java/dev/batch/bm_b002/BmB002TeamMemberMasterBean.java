package dev.batch.bm_b002;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.batch.repository.master.CountryLeagueMasterBatchRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.entity.TeamMemberMasterEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * team_member_masterのbeanロジック
 * @author shiraishitoshio
 *
 */
@Component
public class BmB002TeamMemberMasterBean {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = BmB002TeamMemberMasterBean.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = BmB002TeamMemberMasterBean.class.getName();

	/** TeamMemberDBService部品 */
	@Autowired
	private TeamMemberDBService teamMemberDBService;

	/** CountryLeagueMasterRepositoryレポジトリクラス */
	@Autowired
	private CountryLeagueMasterBatchRepository countryLeagueMasterRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent loggerComponent;

	/** memberキーで保持 */
	private Map<String, TeamMemberMasterEntity> teamMemberMap;

	/** country-league -> team list */
	private Map<String, List<String>> teamMap;

	/**
	 * 初期Mapデータ生成
	 */
	public void init() {
		final String METHOD_NAME = "init";

		Map<String, TeamMemberMasterEntity> map = new HashMap<String, TeamMemberMasterEntity>();
		try {
			List<List<TeamMemberMasterEntity>> list = this.teamMemberDBService.selectInBatch();
			for (List<TeamMemberMasterEntity> listTmp : list) {
				for (TeamMemberMasterEntity subMap : listTmp) {
					String key = memberKey(subMap);
					if (key != null) {
						map.put(key, subMap);
					}
				}
			}
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00013E_INITILIZATION_ERROR;
			String fillChar = (e.getMessage() != null) ? e.getMessage() : null;
			this.loggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			this.loggerComponent.createBusinessException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					null,
					null);
		}
		this.teamMemberMap = map;

		Map<String, List<String>> team = new HashMap<String, List<String>>();
		List<CountryLeagueMasterEntity> master = this.countryLeagueMasterRepository.findData();
		for (CountryLeagueMasterEntity entity : master) {
			String country = entity.getCountry();
			String league = entity.getLeague();
			String teams = entity.getTeam();

			if ("ドジャース（大谷 翔平)".equals(teams)
					|| "レイカーズ".equals(teams)
					|| "インテル・マイアミCF".equals(teams)) {
				continue;
			}

			String key = nz(country) + "-" + nz(league);
			team.computeIfAbsent(key, k -> new ArrayList<>()).add(teams);
		}
		this.teamMap = team;
	}

	/**
	 * member 単独キー
	 */
	public static String memberKey(TeamMemberMasterEntity e) {
		if (e == null) {
			return null;
		}
		String member = nz(e.getMember());
		return member.isEmpty() ? null : member;
	}

	private static String nz(String s) {
		return s == null ? "" : s.trim();
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
