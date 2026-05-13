package dev.application.analyze.bm_m002;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.common.util.BookMakersCommonConst;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M002統計分析ロジック（手動データ投入の場合は適用対象外）
 *
 * @author shiraishitoshio
 *
 */
@Component
public class ConditionResultDataStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ConditionResultDataStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ConditionResultDataStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M002_CONDITION_RESULT_DATA";

	/** 件数配列サイズ */
	private static final int COUNT_SIZE = 11;

	/** 添字定義 */
	private static final int IDX_MAIL_TARGET = 0;
	private static final int IDX_MAIL_ANONYMOUS_TARGET = 1;
	private static final int IDX_MAIL_TARGET_SUCCESS = 2;
	private static final int IDX_MAIL_TARGET_FAIL = 3;
	private static final int IDX_MAIL_TARGET_TO_RESULT_UNKNOWN = 4;
	private static final int IDX_MAIL_FIN_NO_DATA_TO_RESULT_UNKNOWN = 5;
	private static final int IDX_GOAL_DELETE = 6;
	private static final int IDX_DUE_TO_GOAL_DELETE_MAIL_TARGET_MAIL_ANONYMOUS_TARGET_ALTER = 7;
	private static final int IDX_DUE_TO_GOAL_DELETE_MAIL_TARGET_SUCCESS_MAIL_TARGET_FAIL_ALTER = 8;
	private static final int IDX_RESULT_UNKNOWN = 9;
	private static final int IDX_UNEXPECTED_ERROR = 10;

	/** 判定→添字変換Map */
	private static final Map<String, Integer> JUDGE_TO_INDEX_MAP = createJudgeToIndexMap();

	/** Beanクラス */
	@Autowired
	private BmM002ConditionResultDataBean bean;

	/** Writerクラス */
	@Autowired
	private ConditionResultDataWriter conditionResultDataWriter;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";

		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		try {
			// 初期化
			bean.init();

			// condition_result_data bean
			Integer[] conditionCountIntList = toConditionCountArray(bean.getConditionCountList());

			// 一時的に別リストに保管
			Integer[] updConditionCountIntList = conditionCountIntList.clone();

			// Mapを回す
			if (entities != null) {
				for (Map<String, List<BookDataEntity>> innerMap : entities.values()) {
					if (innerMap == null) {
						continue;
					}

					for (List<BookDataEntity> list : innerMap.values()) {
						if (list == null) {
							continue;
						}

						for (BookDataEntity entity : list) {
							if (entity == null) {
								updConditionCountIntList[IDX_UNEXPECTED_ERROR]++;
								continue;
							}

							this.manageLoggerComponent.debugInfoLog(
									PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, entity.getFilePath());

							String judge = entity.getJudge();

							if (judge == null) {
								updConditionCountIntList[IDX_UNEXPECTED_ERROR]++;
								continue;
							}

							int index = JUDGE_TO_INDEX_MAP.getOrDefault(judge, IDX_RESULT_UNKNOWN);
							updConditionCountIntList[index]++;
						}
					}
				}
			}

			// 件数用ログ設定
			String fillChar = setLogCount(conditionCountIntList, updConditionCountIntList);

			// 登録,更新
			this.conditionResultDataWriter.save(
					bean.getUpdFlg(),
					updConditionCountIntList,
					bean.getConditionKeyData(),
					bean.getHash(),
					fillChar);

		} finally {
			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();
		}
	}

	/**
	 * 判定→添字変換Map生成
	 * @return 判定→添字変換Map
	 */
	private static Map<String, Integer> createJudgeToIndexMap() {
		Map<String, Integer> map = new HashMap<String, Integer>();
		map.put(BookMakersCommonConst.MAIL_TARGET, IDX_MAIL_TARGET);
		map.put(BookMakersCommonConst.MAIL_ANONYMOUS_TARGET, IDX_MAIL_ANONYMOUS_TARGET);
		map.put(BookMakersCommonConst.MAIL_TARGET_SUCCESS, IDX_MAIL_TARGET_SUCCESS);
		map.put(BookMakersCommonConst.MAIL_TARGET_FAIL, IDX_MAIL_TARGET_FAIL);
		map.put(BookMakersCommonConst.MAIL_TARGET_TO_RESULT_UNKNOWN, IDX_MAIL_TARGET_TO_RESULT_UNKNOWN);
		map.put(BookMakersCommonConst.MAIL_FIN_NO_DATA_TO_RESULT_UNKNOWN, IDX_MAIL_FIN_NO_DATA_TO_RESULT_UNKNOWN);
		map.put(BookMakersCommonConst.GOAL_DELETE, IDX_GOAL_DELETE);
		map.put(BookMakersCommonConst.DUE_TO_GOAL_DELETE_MAIL_TARGET_MAIL_ANONYMOUS_TARGET_ALTER,
				IDX_DUE_TO_GOAL_DELETE_MAIL_TARGET_MAIL_ANONYMOUS_TARGET_ALTER);
		map.put(BookMakersCommonConst.DUE_TO_GOAL_DELETE_MAIL_TARGET_SUCCESS_MAIL_TARGET_FAIL_ALTER,
				IDX_DUE_TO_GOAL_DELETE_MAIL_TARGET_SUCCESS_MAIL_TARGET_FAIL_ALTER);
		return Collections.unmodifiableMap(map);
	}

	/**
	 * 件数配列変換
	 * @param countList 文字列配列
	 * @return Integer配列
	 */
	private Integer[] toConditionCountArray(String[] countList) {
		Integer[] result = new Integer[COUNT_SIZE];
		Arrays.fill(result, Integer.valueOf(0));

		if (countList == null) {
			return result;
		}

		int loopSize = Math.min(countList.length, COUNT_SIZE);
		for (int i = 0; i < loopSize; i++) {
			result[i] = parseCount(countList[i]);
		}

		return result;
	}

	/**
	 * 件数文字列変換
	 * @param value 件数
	 * @return 件数
	 */
	private Integer parseCount(String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/**
	 * ログ設定
	 * @param conditionCountIntList 更新前件数
	 * @param updConditionCountIntList 更新後件数
	 * @return ログ文字列
	 */
	private String setLogCount(Integer[] conditionCountIntList, Integer[] updConditionCountIntList) {
		StringBuilder sBuilder = new StringBuilder();
		sBuilder.append(BookMakersCommonConst.MAIL_TARGET + ": {" + conditionCountIntList[IDX_MAIL_TARGET] + "} → "
				+ "{" + updConditionCountIntList[IDX_MAIL_TARGET] + "}, ");
		sBuilder.append(BookMakersCommonConst.MAIL_ANONYMOUS_TARGET + ": {"
				+ conditionCountIntList[IDX_MAIL_ANONYMOUS_TARGET] + "} → "
				+ "{" + updConditionCountIntList[IDX_MAIL_ANONYMOUS_TARGET] + "}, ");
		sBuilder.append(BookMakersCommonConst.MAIL_TARGET_SUCCESS + ": {"
				+ conditionCountIntList[IDX_MAIL_TARGET_SUCCESS] + "} → "
				+ "{" + updConditionCountIntList[IDX_MAIL_TARGET_SUCCESS] + "}, ");
		sBuilder.append(BookMakersCommonConst.MAIL_TARGET_FAIL + ": {"
				+ conditionCountIntList[IDX_MAIL_TARGET_FAIL] + "} → "
				+ "{" + updConditionCountIntList[IDX_MAIL_TARGET_FAIL] + "}, ");
		sBuilder.append(BookMakersCommonConst.MAIL_TARGET_TO_RESULT_UNKNOWN + ": {"
				+ conditionCountIntList[IDX_MAIL_TARGET_TO_RESULT_UNKNOWN] + "} → "
				+ "{" + updConditionCountIntList[IDX_MAIL_TARGET_TO_RESULT_UNKNOWN] + "}, ");
		sBuilder.append(BookMakersCommonConst.MAIL_FIN_NO_DATA_TO_RESULT_UNKNOWN + ": {"
				+ conditionCountIntList[IDX_MAIL_FIN_NO_DATA_TO_RESULT_UNKNOWN] + "} → "
				+ "{" + updConditionCountIntList[IDX_MAIL_FIN_NO_DATA_TO_RESULT_UNKNOWN] + "}, ");
		sBuilder.append(BookMakersCommonConst.GOAL_DELETE + ": {"
				+ conditionCountIntList[IDX_GOAL_DELETE] + "} → "
				+ "{" + updConditionCountIntList[IDX_GOAL_DELETE] + "}, ");
		sBuilder.append(BookMakersCommonConst.DUE_TO_GOAL_DELETE_MAIL_TARGET_MAIL_ANONYMOUS_TARGET_ALTER + ": {"
				+ conditionCountIntList[IDX_DUE_TO_GOAL_DELETE_MAIL_TARGET_MAIL_ANONYMOUS_TARGET_ALTER] + "} → "
				+ "{"
				+ updConditionCountIntList[IDX_DUE_TO_GOAL_DELETE_MAIL_TARGET_MAIL_ANONYMOUS_TARGET_ALTER] + "}, ");
		sBuilder.append(BookMakersCommonConst.DUE_TO_GOAL_DELETE_MAIL_TARGET_SUCCESS_MAIL_TARGET_FAIL_ALTER + ": {"
				+ conditionCountIntList[IDX_DUE_TO_GOAL_DELETE_MAIL_TARGET_SUCCESS_MAIL_TARGET_FAIL_ALTER] + "} → "
				+ "{"
				+ updConditionCountIntList[IDX_DUE_TO_GOAL_DELETE_MAIL_TARGET_SUCCESS_MAIL_TARGET_FAIL_ALTER] + "}, ");
		sBuilder.append(BookMakersCommonConst.RESULT_UNKNOWN + ": {"
				+ conditionCountIntList[IDX_RESULT_UNKNOWN] + "} → "
				+ "{" + updConditionCountIntList[IDX_RESULT_UNKNOWN] + "}, ");
		sBuilder.append(BookMakersCommonConst.UNEXPECTED_ERROR + ": {"
				+ conditionCountIntList[IDX_UNEXPECTED_ERROR] + "} → "
				+ "{" + updConditionCountIntList[IDX_UNEXPECTED_ERROR] + "}, ");
		return sBuilder.toString();
	}
}
