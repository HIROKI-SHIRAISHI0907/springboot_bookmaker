package dev.application.analyze.bm_m023H_bm_m026H;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.bm_m023.ScoreBasedFeatureStatsEntity;
import dev.application.analyze.bm_m026.EachTeamScoreBasedFeatureEntity;
import dev.application.domain.repository.bm.EachTeamScoreBasedFeatureStatsHistoryRepository;
import dev.application.domain.repository.bm.EachTeamScoreBasedFeatureStatsRepository;
import dev.application.domain.repository.bm.ScoreBasedFeatureStatsHistoryRepository;
import dev.application.domain.repository.bm.ScoreBasedFeatureStatsRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M023H_BM_M026H統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
public class BasedFeatureHistoryStat {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = BasedFeatureHistoryStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = BasedFeatureHistoryStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M023H_BM_M026H_METRIC_DELTA";

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER_23H = "BM_M023H";

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER_26H = "BM_M026H";

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
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

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
		        PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, null, msg);
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
			String fill = setLoggerFillChar(
					country, league, entity.getSituation(), entity.getScore());
			int result = this.scoreBasedFeatureStatsHistoryRepository.insert(entity);
			if (result != 1) {
				String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, result,
						fill);
			}

			String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					BM_NUMBER_23H + " 登録件数: " + result + "件 (" + fill + ")");
		}
	}

	/**
	 * eachTeamScoreBasedFeatureStatsHistoryRepositoryに登録
	 * @param list
	 */
	private void regEachScoreBasedFeature(String country, String league, String team, List<EachTeamScoreBasedFeatureEntity> list) {
		final String METHOD_NAME = "regEachScoreBasedFeature";
		for (EachTeamScoreBasedFeatureEntity entity : list) {
			String fill = setLoggerFillChar(
					country, league, entity.getTeam(), entity.getSituation(), entity.getScore());
			int result = this.eachTeamScoreBasedFeatureStatsHistoryRepository.insert(entity);
			if (result != 1) {
				String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, result,
						fill);
			}

			String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					BM_NUMBER_26H + " 登録件数: " + result + "件 (" + fill + ")");
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
