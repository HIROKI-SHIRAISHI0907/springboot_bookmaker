package dev.application.analyze.bm_m029;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.RealDataProcessRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M029 リアルタイム差分保存 Writer
 */
@Component
public class RealDataProcessWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = RealDataProcessWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = RealDataProcessWriter.class.getName();

	/** BM番号 */
	private static final String BM_NUMBER = "BM_M029";

	@Autowired
	private RealDataProcessRepository realDataProcessRepository;

	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * match_id 基準で UPSERT
	 * 1件単位の独立Transaction
	 * @param entity 差分エンティティ
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void write(RealDataProcessEntity entity) {
		final String METHOD_NAME = "write";

		String matchId = trimToNull(entity.getMatchId());
		if (isBlank(matchId)) {
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					BM_NUMBER + " skip: matchId が空のため保存スキップ (" + setLoggerFillChar(entity) + ")");
			return;
		}

		int result = this.realDataProcessRepository.upsertByMatchId(entity);

		if (result != 1) {
			String errorCd = MessageCdConst.MCD00007E_INSERT_FAILED;
			this.rootCauseWrapper.throwUnexpectedRowCount(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					errorCd,
					1, result,
					null);
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				BM_NUMBER + " UPSERT件数: 1件 (" + setLoggerFillChar(entity) + ")");
	}

	private String setLoggerFillChar(RealDataProcessEntity entity) {
		StringBuilder sb = new StringBuilder();
		sb.append("国,リーグ: ").append(entity.getDataCategory()).append(", ");
		sb.append("ホームチーム: ").append(entity.getHomeTeamName()).append(", ");
		sb.append("アウェーチーム: ").append(entity.getAwayTeamName()).append(", ");
		sb.append("試合時間: ").append(entity.getTimes()).append(", ");
		sb.append("記録時間: ").append(entity.getRecordTime()).append(", ");
		sb.append("gameId: ").append(entity.getGameId()).append(", ");
		sb.append("matchId: ").append(entity.getMatchId());
		return sb.toString();
	}

	private String trimToNull(String str) {
		if (str == null) {
			return null;
		}
		String s = str.trim();
		return s.isEmpty() ? null : s;
	}

	private boolean isBlank(String str) {
		return str == null || str.trim().isEmpty();
	}
}
