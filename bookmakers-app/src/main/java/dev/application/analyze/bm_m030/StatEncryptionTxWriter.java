package dev.application.analyze.bm_m030;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.StatEncryptionRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

@Service
public class StatEncryptionTxWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = StatEncryptionTxWriter.class
			.getProtectionDomain().getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = StatEncryptionTxWriter.class.getName();

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M023_BM_M026";

	private static final Logger log = LoggerFactory.getLogger(StatEncryptionTxWriter.class);

	private final StatEncryptionRepository statEncryptionRepository;
	private final RootCauseWrapper rootCauseWrapper;
	private final ManageLoggerComponent manageLoggerComponent;

	public StatEncryptionTxWriter(
			StatEncryptionRepository statEncryptionRepository,
			RootCauseWrapper rootCauseWrapper,
			ManageLoggerComponent manageLoggerComponent) {
		this.statEncryptionRepository = statEncryptionRepository;
		this.rootCauseWrapper = rootCauseWrapper;
		this.manageLoggerComponent = manageLoggerComponent;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void save(StatEncryptionEntity entity) throws Exception {
		if (entity == null) {
			return;
		}

		if (entity.isUpdFlg()) {
			update(entity);
		} else {
			insert(entity);
		}
	}

	private void insert(StatEncryptionEntity entity) throws Exception {
		final String METHOD_NAME = "insert";

		log.info("[BM_M023] before statEncryptionRepository.insert. country={}, league={}, home={}, away={}, chkBody={}",
				entity.getCountry(), entity.getLeague(), entity.getHome(), entity.getAway(), entity.getChkBody());

		int result = this.statEncryptionRepository.insert(entity);

		log.info("[BM_M023] after statEncryptionRepository.insert. result={}", result);

		if (result != 1) {
			String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
			this.rootCauseWrapper.throwUnexpectedRowCount(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					messageCd,
					1, result,
					null);
		}

		String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 登録件数: " + result + "件 (BM_M030)");
	}

	private void update(StatEncryptionEntity entity) throws Exception {
		final String METHOD_NAME = "update";

		log.info("[BM_M023] before statEncryptionRepository.updateEncValues. id={}, country={}, league={}, home={}, away={}, chkBody={}",
				entity.getId(), entity.getCountry(), entity.getLeague(),
				entity.getHome(), entity.getAway(), entity.getChkBody());

		int result = this.statEncryptionRepository.updateEncValues(entity);

		log.info("[BM_M023] after statEncryptionRepository.updateEncValues. result={}", result);

		if (result != 1) {
			String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
			this.rootCauseWrapper.throwUnexpectedRowCount(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					messageCd,
					1, result,
					null);
		}

		String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 更新件数: " + result + "件 (BM_M030)");
	}
}
