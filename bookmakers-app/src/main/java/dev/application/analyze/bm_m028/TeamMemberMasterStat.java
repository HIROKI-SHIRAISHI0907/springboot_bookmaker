package dev.application.analyze.bm_m028;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.interf.TeamMemberEntityIF;
import dev.common.entity.TeamMemberMasterEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M028統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
@Transactional
public class TeamMemberMasterStat implements TeamMemberEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamMemberMasterStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamMemberMasterStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M028_TEAM_MEMBER";

	/** beanクラス */
	@Autowired
	private BmM028TeamMemberMasterBean bean;

	/** TeamMemberDBService部品 */
	@Autowired
	private TeamMemberDBService teamMemberDBService;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 * @throws Exception
	 */
	@Override
	public void teamMemberStat(Map<String, List<TeamMemberMasterEntity>> entities) throws Exception {
		final String METHOD_NAME = "execute";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// メンバーマップ
		Map<String, TeamMemberMasterEntity> memberMap = bean.getMemberMap();

		List<String> insertPath = new ArrayList<String>();
		// 今後の対戦カードを登録する
		for (Map.Entry<String, List<TeamMemberMasterEntity>> map : entities.entrySet()) {
			String filePath = map.getKey();
			String fillChar = "ファイル名: " + filePath;
			List<TeamMemberMasterEntity> insertEntities = new ArrayList<TeamMemberMasterEntity>();
			List<TeamMemberMasterEntity> updateEntities = new ArrayList<TeamMemberMasterEntity>();
			try {
				List<TeamMemberMasterEntity> editedList = editData(map.getValue());
				for (TeamMemberMasterEntity entity : editedList) {
					String member = entity.getMember();
					// 監督はskip
					if ("監督".equals(entity.getPosition())) continue;
					// insertとupdateで分ける
					if (memberMap.containsKey(member)) {
						TeamMemberMasterEntity oldEntity = memberMap.get(member);
						TeamMemberMasterEntity updData = updateData(entity, oldEntity);
						if (updData != null) {
							updateEntities.add(updData);
						}
					} else {
						insertEntities.add(entity);
					}
				}
				int result = this.teamMemberDBService.insertInBatch(insertEntities);
				if (result == 9) {
					String messageCd = "新規登録エラー";
					throw new Exception(messageCd);
				}
				result = this.teamMemberDBService.updateInBatch(updateEntities, fillChar);
				if (result == 9) {
					String messageCd = "更新エラー";
					throw new Exception(messageCd);
				}
				insertPath.add(filePath);
			} catch (Exception e) {
				String messageCd = "システムエラー";
				throw new Exception(messageCd, e);
			}
		}

		// 途中で例外が起きなければ全てのファイルを削除する
		//		for (String path : insertPath) {
		//			try {
		//				Files.deleteIfExists(Paths.get(path));
		//			} catch (IOException e) {
		//				this.manageLoggerComponent.debugErrorLog(
		//						PROJECT_NAME, CLASS_NAME, METHOD_NAME, "ファイル削除失敗", e, path);
		//				// ここでは例外をthrowしないことで、DB登録は保持
		//			}
		//		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * 編集データメソッド
	 * @param entity
	 * @return
	 */
	private List<TeamMemberMasterEntity> editData(List<TeamMemberMasterEntity> entities) {
		List<TeamMemberMasterEntity> newDtoList = new ArrayList<TeamMemberMasterEntity>();
		for (TeamMemberMasterEntity exDto : entities) {
			// チーム(暫定データ)を所属チームリストに詰め替える
			exDto.setBelongList(exDto.getTeam());
			// 得点数(暫定データ)を対戦相手,得点数(対戦相手-得点数をカンマ繋ぎ)に詰め替える
			// TODO: 詰め替え対象フィールドを検討
			//newDto.setVersusTeamScoreData(exDto.getScore());
			// ローンが設定されている場合は,あり
			String deadline = (!"".equals(exDto.getLoanBelong())) ? "1" : "0";
			exDto.setDeadline(deadline);
			exDto.setRetireFlg("0");
			newDtoList.add(exDto);
		}
		return newDtoList;
	}

	/**
	 * 更新データメソッド(更新が必要なもの(memberが同一のものが存在)する場合が対象)
	 * @param entities
	 * @param selectEntities
	 * @return
	 */
	private TeamMemberMasterEntity updateData(TeamMemberMasterEntity exDto, TeamMemberMasterEntity oldDto) {
		if (!oldDto.getMember().equals(exDto.getMember())) {
			return null;
		}
		TeamMemberMasterEntity newDto = new TeamMemberMasterEntity();
		newDto.setId(oldDto.getId());
		newDto.setCountry(oldDto.getCountry());
		newDto.setLeague(oldDto.getLeague());
		newDto.setMember(oldDto.getMember());
		newDto.setJersey((oldDto.getJersey() == null || "".equals(oldDto.getJersey())
				? exDto.getJersey() : oldDto.getJersey()));
		newDto.setFacePicPath((oldDto.getFacePicPath() == null || "".equals(oldDto.getFacePicPath())
				? exDto.getFacePicPath() : oldDto.getFacePicPath()));
		newDto.setBirth(oldDto.getBirth());
		newDto.setAge((oldDto.getAge() == null || "".equals(oldDto.getAge())
				? exDto.getAge() : oldDto.getAge()));
		newDto.setInjury((oldDto.getInjury() == null || "".equals(oldDto.getInjury())
				? exDto.getInjury() : oldDto.getInjury()));
		String deadline = (!"".equals(exDto.getLoanBelong())) ? "1" : "0";
		newDto.setDeadline(deadline);
		newDto.setRetireFlg("0");
		newDto.setBelongList(mergeHistory(oldDto.getTeam(), exDto.getTeam()));
		newDto.setHeight(mergeHistory(oldDto.getHeight(), exDto.getHeight()));
		newDto.setWeight(mergeHistory(oldDto.getWeight(), exDto.getWeight()));
		newDto.setPosition(mergeHistory(oldDto.getPosition(), exDto.getPosition()));
		newDto.setMarketValue(mergeHistory(oldDto.getMarketValue(), exDto.getMarketValue()));
		return newDto;
	}

	/**
	 *  値の履歴を "old→new→newest" のように連結
	 * @param oldValue
	 * @param newValue
	 * @return
	 */
	private String mergeHistory(String oldValue, String newValue) {
		if (newValue == null || newValue.isEmpty()) {
			return oldValue != null ? oldValue : "";
		}
		if (oldValue == null || oldValue.isEmpty()) {
			return newValue;
		}
		// ★ 追加: 同じ値が連結されないようにする
		if (oldValue != null && (oldValue.endsWith("→" + newValue) || oldValue.equals(newValue))) {
			return oldValue;
		}
		// すでに履歴が存在する場合は末尾に追記
		if (oldValue.contains("→")) {
			return oldValue + "→" + newValue;
		}
		return oldValue + "→" + newValue;
	}

}
