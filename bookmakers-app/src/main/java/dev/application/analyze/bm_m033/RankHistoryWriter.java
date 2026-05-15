package dev.application.analyze.bm_m033;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.RankHistoryStatRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M033 ランク履歴Writer
 * DB登録/更新のみ担当
 */
@Component
@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
public class RankHistoryWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = RankHistoryWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = RankHistoryWriter.class.getName();

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M033";

	/** RankHistoryStatRepositoryレポジトリクラス */
	@Autowired
	private RankHistoryStatRepository rankHistoryStatRepository;

	/** ログ管理ラッパー */
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * ランク履歴を登録/更新
	 * @param entity ランク履歴
	 */
	public void write(RankHistoryEntity entity) {
		final String METHOD_NAME = "write";

		validate(entity);

		String key = buildKey(entity);

		if (this.rankHistoryStatRepository.select(entity) > 0) {
			int result = this.rankHistoryStatRepository.update(entity);
			if (result != 1) {
				String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, result,
						key);
			}

			String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					BM_NUMBER + " 更新件数: " + result + "件 (" + key + ")");
		} else {
			int result = this.rankHistoryStatRepository.insert(entity);
			if (result != 1) {
				String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, result,
						key);
			}

			String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					BM_NUMBER + " 登録件数: " + result + "件 (" + key + ")");
		}
	}

	/**
	 * 入力検証
	 */
	private void validate(RankHistoryEntity entity) {
		if (entity == null) {
			throw new IllegalArgumentException("RankHistoryEntity is null.");
		}
		if (isBlank(entity.getCountry())) {
			throw new IllegalArgumentException("country is blank.");
		}
		if (isBlank(entity.getLeague())) {
			throw new IllegalArgumentException("league is blank.");
		}
		if (isBlank(entity.getSeasonYear())) {
			throw new IllegalArgumentException("seasonYear is blank.");
		}
		if (entity.getMatch() == null) {
			throw new IllegalArgumentException("match is null.");
		}
		if (isBlank(entity.getTeam())) {
			throw new IllegalArgumentException("team is blank.");
		}
		if (entity.getRank() == null) {
			throw new IllegalArgumentException("rank is null.");
		}
	}

	/**
	 * ログ用キー
	 */
	private String buildKey(RankHistoryEntity entity) {
		return String.join(" / ",
				nullToEmpty(entity.getCountry()),
				nullToEmpty(entity.getLeague()),
				nullToEmpty(entity.getSeasonYear()),
				String.valueOf(entity.getMatch()),
				nullToEmpty(entity.getTeam()),
				String.valueOf(entity.getRank()));
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
