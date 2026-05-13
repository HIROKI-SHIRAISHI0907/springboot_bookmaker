package dev.application.analyze.bm_m019_bm_m020;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.MatchClassificationResultCountRepository;
import dev.application.domain.repository.bm.MatchClassificationResultRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import lombok.RequiredArgsConstructor;

/**
 * DB更新専用
 * - init: 件数初期行の作成
 * - writeLeague: BM_M019明細 + BM_M020件数反映 を同一トランザクションで実行
 */
@Service
@RequiredArgsConstructor
public class MatchClassificationResultWriter {

	private static final String PROJECT_NAME = MatchClassificationResultWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	private static final String CLASS_NAME = MatchClassificationResultWriter.class.getName();

	private static final String BM_NUMBER_19 = "BM_M019";
	private static final String BM_NUMBER_20 = "BM_M020";

	private final MatchClassificationResultRepository matchClassificationResultRepository;
	private final MatchClassificationResultCountRepository matchClassificationResultCountRepository;
	private final RootCauseWrapper rootCauseWrapper;
	private final ManageLoggerComponent manageLoggerComponent;

	/** 件数初期登録（不足行のみ作成） */
	@Transactional
	public void init(String country, String league) {
		final String METHOD_NAME = "init";
		String fillChar = setLoggerFillChar(country, league);

		for (int classify = 1; classify <= MatchClassificationResultStat.SCORE_CLASSIFICATION_ALL_MAP.size() - 1; classify++) {
			if (!this.matchClassificationResultCountRepository
					.findData(country, league, String.valueOf(classify)).isEmpty()) {
				continue;
			}

			MatchClassificationResultCountEntity e = new MatchClassificationResultCountEntity();
			e.setCountry(country);
			e.setLeague(league);
			e.setClassifyMode(String.valueOf(classify));
			e.setCount("0");
			e.setRemarks(getRemarks(classify));

			int result = this.matchClassificationResultCountRepository.insert(e);
			if (result != 1) {
				String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, result,
						"classifymode=" + classify);
			}
		}

		if (this.matchClassificationResultCountRepository.findData(country, league, "-1").isEmpty()) {
			MatchClassificationResultCountEntity e = new MatchClassificationResultCountEntity();
			e.setCountry(country);
			e.setLeague(league);
			e.setClassifyMode("-1");
			e.setCount("0");
			e.setRemarks(getRemarks(-1));

			int result = this.matchClassificationResultCountRepository.insert(e);
			if (result != 1) {
				String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, result,
						"classifymode=-1");
			}

			String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					BM_NUMBER_20 + " 登録件数: "
							+ MatchClassificationResultStat.SCORE_CLASSIFICATION_ALL_MAP.size()
							+ "件 (" + fillChar + ")");
		}
	}

	/**
	 * 1リーグ分の明細登録 + 件数反映
	 * 同一トランザクション
	 */
	@Transactional
	public void writeLeague(
			String country,
			String league,
			List<MatchClassificationResultEntity> bulk,
			List<String> classificationModes) {

		final String METHOD_NAME = "writeLeague";
		String fillChar = setLoggerFillChar(country, league);

		// BM_M019：明細バルク挿入
		if (bulk != null && !bulk.isEmpty()) {
			int result = this.matchClassificationResultRepository.insertBatch(bulk);
			if (result != bulk.size()) {
				String messageCd = MessageCdConst.MCD00011E_BULKINSERT_FAILED;
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						result, bulk.size(),
						"country=" + country + ", league=" + league);
			}

			String messageCd = MessageCdConst.MCD00011I_BULKINSERT_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					BM_NUMBER_19 + " 登録件数: " + bulk.size() + "件 (" + fillChar + ")");
		}

		// BM_M020：件数反映
		if (classificationModes != null && !classificationModes.isEmpty()) {
			for (String classifyMode : classificationModes) {
				if (Objects.isNull(classifyMode)) {
					continue;
				}

				String trimmed = classifyMode.trim();
				if (!trimmed.matches("-?\\d+")) {
					continue;
				}

				MatchClassificationResultCountEntity e = new MatchClassificationResultCountEntity();
				e.setCountry(country);
				e.setLeague(league);
				e.setClassifyMode(trimmed);
				e.setRemarks(getRemarks(safeParseInt(trimmed, -1)));

				int up = this.matchClassificationResultCountRepository.upsertIncrementCount(e);
				if (up != 1) {
					String messageCd = MessageCdConst.MCD00012E_COUNTER_REFLECTION_FAILED;
					this.rootCauseWrapper.throwUnexpectedRowCount(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							messageCd,
							1, up,
							String.format("country=%s, league=%s, classify=%s", country, league, trimmed));
				}

				String messageCd = MessageCdConst.MCD00012I_COUNTER_REFLECTION_SUCCESS;
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
						BM_NUMBER_20 + " 反映: " + up + "件 (classify=" + trimmed + ", " + fillChar + ")");
			}
		}
	}

	private String setLoggerFillChar(String country, String league) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("国: " + country + ", ");
		stringBuilder.append("リーグ: " + league);
		return stringBuilder.toString();
	}

	private static int safeParseInt(String s, int def) {
		try {
			return Integer.parseInt(s);
		} catch (Exception e) {
			return def;
		}
	}

	private String getRemarks(int key) {
		return MatchClassificationResultStat.SCORE_CLASSIFICATION_ALL_MAP.get(key);
	}
}
