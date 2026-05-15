package dev.application.analyze.bm_m031;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.bm_m032.SurfaceOverviewProcessEntity;
import dev.application.domain.repository.bm.SurfaceOverviewProcessRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * surface_overview_process 書き込みWriter
 */
@Component
public class SurfaceOverviewProcessWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = SurfaceOverviewProcessWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = SurfaceOverviewProcessWriter.class.getName();

	@Autowired
	private SurfaceOverviewProcessRepository surfaceOverviewProcessRepository;

	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * process entity を upsert 保存
	 * @param entity 対象エンティティ
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void write(SurfaceOverviewProcessEntity entity) {
		final String METHOD_NAME = "write";

		if (entity == null || entity.getCurrentRoundNo() == null) {
			return;
		}

		List<SurfaceOverviewProcessEntity> rows = surfaceOverviewProcessRepository.findByKey(
				entity.getCountry(),
				entity.getLeague(),
				entity.getGameYear(),
				entity.getGameMonth(),
				entity.getTeam(),
				entity.getCurrentRoundNo());

		int result;
		String fillChar = buildFillChar(entity);

		if (rows == null || rows.isEmpty()) {
			result = surfaceOverviewProcessRepository.insert(entity);
			if (result != 1) {
				String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
				rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, 1, result, fillChar);
			}

			manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00005I_INSERT_SUCCESS,
					"surface_overview_process 登録件数: " + result + "件 (" + fillChar + ")");
			return;
		}

		result = surfaceOverviewProcessRepository.update(entity);
		if (result != 1) {
			String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
			rootCauseWrapper.throwUnexpectedRowCount(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, 1, result, fillChar);
		}

		manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME,
				MessageCdConst.MCD00006I_UPDATE_SUCCESS,
				"surface_overview_process 更新件数: " + result + "件 (" + fillChar + ")");
	}

	private String buildFillChar(SurfaceOverviewProcessEntity entity) {
		return String.format("country=%s, league=%s, year=%s, month=%s, team=%s, round=%s",
				safe(entity.getCountry()),
				safe(entity.getLeague()),
				safe(entity.getGameYear()),
				safe(entity.getGameMonth()),
				safe(entity.getTeam()),
				safe(entity.getCurrentRoundNo()));
	}

	private String safe(Object value) {
		return value == null ? "" : String.valueOf(value);
	}
}
