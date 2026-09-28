package dev.application.analyze.bm_m002;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.ConditionResultDataRepository;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * condition_result_data の書き込み専用 Writer。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * {@link ConditionResultDataStat} が集計した11項目の件数を {@link ConditionResultDataEntity} に詰め、
 * 既存行があれば UPDATE、なければ INSERT する。1件でない結果になった場合は例外にする。
 * </p>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>件数配列の並び順</b>: 【修正】{@link ConditionResultDataEntity#applyCounts(Integer[])} に集約。
 *       長さが11でなければ IllegalArgumentException。{@link ConditionResultDataStat} の IDX_* と
 *       Entity の並び順は一致させること。</li>
 *   <li><b>同時実行で重複行ができる可能性</b>: INSERT/UPDATE の判断は Bean の init() 時点の検索結果。
 *       2本同時に初回実行すると、両方が INSERT して同じハッシュの行が2件できる
 *       （hash に一意制約がなければ）。</li>
 *   <li><b>UPDATE の条件</b>: update SQL が hash を条件にしている前提。同じハッシュの行が複数あると
 *       件数が2以上になり、例外になる。</li>
 *   <li><b>メッセージコードに日本語文字列を渡している</b>: debugInfoLog / throwUnexpectedRowCount の
 *       messageCd 引数に「更新」「新規登録エラー」などを渡している。メッセージコードの定数を想定した
 *       引数であれば、ログのメッセージが正しく引けない可能性がある。</li>
 *   <li><b>ログの件数が固定</b>: 「登録件数: 1件」は実際の結果件数ではなく固定文字列。</li>
 * </ul>
 */
@Service
public class ConditionResultDataWriter {

	private static final String PROJECT_NAME = ConditionResultDataWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();
	private static final String CLASS_NAME = ConditionResultDataWriter.class.getName();

	@Autowired
	private ConditionResultDataRepository conditionResultDataRepository;

	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 集計結果を condition_result_data に登録または更新する。
	 *
	 * <p>懸念点:</p>
	 * <ul>
	 *   <li>updConditionCountIntList は長さ11・添字順固定が前提（ずれると別列に保存）。</li>
	 *   <li>INSERT/UPDATE の判断は呼び出し元（Bean の init 時点）の結果に依存し、
	 *       同時実行では重複 INSERT の可能性がある。</li>
	 *   <li>結果件数が1件以外なら throwUnexpectedRowCount で例外（トランザクションはロールバック）。</li>
	 * </ul>
	 *
	 * @param updFlg true: UPDATE / false: INSERT
	 * @param updConditionCountIntList 更新後の件数（長さ11）
	 * @param condition 条件分岐データ（全文）
	 * @param hash 条件分岐データのハッシュ値
	 * @param fillChar ログ用の件数文字列
	 */
	@Transactional
	public void save(boolean updFlg, Integer[] updConditionCountIntList,
			String condition, String hash, String fillChar) {
		final String METHOD_NAME = "save";

		// 【修正】件数の並び順は ConditionResultDataEntity#applyCounts に集約（長さ不正は例外）
		ConditionResultDataEntity conditionResultDataEntity = new ConditionResultDataEntity();
		conditionResultDataEntity.applyCounts(updConditionCountIntList);
		conditionResultDataEntity.setConditionData(condition);
		conditionResultDataEntity.setHash(hash);

		if (fillChar == null) {
			fillChar = "";
		}

		String messageCd;
		boolean errFlg = false;
		int result;

		if (updFlg) {
			messageCd = "更新";
			result = this.conditionResultDataRepository.update(conditionResultDataEntity);
			if (result != 1) {
				errFlg = true;
				messageCd = "更新エラー";
			}
			fillChar += ", BM_M002 更新件数: 1件";
		} else {
			messageCd = "新規登録";
			result = this.conditionResultDataRepository.insert(conditionResultDataEntity);
			if (result != 1) {
				errFlg = true;
				messageCd = "新規登録エラー";
			}
			fillChar += ", BM_M002 登録件数: 1件";
		}

		if (errFlg) {
			this.rootCauseWrapper.throwUnexpectedRowCount(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					messageCd,
					1, result,
					null);
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar);
	}
}
