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
 * <h2>何を導出するクラスか</h2>
 * <p>
 * 得点予測モデルの「通知条件（条件分岐データ）」ごとの的中実績を集計する。
 * </p>
 * <p>
 * モデルは試合中の各時点（times＝スナップショット行）で「この先に得点が入るか」を判定し、
 * 通知対象ならメールを送る。その後、実際に得点が入れば「メール通知成功」、入らなければ
 * 「メール通知失敗」として、その時点の行の judge に記録される。
 * 本クラスは judge を times 単位（行単位）で数え、通知条件ファイルのハッシュ値ごとに
 * condition_result_data テーブルへ累積保存する。
 * 条件を変更するとハッシュが変わり、別の行として集計が始まるため、
 * 「どの通知条件がどれだけ当たったか（成功数 ÷ (成功数 + 失敗数)）」を条件ごとに比較できる。
 * </p>
 *
 * <h3>集計する件数（11項目、添字順）</h3>
 * <ol start="0">
 *   <li>メール通知対象数（MAIL_TARGET）</li>
 *   <li>メール非通知対象数（MAIL_ANONYMOUS_TARGET）</li>
 *   <li>メール通知成功数（MAIL_TARGET_SUCCESS）</li>
 *   <li>メール通知失敗数（MAIL_TARGET_FAIL）</li>
 *   <li>前メール通知→結果不明数（MAIL_TARGET_TO_RESULT_UNKNOWN）</li>
 *   <li>前終了済データ無し→結果不明数（MAIL_FIN_NO_DATA_TO_RESULT_UNKNOWN）</li>
 *   <li>ゴール取り消し（GOAL_DELETE）</li>
 *   <li>ゴール取り消しによる通知/非通知変更</li>
 *   <li>ゴール取り消しによる成功/失敗変更</li>
 *   <li>結果不明（上記いずれにも当てはまらない judge）</li>
 *   <li>予期せぬエラー（entity が null、または judge が null）</li>
 * </ol>
 *
 * <h3>処理の流れ</h3>
 * <ol>
 *   <li>{@link BmM002ConditionResultDataBean#init()} で条件ファイルを読み、ハッシュ値と既存件数を取得</li>
 *   <li>全リーグ・全試合・全スナップショット行の judge を数え、既存件数に加算</li>
 *   <li>{@link ConditionResultDataWriter#save} で INSERT（初回）または UPDATE（2回目以降）</li>
 * </ol>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>judge が null の行は「予期せぬエラー」に数えられる</b>:
 *       仕様上は全 times（全行）に judge が入る（実データでも null なしを確認済み）。
 *       null が出た場合は本当に異常なデータとして IDX_UNEXPECTED_ERROR に加算する。</li>
 *   <li><b>times 単位（行単位）の件数である</b>: 1試合で複数の時点が通知対象になれば、
 *       その回数分数えられる（仕様どおり）。「試合単位の的中率」とは異なる点に注意。</li>
 *   <li><b>【重要】再実行で二重計上される</b>: 既存件数に今回分を足す累積方式で、処理済みデータの記録がない。
 *       同じ入力で再実行すると、その分がもう一度加算される。</li>
 *   <li><b>未定義の judge は黙って「結果不明」になる</b>: 定数の追加漏れや表記揺れ（全角/半角・空白）があっても
 *       エラーにならず RESULT_UNKNOWN に入るため、気づきにくい。</li>
 *   <li><b>ログ量</b>: 全行で debugInfoLog を出すため、データ量に比例してログが増える。</li>
 *   <li><b>件数配列の順序</b>: 【修正】Bean・Writer の読み書きは
 *       {@link ConditionResultDataEntity#toCountArray()} / {@link ConditionResultDataEntity#applyCounts(Integer[])}
 *       に集約した。本クラスの IDX_* 定数だけは Entity の並び順と一致させる必要がある。</li>
 *   <li><b>条件ファイルが読めない場合</b>: 【修正】"dummy" 条件として集計せず、Bean の init() で例外になり処理が止まる。</li>
 *   <li><b>Bean がシングルトンで状態を持つ</b>: 同時に2本実行すると、ハッシュ・件数・更新フラグが混ざる。</li>
 * </ul>
 *
 * @author shiraishitoshio
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

	/** 件数配列サイズ（【修正】Entity の定義を参照） */
	private static final int COUNT_SIZE = ConditionResultDataEntity.COUNT_SIZE;

	/** 添字定義（Bean の conditionCountList、Writer の設定順と一致させること） */
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
	 * 条件分岐データごとの judge 件数を集計し、condition_result_data に保存する。
	 *
	 * <p>懸念点:</p>
	 * <ul>
	 *   <li>judge が null の行は「予期せぬエラー」に加算される（全 times に judge が入る前提）。</li>
	 *   <li>times（スナップショット行）単位で数える。1試合に通知対象の時点が複数あれば、その回数分数える。</li>
	 *   <li>既存件数への累積のため、同じデータで再実行すると二重計上になる。</li>
	 *   <li>bean.init() で条件ファイルが読めない場合、例外で処理全体が止まる（誤った条件で集計しないため）。</li>
	 * </ul>
	 *
	 * @param entities 国・リーグ → 試合 → スナップショット行 のマップ（null 可）
	 */
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";

		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		try {
			// 初期化（条件ファイル読込・ハッシュ算出・既存件数取得）
			bean.init();

			// 既存件数（DB値）
			Integer[] conditionCountIntList = toConditionCountArray(bean.getConditionCountList());

			// 今回分を加算していく配列（ログで before/after を出すため別に保持）
			Integer[] updConditionCountIntList = conditionCountIntList.clone();

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

							// 懸念: 全行でログ出力するためログ量が多い
							this.manageLoggerComponent.debugInfoLog(
									PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, entity.getFilePath());

							String judge = entity.getJudge();

							// 懸念: judge 未設定の行もエラー扱いになる（仕様確認が必要）
							if (judge == null) {
								updConditionCountIntList[IDX_UNEXPECTED_ERROR]++;
								continue;
							}

							// 懸念: 未定義・表記揺れの judge は黙って「結果不明」になる
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
	 * judge の文字列から件数配列の添字への変換Mapを生成する。
	 *
	 * <p>懸念点: BookMakersCommonConst に judge の種類を追加した場合、ここにも追加しないと
	 * RESULT_UNKNOWN として数えられる。</p>
	 *
	 * @return 変更不可の判定→添字変換Map
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
	 * DB から取得した件数（文字列配列）を Integer 配列（COUNT_SIZE 固定長）に変換する。
	 *
	 * <p>懸念点: 配列が COUNT_SIZE より短い場合、足りない項目は 0 になる。
	 * DB の値が壊れていて数値にできない場合も 0 に戻るため、それまでの累積件数が失われる
	 * （【修正】その場合はログを出す）。</p>
	 *
	 * @param countList 文字列配列（null 可）
	 * @return Integer 配列（要素はすべて非 null）
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
	 * 件数文字列を整数に変換する。
	 *
	 * <p>null・空は 0。数値以外は 0 を返し、【修正】警告ログを出す（累積件数のリセットに気づけるように）。</p>
	 *
	 * @param value 件数文字列
	 * @return 件数（変換できない場合は 0）
	 */
	private Integer parseCount(String value) {
		if (value == null || value.isBlank()) {
			return 0;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			// 【修正】累積件数が 0 に戻ることに気づけるよう警告ログを出す
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, "parseCount", null,
					"condition_result_data の件数が数値ではないため 0 として扱います: value=" + value);
			return 0;
		}
	}

	/**
	 * 更新前後の件数をログ用の文字列にまとめる。
	 *
	 * @param conditionCountIntList 更新前件数
	 * @param updConditionCountIntList 更新後件数
	 * @return 「項目名: {前} → {後}, ...」形式の文字列
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
