package dev.application.analyze.bm_m004;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.TeamTimeSegmentShootingStatsRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M004 登録処理
 */
@Service
public class TeamTimeSegmentShootingWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamTimeSegmentShootingWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamTimeSegmentShootingWriter.class.getName();

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M004";

	private final TeamTimeSegmentShootingStatsRepository teamTimeSegmentShootingStatsRepository;
	private final RootCauseWrapper rootCauseWrapper;
	private final ManageLoggerComponent manageLoggerComponent;

	@Autowired
	public TeamTimeSegmentShootingWriter(
			TeamTimeSegmentShootingStatsRepository teamTimeSegmentShootingStatsRepository,
			RootCauseWrapper rootCauseWrapper,
			ManageLoggerComponent manageLoggerComponent) {
		this.teamTimeSegmentShootingStatsRepository = teamTimeSegmentShootingStatsRepository;
		this.rootCauseWrapper = rootCauseWrapper;
		this.manageLoggerComponent = manageLoggerComponent;
	}

	/**
	 * 登録
	 * @param entity
	 */
	@Transactional
	public void insert(TeamTimeSegmentShootingStatsEntity entity) {
		final String METHOD_NAME = "insert";

		if (entity == null) {
			return;
		}

		int result = this.teamTimeSegmentShootingStatsRepository.insert(entity);
		if (result != 1) {
			String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
			this.rootCauseWrapper.throwUnexpectedRowCount(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					1,
					result,
					null);
		}

		String fillChar = buildLoggerFillChar(entity);
		String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME,
				CLASS_NAME,
				METHOD_NAME,
				messageCd,
				BM_NUMBER + " 登録件数: 1件 (" + fillChar + ")");
	}

	/**
	 * 埋め字設定
	 * @param entity
	 * @return
	 */
	private String buildLoggerFillChar(TeamTimeSegmentShootingStatsEntity entity) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("国,リーグ: ").append(entity.getDataCategory()).append(", ");
		stringBuilder.append("ホームチーム: ").append(entity.getTeamName()).append(", ");
		stringBuilder.append("アウェーチーム: ").append(entity.getAwayTeamName());
		return stringBuilder.toString();
	}
}
