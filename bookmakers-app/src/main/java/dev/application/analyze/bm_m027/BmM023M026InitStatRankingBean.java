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
	private static final String CLASS_NAME = BmM023M026InitStatRankingBean.class.getName();

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
		Map<String, List<KeyRanking>> scoreMap = new HashedMap<>();
		List<ScoreBasedFeatureStatsEntity> scoreBasedResult = this.scoreBasedFeatureStatsRepository.findAllStatData();
		Field[] outFields1 = ScoreBasedFeatureStatsEntity.class.getDeclaredFields();

		for (ScoreBasedFeatureStatsEntity scoreEntity : scoreBasedResult) {
			String key = scoreEntity.getCountry() + "_" + scoreEntity.getLeague() + "_"
					+ scoreEntity.getScore();

			int ind = 0;
			for (Field field : outFields1) {
				if (ind < 7) {
					ind++;
					continue;
				}

				String fillChar = field.getName();
				try {
					field.setAccessible(true);

					// String以外は対象外
					if (!String.class.equals(field.getType())) {
						ind++;
						continue;
					}

					String name = field.getName();
					String value = (String) field.get(scoreEntity);
					fillChar = name + ": " + value;

					// null / 空はスキップ
					if (value == null || value.isBlank()) {
						ind++;
						continue;
					}

					ParsedStat stat = parseStatValue(value);

					scoreMap.computeIfAbsent(key, k -> new ArrayList<>())
							.add(new KeyRanking(
									scoreEntity.getId(),
									scoreEntity.getCountry(),
									scoreEntity.getLeague(),
									null,
									scoreEntity.getScore(),
									name,
									stat.min,
									stat.avg,
									stat.max,
									value,
									null));

				} catch (Exception e) {
					String messageCd = MessageCdConst.MCD00013E_INITILIZATION_ERROR;
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
				}
				ind++;
			}
		}
		this.scoreMap = scoreMap;

		// average_statistics_data_detail
		Map<String, List<KeyRanking>> eachScoreMap = new HashedMap<>();
		List<EachTeamScoreBasedFeatureEntity> eachTeamScoreBasedResult = this.eachTeamScoreBasedFeatureStatsRepository
				.findAllStatData();
		Field[] outFields2 = EachTeamScoreBasedFeatureEntity.class.getDeclaredFields();

		for (EachTeamScoreBasedFeatureEntity eachTeamScoreEntity : eachTeamScoreBasedResult) {
			String key = eachTeamScoreEntity.getCountry() + "_" + eachTeamScoreEntity.getLeague() + "_"
					+ eachTeamScoreEntity.getTeam() + "_" + eachTeamScoreEntity.getScore();

			int ind = 0;
			for (Field field : outFields2) {
				if (ind < 8) {
					ind++;
					continue;
				}

				String fillChar = field.getName();
				try {
					field.setAccessible(true);

					// String以外は対象外
					if (!String.class.equals(field.getType())) {
						ind++;
						continue;
					}

					String name = field.getName();
					String value = (String) field.get(eachTeamScoreEntity);
					fillChar = name + ": " + value;

					// null / 空はスキップ
					if (value == null || value.isBlank()) {
						ind++;
						continue;
					}

					ParsedStat stat = parseStatValue(value);

					eachScoreMap.computeIfAbsent(key, k -> new ArrayList<>())
							.add(new KeyRanking(
									eachTeamScoreEntity.getId(),
									eachTeamScoreEntity.getCountry(),
									eachTeamScoreEntity.getLeague(),
									eachTeamScoreEntity.getTeam(),
									eachTeamScoreEntity.getScore(),
									name,
									stat.min,
									stat.avg,
									stat.max,
									value,
									null));

				} catch (Exception e) {
					String messageCd = MessageCdConst.MCD00013E_INITILIZATION_ERROR;
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
				}
				ind++;
			}
		}
		this.eachScoreMap = eachScoreMap;
	}

	/**
	 * 値文字列から min / avg / max を安全に取り出す
	 */
	private ParsedStat parseStatValue(String value) {
		ParsedStat stat = new ParsedStat();

		if (value == null || value.isBlank()) {
			return stat;
		}

		String[] list = value.split(",");

		// 例:
		// - パーセント系: min,xxx,max
		// - 通常系: min,xxx,max,xxx,avg
		if (value.contains("%") && value.contains("(")) {
			stat.min = getPart(list, 0);
			stat.max = getPart(list, 2);
		} else {
			stat.min = getPart(list, 0);
			stat.max = getPart(list, 2);
			stat.avg = getPart(list, 4);
		}

		return stat;
	}

	/**
	 * 配列の指定位置を安全に取得
	 */
	private String getPart(String[] list, int index) {
		if (list == null || index < 0 || index >= list.length) {
			return null;
		}
		String value = list[index];
		return value == null ? null : value.trim();
	}

	/**
	 * パース結果保持用
	 */
	private static class ParsedStat {
		private String min;
		private String avg;
		private String max;
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
