package dev.application.analyze.bm_m027;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.bm_m026.EachTeamScoreBasedFeatureEntity;
import dev.application.domain.repository.bm.EachTeamScoreBasedFeatureStatsRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M027 EachTeamScoreBasedFeature 更新Writer
 */
@Component
public class AnalyzeRankingEachTeamScoreBasedFeatureWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = AnalyzeRankingEachTeamScoreBasedFeatureWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = AnalyzeRankingEachTeamScoreBasedFeatureWriter.class.getName();

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M027";

	/** 備考 */
	private static final String EACH_SCORE_BASED_FEATURE = "eachTeamScoreBasedFeatureStats";

	@Autowired
	private EachTeamScoreBasedFeatureStatsRepository eachTeamScoreBasedFeatureStatsRepository;

	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void write(EachTeamScoreBasedFeatureEntity entity) {
		final String METHOD_NAME = "write";

		String fillChar = setLoggerFillChar(
				entity.getScore(),
				entity.getCountry(),
				entity.getLeague(),
				EACH_SCORE_BASED_FEATURE);

		int result = this.eachTeamScoreBasedFeatureStatsRepository.updateStatValues(entity);

		if (result != 1) {
			String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
			this.rootCauseWrapper.throwUnexpectedRowCount(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					messageCd, 1, result,
					String.format("id=%s", entity.getId()));
		}

		String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 更新件数: " + result + "件 (" + fillChar + ")");
	}

	private String setLoggerFillChar(String score, String country, String league, String bikou) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("スコア: ").append(score).append(", ");
		stringBuilder.append("国: ").append(country).append(", ");
		stringBuilder.append("リーグ: ").append(league).append(", ");
		stringBuilder.append("備考: ").append(bikou);
		return stringBuilder.toString();
	}
}
