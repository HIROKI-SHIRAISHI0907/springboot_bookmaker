package dev.application.analyze.bm_m005;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.NoGoalMatchStatsRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M005 登録処理
 */
@Service
public class NoGoalMatchWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = NoGoalMatchWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = NoGoalMatchWriter.class.getName();

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M005";

	/** NoGoalMatchStatisticsRepositoryレポジトリクラス */
	@Autowired
	private NoGoalMatchStatsRepository noGoalMatchStatisticsRepository;

	/** ログ管理ラッパー*/
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 登録ロジック
	 * @param entity
	 */
	@Transactional
	public void insert(NoGoalMatchStatisticsEntity entity) {
		final String METHOD_NAME = "insert";

		int result = this.noGoalMatchStatisticsRepository.insert(entity);
		if (result != 1) {
			String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
			this.rootCauseWrapper.throwUnexpectedRowCount(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					messageCd, 1, result, null);
		}

		String fillChar = setLoggerFillChar(entity);
		String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 登録件数: " + result + "件 (" + fillChar + ")");
	}

	/**
	 * 埋め字設定
	 * @param entity
	 * @return
	 */
	private String setLoggerFillChar(NoGoalMatchStatisticsEntity entity) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("国,リーグ: ").append(entity.getDataCategory()).append(", ");
		stringBuilder.append("ホームチーム: ").append(entity.getHomeTeamName()).append(", ");
		stringBuilder.append("アウェーチーム: ").append(entity.getAwayTeamName());
		return stringBuilder.toString();
	}
}
