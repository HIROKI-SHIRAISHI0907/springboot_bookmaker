package dev.application.analyze.bm_m023;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.ScoreBasedFeatureStatsRepository;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;

@Service
public class ScoreBasedFeatureStatsTxWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ScoreBasedFeatureStatsTxWriter.class
			.getProtectionDomain().getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ScoreBasedFeatureStatsTxWriter.class.getName();

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M023";

	private static final Logger log = LoggerFactory.getLogger(ScoreBasedFeatureStatsTxWriter.class);

	private final ScoreBasedFeatureStatsRepository scoreBasedFeatureStatsRepository;
	private final ManageLoggerComponent manageLoggerComponent;

	public ScoreBasedFeatureStatsTxWriter(
			ScoreBasedFeatureStatsRepository scoreBasedFeatureStatsRepository,
			ManageLoggerComponent manageLoggerComponent) {
		this.scoreBasedFeatureStatsRepository = scoreBasedFeatureStatsRepository;
		this.manageLoggerComponent = manageLoggerComponent;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void write(ScoreBasedFeatureStatsEntity entity) {
		if (entity == null) {
			return;
		}

		if (entity.isUpd()) {
			update(entity);
		} else {
			insert(entity);
		}
	}

	private void insert(ScoreBasedFeatureStatsEntity entity) {
		final String METHOD_NAME = "insert";
		String fillChar = setLoggerFillChar(
				entity.getSituation(), entity.getScore(), entity.getCountry(), entity.getLeague());

		log.info("[BM_M023] before scoreBasedFeatureStatsRepository.insert. {}", fillChar);

		int result = this.scoreBasedFeatureStatsRepository.insert(entity);

		log.info("[BM_M023] after scoreBasedFeatureStatsRepository.insert. {} result={}", fillChar, result);

		if (result != 1) {
			String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, fillChar);
			this.manageLoggerComponent.createSystemException(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, null);
		}

		String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 登録件数: " + result + "件 (" + fillChar + ")");
	}

	private void update(ScoreBasedFeatureStatsEntity entity) {
		final String METHOD_NAME = "update";
		String fillChar = setLoggerFillChar(
				entity.getSituation(), entity.getScore(), entity.getCountry(), entity.getLeague());

		log.info("[BM_M023] before scoreBasedFeatureStatsRepository.updateStatValues. {}", fillChar);

		int result = this.scoreBasedFeatureStatsRepository.updateStatValues(entity);

		log.info("[BM_M023] after scoreBasedFeatureStatsRepository.updateStatValues. {} result={}", fillChar, result);

		if (result != 1) {
			String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, fillChar);
			this.manageLoggerComponent.createSystemException(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, null);
		}

		String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 更新件数: " + result + "件 (" + fillChar + ")");
	}

	private String setLoggerFillChar(String situation, String score, String country, String league) {
		StringBuilder sb = new StringBuilder();
		sb.append("状況: ").append(situation).append(", ");
		sb.append("スコア: ").append(score).append(", ");
		sb.append("国: ").append(country).append(", ");
		sb.append("リーグ: ").append(league);
		return sb.toString();
	}
}
