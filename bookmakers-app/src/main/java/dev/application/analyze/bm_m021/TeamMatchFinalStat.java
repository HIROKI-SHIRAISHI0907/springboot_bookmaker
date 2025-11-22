package dev.application.analyze.bm_m021;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.bm.TeamMatchFinalStatsRepository;
import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.BookDataEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M021統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
public class TeamMatchFinalStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamMatchFinalStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamMatchFinalStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M021_TEAM_MATCH_FINAL";

	/** TeamMatchFinalStatsRepositoryレポジトリクラス */
	@Autowired
	private TeamMatchFinalStatsRepository teamMatchFinalStatsRepository;

	/** BookDataToTeamMatchFinalMapperマッパークラス */
	@Autowired
	private BookDataToTeamMatchFinalMapper bookDataToTeamMatchFinalMapper;

	/** ログ管理ラッパー*/
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		entities.entrySet().parallelStream().forEach(entry -> {
			Map<String, List<BookDataEntity>> matchMap = entry.getValue();
			for (List<BookDataEntity> dataList : matchMap.values()) {
				BookDataEntity returnMaxEntity = ExecuteMainUtil.getMaxSeqEntities(dataList);
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, returnMaxEntity.getFilePath());
				if (!finDataExistsChk(returnMaxEntity))
					continue;
				// 支配率,3分割データについて設定
				TeamMatchFinalOutputDTO dto = setFinalData(dataList, returnMaxEntity);
				// その他情報設定,BM_M021登録
				setFinal(dto, returnMaxEntity);
			}
		});

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * 登録メソッド
	 * @param entities エンティティ
	 * @param fillChar 埋め字
	 */
	private synchronized void saveTeamMatchData(TeamMatchFinalStatsEntity entities, String fillChar) {
		final String METHOD_NAME = "saveTeamMatchData";
		int result = this.teamMatchFinalStatsRepository.insert(entities);
		if (result != 1) {
			String messageCd = "新規登録エラー";
			this.rootCauseWrapper.throwUnexpectedRowCount(
			        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
			        messageCd,
			        1, result,
			        null
			    );
		}
		String messageCd = "登録件数";
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar, "BM_M021 登録件数: 1件");
	}

	/**
	 * 終了済データ存在チェック
	 * @param entity BookDataEntity
	 * @return
	 */
	private boolean finDataExistsChk(BookDataEntity entity) {
		final String METHOD_NAME = "finDataExistsChk";
		String fillChar = setLoggerFillChar(entity.getGameTeamCategory(),
				entity.getHomeTeamName(), entity.getAwayTeamName());
		// 最大通番を持つデータを取得
		if (!BookMakersCommonConst.FIN.equals(entity.getTime())) {
			String messageCd = "終了済データなし";
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar);
			return false;
		}
		return true;
	}

	/**
	 * 埋め字設定
	 * @param detaKey 国リーグ
	 * @param home ホーム
	 * @param away アウェー
	 * @return
	 */
	private String setLoggerFillChar(String detaKey, String home, String away) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("国,リーグ: " + detaKey + ", ");
		stringBuilder.append("ホーム: " + home + ", ");
		stringBuilder.append("アウェー: " + away);
		return stringBuilder.toString();
	}

	/**
	 * 特殊最終データ格納
	 * @param entity List<BookDataEntity>
	 * @param returnMaxEntity BookDataEntity
	 * @return
	 */
	private TeamMatchFinalOutputDTO setFinalData(List<BookDataEntity> entity, BookDataEntity returnMaxEntity) {
		TeamMatchFinalOutputDTO teamMatchFinalOutputDTO = new TeamMatchFinalOutputDTO();
		// ボール保持率
		double totalHomePossession = 0.0;
		double totalAwayPossession = 0.0;
		int countHome = 0;
		int countAway = 0;
		for (BookDataEntity e : entity) {
			String homePossStr = e.getHomeBallPossesion();
			if (homePossStr != null && homePossStr.endsWith("%")) {
				try {
					double value = Double.parseDouble(homePossStr.replace("%", "").trim());
					totalHomePossession += value;
					countHome++;
				} catch (NumberFormatException ex) {
					// パースできなかった場合は無視
				}
			}

			String awayPossStr = e.getAwayBallPossesion();
			if (awayPossStr != null && awayPossStr.endsWith("%")) {
				try {
					double value = Double.parseDouble(awayPossStr.replace("%", "").trim());
					totalAwayPossession += value;
					countAway++;
				} catch (NumberFormatException ex) {
					// パースできなかった場合は無視
				}
			}
		}
		// 平均値
		String avgHomePossession = countHome > 0 ? String.format("%.2f%%", totalHomePossession / countHome)
				: String.format("%.2f%%", totalHomePossession);
		String avgAwayPossession = countAway > 0 ? String.format("%.2f%%", totalAwayPossession / countAway)
				: String.format("%.2f%%", totalAwayPossession);

		// 3分割データ
		List<String> homePassList = ExecuteMainUtil.splitGroup(returnMaxEntity.getHomePassCount());
		List<String> awayPassList = ExecuteMainUtil.splitGroup(returnMaxEntity.getAwayPassCount());
		List<String> homeFinalPassList = ExecuteMainUtil.splitGroup(returnMaxEntity.getHomeFinalThirdPassCount());
		List<String> awayFinalPassList = ExecuteMainUtil.splitGroup(returnMaxEntity.getAwayFinalThirdPassCount());
		List<String> homeCrossList = ExecuteMainUtil.splitGroup(returnMaxEntity.getHomeCrossCount());
		List<String> awayCrossList = ExecuteMainUtil.splitGroup(returnMaxEntity.getAwayCrossCount());
		List<String> homeTackleList = ExecuteMainUtil.splitGroup(returnMaxEntity.getHomeTackleCount());
		List<String> awayTackleList = ExecuteMainUtil.splitGroup(returnMaxEntity.getAwayTackleCount());

		FinalData homeFinalData = new FinalData();
		homeFinalData.setPossession(avgHomePossession);
		homeFinalData.setPass(new RetentionData(homePassList.get(0), homePassList.get(1), homePassList.get(2)));
		homeFinalData.setFinalThirdPass(new RetentionData(homeFinalPassList.get(0),
				homeFinalPassList.get(1), homeFinalPassList.get(2)));
		homeFinalData.setCross(new RetentionData(homeCrossList.get(0), homeCrossList.get(1), homeCrossList.get(2)));
		homeFinalData.setTackle(new RetentionData(homeTackleList.get(0), homeTackleList.get(1), homeTackleList.get(2)));

		FinalData awayFinalData = new FinalData();
		awayFinalData.setPossession(avgAwayPossession);
		awayFinalData.setPass(new RetentionData(awayPassList.get(0), awayPassList.get(1), awayPassList.get(2)));
		awayFinalData.setFinalThirdPass(new RetentionData(awayFinalPassList.get(0),
				awayFinalPassList.get(1), awayFinalPassList.get(2)));
		awayFinalData.setCross(new RetentionData(awayCrossList.get(0), awayCrossList.get(1), awayCrossList.get(2)));
		awayFinalData.setTackle(new RetentionData(awayTackleList.get(0), awayTackleList.get(1), awayTackleList.get(2)));

		teamMatchFinalOutputDTO.setHomeObject(homeFinalData);
		teamMatchFinalOutputDTO.setAwayObject(awayFinalData);
		return teamMatchFinalOutputDTO;
	}

	/**
	 * 最終登録データを格納
	 * @param dto TeamMatchFinalOutputDTO
	 * @param returnMaxEntity BookDataEntity
	 */
	private void setFinal(final TeamMatchFinalOutputDTO dto, final BookDataEntity returnMaxEntity) {
		// 特殊データは詰め替える
		BookDataEntity mappEntity = returnMaxEntity;
		FinalData finalHomeData = dto.getHomeObject();
		FinalData finalAwayData = dto.getAwayObject();
		String resultHome = compareScore(returnMaxEntity.getHomeScore(),
				returnMaxEntity.getAwayScore());
		String resultAway = compareScore(returnMaxEntity.getAwayScore(),
				returnMaxEntity.getHomeScore());
		String fillChar = setLoggerFillChar(returnMaxEntity.getGameTeamCategory(),
				returnMaxEntity.getHomeTeamName(), returnMaxEntity.getAwayTeamName());
		TeamMatchFinalStatsEntity homeMatchFinalStatsEntity = this.bookDataToTeamMatchFinalMapper.mapHomeStruct(
				mappEntity, finalHomeData, finalAwayData,
				"H", resultHome, setSymbol(resultHome) + returnMaxEntity.getHomeScore() + "-" + returnMaxEntity.getAwayScore());
		TeamMatchFinalStatsEntity awayMatchFinalStatsEntity = this.bookDataToTeamMatchFinalMapper.mapAwayStruct(
				mappEntity, finalAwayData, finalHomeData,
				"A", resultAway, setSymbol(resultAway) + returnMaxEntity.getAwayScore() + "-" + returnMaxEntity.getHomeScore());

		saveTeamMatchData(homeMatchFinalStatsEntity, fillChar);
		saveTeamMatchData(awayMatchFinalStatsEntity, fillChar);
	}

	/**
	 * スコア比較
	 * @param homeScore
	 * @param awayScore
	 * @return
	 */
	private String compareScore(final String homeScore, final String awayScore) {
		try {
	        int home = Integer.parseInt(homeScore);
	        int away = Integer.parseInt(awayScore);
	        if (home > away) return "WIN";
	        if (home < away) return "LOSE";
	        return "DRAW";
	    } catch (NumberFormatException e) {
	        return "DRAW"; // or handle error
	    }
	}

	/**
	 * 勝敗のマークを返す
	 * @param homeScore
	 * @param awayScore
	 * @return
	 */
	private String setSymbol(final String result) {
		String mark = "";
		if ("WIN".equals(result)) {
			mark = "○";
		} else if ("LOSE".equals(result)) {
			mark = "●";
		} else {
			mark = "△";
		}
		return mark;
	}

}
