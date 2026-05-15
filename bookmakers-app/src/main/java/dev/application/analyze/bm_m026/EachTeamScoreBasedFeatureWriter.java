package dev.application.analyze.bm_m026;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.EachTeamScoreBasedFeatureStatsRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import lombok.extern.slf4j.Slf4j;

/**
 * BM_M026 本体書き込みWriter
 */
@Component
@Slf4j
public class EachTeamScoreBasedFeatureWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = EachTeamScoreBasedFeatureWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = EachTeamScoreBasedFeatureWriter.class.getName();

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M026";

	@Autowired
	private EachTeamScoreBasedFeatureStatsRepository eachTeamScoreBasedFeatureStatsRepository;

	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * insert / update を 1 件単位の別Transactionで実行
	 * @param entity BM_M026本体エンティティ
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void write(EachTeamScoreBasedFeatureEntity entity) {
		final String METHOD_NAME = "write";

		String key = buildKey(entity);

		try {
			log.info("[BM_M026_WRITER] before update. {}", key);

			int updated = eachTeamScoreBasedFeatureStatsRepository.updateStatValues(entity);

			log.info("[BM_M026_WRITER] after update. updated={}, {}", updated, key);

			if (updated == 1) {
				String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd, BM_NUMBER + " 更新件数: " + updated + "件");
				return;
			}

			if (updated > 1) {
				String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, updated, key);
			}

			log.info("[BM_M026_WRITER] no row updated. try insert. {}", key);

			try {
				int inserted = eachTeamScoreBasedFeatureStatsRepository.insert(entity);

				log.info("[BM_M026_WRITER] after insert. inserted={}, {}", inserted, key);

				if (inserted == 1) {
					String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
					this.manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							messageCd, BM_NUMBER + " 登録件数: " + inserted + "件");
					return;
				}

				String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, inserted, key);

			} catch (DuplicateKeyException e) {
				log.warn("[BM_M026_WRITER] duplicate on insert, retry update. {}", key, e);

				int retried = eachTeamScoreBasedFeatureStatsRepository.updateStatValues(entity);

				log.info("[BM_M026_WRITER] after retry update. retried={}, {}", retried, key);

				if (retried == 1) {
					String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
					this.manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							messageCd, BM_NUMBER + " 更新件数: " + retried + "件");
					return;
				}

				String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, retried, key);
			}

		} catch (RuntimeException e) {
			log.error("[BM_M026_WRITER] write failed. key={}, entity={}", key, entity, e);
			throw e;
		}
	}

	private String buildKey(EachTeamScoreBasedFeatureEntity entity) {
		return String.format(
				"score=%s, country=%s, league=%s, team=%s, situation=%s",
				safe(entity.getScore()),
				safe(entity.getCountry()),
				safe(entity.getLeague()),
				safe(entity.getTeam()),
				safe(entity.getSituation()));
	}

	private String safe(String s) {
		return s == null ? "" : s;
	}
}
