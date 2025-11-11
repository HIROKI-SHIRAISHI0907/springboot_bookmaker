package dev.application.analyze.bm_m033;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.RankHistoryStatRepository;
import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.BookDataEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M033統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
public class RankHistoryStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = RankHistoryStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = RankHistoryStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M033_RANK_HISTORY";

	/** RankHistoryStatRepositoryレポジトリクラス */
	@Autowired
	private RankHistoryStatRepository rankHistoryStatRepository;

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

		// 全リーグ・国を走査
		for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
			Map<String, List<BookDataEntity>> entrySub = entry.getValue();
			for (List<BookDataEntity> entityList : entrySub.values()) {
				// null や空リストはスキップ
				if (entityList == null || entityList.isEmpty())
					continue;
				// 終了済に関するデータのみ
				int match = -1;
				int homeRank = -1;
				int awayRank = -1;
				for (BookDataEntity entity : entityList) {
					if (!entity.getGameTeamCategory().isBlank()) {
						match = ExecuteMainUtil.extractRoundNumbers(entity.getGameTeamCategory());
					}
					if (BookMakersCommonConst.FIN.equals(entity.getTime())) {
						// 順位が設定済み
						if (!entity.getHomeScore().isBlank()) {
							homeRank = Integer.parseInt(entity.getHomeScore().replace(".0", ""));
						} else {

						}

						if (!entity.getAwayScore().isBlank()) {
							awayRank = Integer.parseInt(entity.getAwayScore().replace(".0", ""));
						} else {

						}
					}
				}
				if (match != -1) {
					break;
				}
			}
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

}
