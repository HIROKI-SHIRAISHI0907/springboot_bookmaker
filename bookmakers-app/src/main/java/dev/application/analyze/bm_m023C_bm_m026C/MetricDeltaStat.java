package dev.application.analyze.bm_m023C_bm_m026C;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.bm_m023.ScoreBasedFeatureStatsEntity;
import dev.application.analyze.bm_m026.EachTeamScoreBasedFeatureEntity;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.bm.EachTeamScoreBasedFeatureStatsHistoryRepository;
import dev.application.domain.repository.bm.EachTeamScoreBasedFeatureStatsRepository;
import dev.application.domain.repository.bm.ScoreBasedFeatureStatsHistoryRepository;
import dev.application.domain.repository.bm.ScoreBasedFeatureStatsRepository;
import dev.common.entity.BookDataEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M023C_BM_M026C統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
public class MetricDeltaStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = MetricDeltaStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = MetricDeltaStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M023C_BM_M026C_METRIC_DELTA";

	/** ScoreBasedFeatureStatsRepositoryレポジトリクラス */
	@Autowired
	private ScoreBasedFeatureStatsRepository scoreBasedFeatureStatsRepository;

	/** EachTeamScoreBasedFeatureStatsRepositoryレポジトリクラス */
	@Autowired
	private EachTeamScoreBasedFeatureStatsRepository eachTeamScoreBasedFeatureStatsRepository;

	/** ScoreBasedFeatureStatsHistoryRepositoryレポジトリクラス */
	@Autowired
	private ScoreBasedFeatureStatsHistoryRepository scoreBasedFeatureStatsHistoryRepository;

	/** EachTeamScoreBasedFeatureStatsHistoryRepositoryレポジトリクラス */
	@Autowired
	private EachTeamScoreBasedFeatureStatsHistoryRepository eachTeamScoreBasedFeatureStatsHistoryRepository;

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

		// Truncate
		int resultT1 = this.scoreBasedFeatureStatsHistoryRepository.truncate();
		String messageCd = "Truncateしました";
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, "BM_M023C 登録件数: " + resultT1 + "件");
		int resultT2 = this.eachTeamScoreBasedFeatureStatsHistoryRepository.truncate();
		messageCd = "Truncateしました";
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, "BM_M026C 登録件数: " + resultT2 + "件");

		int index = 0;
		final int total = entities.size();
		for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
			String[] data_category = ExecuteMainUtil.splitLeagueInfo(entry.getKey());
			String country = data_category[0];
			String league = data_category[1];
			// scoreBasedFeatureStatsRepository
			List<ScoreBasedFeatureStatsEntity> result1 =
					this.scoreBasedFeatureStatsRepository.findData(country, league);
			regScoreBasedFeature(country, league, result1);

			boolean processed = false;
			Map<String, List<BookDataEntity>> maps = entry.getValue();
			for (List<BookDataEntity> entityList : maps.values()) {
				// null や空リストはスキップ
				if (entityList == null || entityList.isEmpty())
					continue;
				String home = entityList.get(0).getHomeTeamName();
				String away = entityList.get(0).getAwayTeamName();
				// eachTeamScoreBasedFeatureStatsHistoryRepository home
				List<EachTeamScoreBasedFeatureEntity> result2 =
						this.eachTeamScoreBasedFeatureStatsRepository.findData(country, league, home);
				regEachScoreBasedFeature(country, league, home, result2);
				// eachTeamScoreBasedFeatureStatsHistoryRepository away
				List<EachTeamScoreBasedFeatureEntity> result3 =
						this.eachTeamScoreBasedFeatureStatsRepository.findData(country, league, away);
				regEachScoreBasedFeature(country, league, away, result3);
				// 登録が終わったらbreak
				processed = true;
				break;
			}
			// 「外側のn件目/全体」の明確なログにする
		    String msg = processed
		        ? String.format("外枠進捗: %d/%d（%s - %s を処理、mapsから最初の1件のみ）", index, total, country, league)
		        : String.format("外枠進捗: %d/%d（%s - %s は処理対象なし）", index, total, country, league);

		    this.manageLoggerComponent.debugInfoLog(
		        PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, msg);
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * scoreBasedFeatureStatsHistoryRepositoryに登録
	 * @param list
	 */
	private void regScoreBasedFeature(String country, String league, List<ScoreBasedFeatureStatsEntity> list) {
		final String METHOD_NAME = "regScoreBasedFeature";
		for (ScoreBasedFeatureStatsEntity entity : list) {
			int result = this.scoreBasedFeatureStatsHistoryRepository.insert(entity);
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
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, setLoggerFillChar(
							country, league, entity.getSituation(), entity.getScore()), "BM_M023C 登録件数: 1件");
		}
	}

	/**
	 * eachTeamScoreBasedFeatureStatsHistoryRepositoryに登録
	 * @param list
	 */
	private void regEachScoreBasedFeature(String country, String league, String team, List<EachTeamScoreBasedFeatureEntity> list) {
		final String METHOD_NAME = "regEachScoreBasedFeature";
		for (EachTeamScoreBasedFeatureEntity entity : list) {
			int result = this.eachTeamScoreBasedFeatureStatsHistoryRepository.insert(entity);
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
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, setLoggerFillChar(
							country, league, team, entity.getSituation(), entity.getScore()), "BM_M026C 登録件数: 1件");
		}
	}

	/**
	 * 埋め字設定
	 * @param country 国
	 * @param league リーグ
	 * @return
	 */
	private String setLoggerFillChar(String country, String league, String situation, String score) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("国: " + country + ", ");
		stringBuilder.append("リーグ: " + league + ", ");
		stringBuilder.append("状況: " + situation + ", ");
		stringBuilder.append("スコア: " + score);
		return stringBuilder.toString();
	}

	/**
	 * 埋め字設定
	 * @param country 国
	 * @param league リーグ
	 * @param team チーム
	 * @return
	 */
	private String setLoggerFillChar(String country, String league, String team, String situation, String score) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("国: " + country + ", ");
		stringBuilder.append("リーグ: " + league + ", ");
		stringBuilder.append("チーム: " + team + ", ");
		stringBuilder.append("状況: " + situation + ", ");
		stringBuilder.append("スコア: " + score);
		return stringBuilder.toString();
	}

}
