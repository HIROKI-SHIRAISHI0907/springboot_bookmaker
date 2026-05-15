package dev.application.analyze.bm_m026;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.bm_m030.StatEncryptionEntity;
import dev.application.domain.repository.bm.StatEncryptionRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import lombok.extern.slf4j.Slf4j;

/**
 * BM_M030 暗号化保存テーブル書き込みWriter
 */
@Component
@Slf4j
public class EachTeamScoreBasedFeatureStatEncryptionWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = EachTeamScoreBasedFeatureStatEncryptionWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = EachTeamScoreBasedFeatureStatEncryptionWriter.class.getName();

	@Autowired
	private StatEncryptionRepository statEncryptionRepository;

	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * insert / update を 1 件単位の別Transactionで実行
	 * @param entity 暗号化済みエンティティ
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void write(StatEncryptionEntity entity) {
		final String METHOD_NAME = "write";

		String fillChar = String.format("id=%s, team=%s, country=%s, league=%s, chkBody=%s",
				safe(entity.getId()),
				safe(entity.getTeam()),
				safe(entity.getCountry()),
				safe(entity.getLeague()),
				safe(entity.getChkBody()));

		boolean shouldUpdate = entity.isUpdFlg()
				&& entity.getId() != null
				&& !entity.getId().isBlank();

		if (shouldUpdate) {
			log.info("[BM_M026_ENC_WRITER] before update. {}", fillChar);

			int result = this.statEncryptionRepository.updateEncValues(entity);

			log.info("[BM_M026_ENC_WRITER] after update. result={}, {}", result, fillChar);

			if (result != 1) {
				String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, result, fillChar);
			}

			String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					messageCd, "BM_M030 更新件数: " + result + "件");
			return;
		}

		log.info("[BM_M026_ENC_WRITER] before insert. {}", fillChar);

		int result = this.statEncryptionRepository.insert(entity);

		log.info("[BM_M026_ENC_WRITER] after insert. result={}, {}", result, fillChar);

		if (result != 1) {
			String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
			this.rootCauseWrapper.throwUnexpectedRowCount(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					messageCd,
					1, result, fillChar);
		}

		String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME,
				messageCd, "BM_M030 登録件数: " + result + "件");
	}

	private String safe(String s) {
		return s == null ? "" : s;
	}
}
