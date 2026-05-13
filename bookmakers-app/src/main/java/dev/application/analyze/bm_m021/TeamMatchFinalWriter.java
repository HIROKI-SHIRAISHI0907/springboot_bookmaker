package dev.application.analyze.bm_m021;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.TeamMatchFinalStatsRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import lombok.RequiredArgsConstructor;

/**
 * BM_M021 DB更新専用
 * - 1試合(home/away 2件)を同一トランザクションで登録
 *
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class TeamMatchFinalWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamMatchFinalWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamMatchFinalWriter.class.getName();

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M021";

	/** SLF4J Logger */
	private static final Logger log = LoggerFactory.getLogger(TeamMatchFinalWriter.class);

	/** Repository */
	private final TeamMatchFinalStatsRepository teamMatchFinalStatsRepository;

	/** 例外ラッパー */
	private final RootCauseWrapper rootCauseWrapper;

	/** ログ管理クラス */
	private final ManageLoggerComponent manageLoggerComponent;

	/**
	 * 1試合分(home/away 2件)を同一トランザクションで登録
	 * @param homeEntity ホーム側登録エンティティ
	 * @param awayEntity アウェー側登録エンティティ
	 * @param fillChar 埋め字
	 * @return 保存件数
	 */
	@Transactional
	public int writePair(
			final TeamMatchFinalStatsEntity homeEntity,
			final TeamMatchFinalStatsEntity awayEntity,
			final String fillChar) {

		final String METHOD_NAME = "writePair";

		int total = 0;

		log.info("[BM_M021] writePair start. fillChar={}", fillChar);

		total += saveOne(homeEntity, fillChar, "HOME");
		total += saveOne(awayEntity, fillChar, "AWAY");

		log.info("[BM_M021] writePair done. fillChar={}, total={}", fillChar, total);

		String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 登録件数: " + total + "件 (" + fillChar + ")");

		return total;
	}

	/**
	 * 1件登録
	 * @param entity 登録エンティティ
	 * @param fillChar 埋め字
	 * @param side HOME / AWAY
	 * @return 保存件数
	 */
	private int saveOne(
			final TeamMatchFinalStatsEntity entity,
			final String fillChar,
			final String side) {

		final String METHOD_NAME = "saveOne";

		log.info("[BM_M021] before insert. side={}, fillChar={}, entity={}",
				side, fillChar, summarizeEntity(entity));

		int result = this.teamMatchFinalStatsRepository.insert(entity);

		log.info("[BM_M021] after insert. side={}, fillChar={}, result={}",
				side, fillChar, result);

		if (result != 1) {
			String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
			this.rootCauseWrapper.throwUnexpectedRowCount(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					messageCd,
					1, result,
					"side=" + side + ", " + fillChar
			);
		}

		return result;
	}

	/**
	 * insert前ログ用の簡易要約
	 */
	private String summarizeEntity(TeamMatchFinalStatsEntity entity) {
		if (entity == null) {
			return "null";
		}
		try {
			return entity.toString();
		} catch (Exception e) {
			return entity.getClass().getSimpleName() + "@"
					+ Integer.toHexString(System.identityHashCode(entity));
		}
	}
}
