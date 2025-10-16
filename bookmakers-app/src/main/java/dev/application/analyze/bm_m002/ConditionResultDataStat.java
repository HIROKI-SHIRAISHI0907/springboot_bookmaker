package dev.application.analyze.bm_m002;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.common.util.BookMakersCommonConst;
import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.ConditionResultDataRepository;
import dev.common.entity.BookDataEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M002統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
public class ConditionResultDataStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = ConditionResultDataStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = ConditionResultDataStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M002_CONDITION_RESULT_DATA";

	/** Beanクラス */
	@Autowired
	private BmM002ConditionResultDataBean bean;

	/** ConditionResultDataRepositoryレポジトリクラス */
	@Autowired
	private ConditionResultDataRepository conditionResultDataRepository;

	/** ログ管理ラッパー*/
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// condition_result_data bean
		Integer[] conditionCountIntList = Arrays.stream(bean.getConditionCountList())
				.map(s -> {
					try {
						return Integer.parseInt(s);
					} catch (NumberFormatException e) {
						return 0;
					}
				})
				.toArray(Integer[]::new);

		// 一時的に別リストに保管
		Integer[] updConditionCountIntList = conditionCountIntList.clone();

		Map<String, Integer> judgeToIndexMap = Map.ofEntries(
				Map.entry(BookMakersCommonConst.MAIL_TARGET, 0),
				Map.entry(BookMakersCommonConst.MAIL_ANONYMOUS_TARGET, 1),
				Map.entry(BookMakersCommonConst.MAIL_TARGET_SUCCESS, 2),
				Map.entry(BookMakersCommonConst.MAIL_TARGET_FAIL, 3),
				Map.entry(BookMakersCommonConst.MAIL_TARGET_TO_RESULT_UNKNOWN, 4),
				Map.entry(BookMakersCommonConst.MAIL_FIN_NO_DATA_TO_RESULT_UNKNOWN, 5),
				Map.entry(BookMakersCommonConst.GOAL_DELETE, 6),
				Map.entry(BookMakersCommonConst.DUE_TO_GOAL_DELETE_MAIL_TARGET_MAIL_ANONYMOUS_TARGET_ALTER, 7),
				Map.entry(BookMakersCommonConst.DUE_TO_GOAL_DELETE_MAIL_TARGET_SUCCESS_MAIL_TARGET_FAIL_ALTER, 8));

		// Mapを回す
		for (Map<String, List<BookDataEntity>> innerMap : entities.values()) {
			for (List<BookDataEntity> list : innerMap.values()) {
				for (BookDataEntity entity : list) {
					this.manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, entity.getFilePath());
					String judge = entity.getJudge();

					if (judge != null) {
		                int index = judgeToIndexMap.getOrDefault(judge, 9); // 該当しない場合は未定義扱い
		                updConditionCountIntList[index]++;
		            } else {
		                updConditionCountIntList[10]++; // null → 異常系としてカウント
		            }
				}
			}
		}

		// 件数用ログ設定
		String fillChar = setLogCount(conditionCountIntList, updConditionCountIntList);

		// 登録,更新
		boolean updFlg = bean.getUpdFlg();
		newData(updFlg, updConditionCountIntList, bean.getConditionKeyData(), bean.getHash(), fillChar);

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();

	}

	/**
	 * ログ設定
	 * @param conditionCountIntList 更新前件数
	 * @param updConditionCountIntList 更新後件数
	 * @return
	 */
	private String setLogCount(Integer[] conditionCountIntList,
			Integer[] updConditionCountIntList) {
		StringBuilder sBuilder = new StringBuilder();
		sBuilder.append(BookMakersCommonConst.MAIL_TARGET + ": {" + conditionCountIntList[0] + "} → "
				+ "{" + updConditionCountIntList[0] + "}, ");
		sBuilder.append(BookMakersCommonConst.MAIL_ANONYMOUS_TARGET + ": {" + conditionCountIntList[1] + "} → "
				+ "{" + updConditionCountIntList[1] + "}, ");
		sBuilder.append(BookMakersCommonConst.MAIL_TARGET_SUCCESS + ": {" + conditionCountIntList[2] + "} → "
				+ "{" + updConditionCountIntList[2] + "}, ");
		sBuilder.append(BookMakersCommonConst.MAIL_TARGET_FAIL + ": {" + conditionCountIntList[3] + "} → "
				+ "{" + updConditionCountIntList[3] + "}, ");
		sBuilder.append(BookMakersCommonConst.MAIL_TARGET_TO_RESULT_UNKNOWN + ": {" + conditionCountIntList[4] + "} → "
				+ "{" + updConditionCountIntList[4] + "}, ");
		sBuilder.append(
				BookMakersCommonConst.MAIL_FIN_NO_DATA_TO_RESULT_UNKNOWN + ": {" + conditionCountIntList[5] + "} → "
						+ "{" + updConditionCountIntList[5] + "}, ");
		sBuilder.append(BookMakersCommonConst.GOAL_DELETE + ": {" + conditionCountIntList[6] + "} → "
				+ "{" + updConditionCountIntList[6] + "}, ");
		sBuilder.append(BookMakersCommonConst.DUE_TO_GOAL_DELETE_MAIL_TARGET_MAIL_ANONYMOUS_TARGET_ALTER + ": {"
				+ conditionCountIntList[7] + "} → "
				+ "{" + updConditionCountIntList[7] + "}, ");
		sBuilder.append(BookMakersCommonConst.DUE_TO_GOAL_DELETE_MAIL_TARGET_SUCCESS_MAIL_TARGET_FAIL_ALTER + ": {"
				+ conditionCountIntList[8] + "} → "
				+ "{" + updConditionCountIntList[8] + "}, ");
		sBuilder.append(BookMakersCommonConst.RESULT_UNKNOWN + ": {" + conditionCountIntList[9] + "} → "
				+ "{" + updConditionCountIntList[9] + "}, ");
		sBuilder.append(BookMakersCommonConst.UNEXPECTED_ERROR + ": {" + conditionCountIntList[10] + "} → "
				+ "{" + updConditionCountIntList[10] + "}, ");
		return sBuilder.toString();
	}

	/**
	 * 登録,更新メソッド
	 * @param updFlg 更新フラグ
	 * @param updConditionCountIntList 更新件数
	 * @param condition 条件分岐データ
	 * @param hash ハッシュ
	 * @param fillChar 埋め字
	 */
	private synchronized void newData(boolean updFlg, Integer[] updConditionCountIntList, String condition, String hash,
			String fillChar) {
		final String METHOD_NAME = "newData";

		// Entity設定
		ConditionResultDataEntity conditionResultDataEntity = new ConditionResultDataEntity();
		conditionResultDataEntity.setMailTargetCount(String.valueOf(updConditionCountIntList[0]));
		conditionResultDataEntity.setMailAnonymousTargetCount(String.valueOf(updConditionCountIntList[1]));
		conditionResultDataEntity.setMailTargetSuccessCount(String.valueOf(updConditionCountIntList[2]));
		conditionResultDataEntity.setMailTargetFailCount(String.valueOf(updConditionCountIntList[3]));
		conditionResultDataEntity.setExMailTargetToNoResultCount(String.valueOf(updConditionCountIntList[4]));
		conditionResultDataEntity.setExNoFinDataToNoResultCount(String.valueOf(updConditionCountIntList[5]));
		conditionResultDataEntity.setGoalDelete(String.valueOf(updConditionCountIntList[6]));
		conditionResultDataEntity.setAlterTargetMailAnonymous(String.valueOf(updConditionCountIntList[7]));
		conditionResultDataEntity.setAlterTargetMailFail(String.valueOf(updConditionCountIntList[8]));
		conditionResultDataEntity.setNoResultCount(String.valueOf(updConditionCountIntList[9]));
		conditionResultDataEntity.setErrData(String.valueOf(updConditionCountIntList[10]));
		conditionResultDataEntity.setConditionData(condition);
		conditionResultDataEntity.setHash(hash);

		String messageCd = "";
		boolean errFlg = false;
		int result = -1;
		if (updFlg) {
			messageCd = "更新";
			result = this.conditionResultDataRepository.update(conditionResultDataEntity);
			if (result != 1) {
				errFlg = true;
				messageCd = "更新エラー";
			}
			fillChar += (", BM_M002 更新件数: 1件");
		} else {
			messageCd = "新規登録";
			result = this.conditionResultDataRepository.insert(conditionResultDataEntity);
			if (result != 1) {
				errFlg = true;
				messageCd = "新規登録エラー";
			}
			fillChar += (", BM_M002 登録件数: 1件");
		}

		if (errFlg) {
			this.rootCauseWrapper.throwUnexpectedRowCount(
			        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
			        messageCd,
			        1, result,
			        null
			    );
		}

		// 途中ログ
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar);
	}

}
