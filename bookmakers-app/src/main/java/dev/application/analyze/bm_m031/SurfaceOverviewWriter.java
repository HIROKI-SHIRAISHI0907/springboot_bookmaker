package dev.application.analyze.bm_m031;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.SurfaceOverviewRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * surface_overview 書き込みWriter
 */
@Component
public class SurfaceOverviewWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = SurfaceOverviewWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = SurfaceOverviewWriter.class.getName();

	@Autowired
	private SurfaceOverviewRepository surfaceOverviewRepository;

	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * insert / update
	 * @param entity 対象エンティティ
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void write(SurfaceOverviewEntity entity) {
		final String METHOD_NAME = "write";

		if (entity == null) {
			return;
		}

		int result;
		String fillChar = buildFillChar(entity);

		if (entity.getId() != null && !entity.getId().isBlank()) {
			result = surfaceOverviewRepository.update(entity);
			if (result != 1) {
				String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
				rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, 1, result, fillChar);
			}

			manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00006I_UPDATE_SUCCESS,
					"surface_overview 更新件数: " + result + "件 (" + fillChar + ")");
			return;
		}

		result = surfaceOverviewRepository.insert(entity);
		if (result != 1) {
			String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
			rootCauseWrapper.throwUnexpectedRowCount(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, 1, result, fillChar);
		}

		manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME,
				MessageCdConst.MCD00005I_INSERT_SUCCESS,
				"surface_overview 登録件数: " + result + "件 (" + fillChar + ")");
	}

	private String buildFillChar(SurfaceOverviewEntity entity) {
		return String.format("country=%s, league=%s, year=%s, month=%s, team=%s, id=%s",
				safe(entity.getCountry()),
				safe(entity.getLeague()),
				entity.getGameYear(),
				entity.getGameMonth(),
				safe(entity.getTeam()),
				safe(entity.getId()));
	}

	private String safe(String s) {
		return s == null ? "" : s;
	}
}
