package dev.application.analyze.bm_m027;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.map.HashedMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.bm_m023.ScoreBasedFeatureStatsEntity;
import dev.application.analyze.bm_m026.EachTeamScoreBasedFeatureEntity;
import dev.application.domain.repository.bm.EachTeamScoreBasedFeatureStatsRepository;
import dev.application.domain.repository.bm.ScoreBasedFeatureStatsRepository;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;

/**
 * average_statistics_dataのbeanロジック
 * @author shiraishitoshio
 *
 */
@Component
public class BmM023M026InitStatRankingBean {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = BmM023M026InitStatRankingBean.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = BmM023M026InitStatRankingBean.class.getSimpleName();

	/** ScoreBasedFeatureStatsRepositoryレポジトリクラス */
	@Autowired
	private ScoreBasedFeatureStatsRepository scoreBasedFeatureStatsRepository;

	/** EachTeamScoreBasedFeatureStatsRepositoryレポジトリクラス */
	@Autowired
	private EachTeamScoreBasedFeatureStatsRepository eachTeamScoreBasedFeatureStatsRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/** スコアマップクラス */
	private Map<String, List<KeyRanking>> scoreMap;

	/** チームマップクラス */
	private Map<String, List<KeyRanking>> eachScoreMap;

	/** 初期化 */
	public void init() {
		final String METHOD_NAME = "init";
		// average_statistics_data
		Map<String, List<KeyRanking>> scoreMap = new HashedMap<String, List<KeyRanking>>();
		List<ScoreBasedFeatureStatsEntity> scoreBasedResult = this.scoreBasedFeatureStatsRepository.findAllStatData();
		Field[] outFields1 = ScoreBasedFeatureStatsEntity.class.getDeclaredFields();
		for (ScoreBasedFeatureStatsEntity scoreEntity : scoreBasedResult) {
			String key = scoreEntity.getCountry() + "_" + scoreEntity.getLeague() + "_"
					+ scoreEntity.getScore();
			String fillChar = "";
			try {
				int ind = 0;
				for (Field field : outFields1) {
					if (ind < 7) {
						ind++;
						continue;
					}
					field.setAccessible(true);
					String name = field.getName();
					String value = (String) field.get(scoreEntity);
					fillChar = name + ": " + value;
					String min = null;
					String avg = null;
					String max = null;
					if (value.contains("%") && value.contains("(")) {
						String[] list = value.split(",");
						min = list[0];
						max = list[2];
					} else {
						String[] list = value.split(",");
						min = list[0];
						max = list[2];
						avg = list[4];
					}
					scoreMap.computeIfAbsent(key, k -> new ArrayList<>())
							.add(new KeyRanking(scoreEntity.getId(),
									scoreEntity.getCountry(), scoreEntity.getLeague(), null,
									scoreEntity.getScore(),
									name, min, avg, max, value, null));
					ind++;
				}
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00013E_INITILIZATION_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			}
		}
		this.scoreMap = scoreMap;

		// average_statistics_data_detail
		Map<String, List<KeyRanking>> eachScoreMap = new HashedMap<String, List<KeyRanking>>();
		List<EachTeamScoreBasedFeatureEntity> eachTeamScoreBasedResult = this.eachTeamScoreBasedFeatureStatsRepository
				.findAllStatData();
		Field[] outFields2 = EachTeamScoreBasedFeatureEntity.class.getDeclaredFields();
		for (EachTeamScoreBasedFeatureEntity eachTeamScoreEntity : eachTeamScoreBasedResult) {
			String key = eachTeamScoreEntity.getCountry() + "_" + eachTeamScoreEntity.getLeague() + "_"
					+ eachTeamScoreEntity.getTeam() + "_" + eachTeamScoreEntity.getScore();
			String fillChar = "";
			try {
				int ind = 0;
				for (Field field : outFields2) {
					if (ind < 8) {
						ind++;
						continue;
					}
					field.setAccessible(true);
					String name = field.getName();
					String value = (String) field.get(eachTeamScoreEntity);
					fillChar = name + ": " + value;
					String min = null;
					String avg = null;
					String max = null;
					if (value.contains("%") && value.contains("(")) {
						String[] list = value.split(",");
						min = list[0];
						max = list[2];
					} else {
						String[] list = value.split(",");
						min = list[0];
						max = list[2];
						avg = list[4];
					}
					eachScoreMap.computeIfAbsent(key, k -> new ArrayList<>())
							.add(new KeyRanking(eachTeamScoreEntity.getId(),
									eachTeamScoreEntity.getCountry(), eachTeamScoreEntity.getLeague(),
									eachTeamScoreEntity.getTeam(), eachTeamScoreEntity.getScore(),
									name, min, avg, max, value, null));
					ind++;
				}
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00013E_INITILIZATION_ERROR;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			}
		}
		this.eachScoreMap = eachScoreMap;
	}

	/**
	 * スコアマップ
	 * @return scoreMap
	 */
	public Map<String, List<KeyRanking>> getScoreMap() {
		return scoreMap;
	}

	/**
	 * チームマップ
	 * @return eachScoreMap
	 */
	public Map<String, List<KeyRanking>> getEachScoreMap() {
		return eachScoreMap;
	}

}
