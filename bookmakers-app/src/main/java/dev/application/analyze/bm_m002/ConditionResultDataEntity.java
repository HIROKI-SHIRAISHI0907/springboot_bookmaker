package dev.application.analyze.bm_m002;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * condition_result_data テーブルに対応するエンティティ。
 *
 * <h2>何を表すクラスか</h2>
 * <p>
 * 1行 = 1つの「条件分岐データ（得点予測モデルの通知条件ファイル）」。
 * その条件でモデルが times 単位に判定した結果（judge）の種類ごとの累積件数を持つ。
 * 条件ファイルの内容そのもの（conditionData）と、そのハッシュ（hash）で条件を識別する。
 * </p>
 *
 * <h2>件数項目の並び順（添字）</h2>
 * <p>
 * 件数11項目の並び順は {@link #toCountArray()} / {@link #applyCounts(Integer[])} の1か所で定義する。
 * Bean・Writer はこのメソッドを通して読み書きするため、項目を追加・並べ替える場合は
 * この2メソッドと {@link #COUNT_SIZE}、および {@link ConditionResultDataStat} の IDX_* 定数を直すこと。
 * </p>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>件数がすべて String 型</b>: DB に数値以外が入っていると、集計側で 0 として扱われる
 *       （{@link ConditionResultDataStat} で警告ログを出す）。</li>
 *   <li><b>dataSeq が使われていない</b>: 本パッケージ内では設定・参照されていない。</li>
 *   <li><b>conditionData に条件ファイル全文を保存する</b>: ファイルが大きいと行サイズも大きくなる。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ConditionResultDataEntity extends MetaEntity {

	/** 件数項目の数 */
	public static final int COUNT_SIZE = 11;

	/** まとめ通番（本パッケージ内では未使用） */
	private String dataSeq;

	/** [0] メール通知対象数 */
	private String mailTargetCount;

	/** [1] メール非通知対象数 */
	private String mailAnonymousTargetCount;

	/** [2] メール通知成功数 */
	private String mailTargetSuccessCount;

	/** [3] メール通知失敗数 */
	private String mailTargetFailCount;

	/** [4] 前メール通知失敗結果不明数 */
	private String exMailTargetToNoResultCount;

	/** [5] 前終了済データ無し結果不明数 */
	private String exNoFinDataToNoResultCount;

	/** [6] ゴール取り消し */
	private String goalDelete;

	/** [7] ゴール取り消しによる通知非通知変更 */
	private String alterTargetMailAnonymous;

	/** [8] ゴール取り消しによる成功失敗変更 */
	private String alterTargetMailFail;

	/** [9] 結果不明 */
	private String noResultCount;

	/** [10] 予期せぬエラーデータ数 */
	private String errData;

	/** 条件分岐データ（条件ファイルの全文） */
	private String conditionData;

	/** ハッシュ値データ（conditionData の Base64 エンコード済みダイジェスト。行の識別キー） */
	private String hash;

	/**
	 * 【追加】件数11項目を添字順の配列で返す（並び順の定義はここだけ）。
	 * @return 件数の文字列配列（長さ {@link #COUNT_SIZE}。要素は null の可能性あり）
	 */
	public String[] toCountArray() {
		return new String[] {
				mailTargetCount,
				mailAnonymousTargetCount,
				mailTargetSuccessCount,
				mailTargetFailCount,
				exMailTargetToNoResultCount,
				exNoFinDataToNoResultCount,
				goalDelete,
				alterTargetMailAnonymous,
				alterTargetMailFail,
				noResultCount,
				errData
		};
	}

	/**
	 * 【追加】添字順の件数配列を各項目に設定する（{@link #toCountArray()} と同じ並び順）。
	 * @param counts 件数配列（長さ {@link #COUNT_SIZE}）
	 * @throws IllegalArgumentException 配列が null または長さが一致しない場合
	 */
	public void applyCounts(Integer[] counts) {
		if (counts == null || counts.length != COUNT_SIZE) {
			throw new IllegalArgumentException(
					"件数配列の長さが不正です: " + (counts == null ? "null" : counts.length)
							+ " (期待値: " + COUNT_SIZE + ")");
		}
		this.mailTargetCount = String.valueOf(counts[0]);
		this.mailAnonymousTargetCount = String.valueOf(counts[1]);
		this.mailTargetSuccessCount = String.valueOf(counts[2]);
		this.mailTargetFailCount = String.valueOf(counts[3]);
		this.exMailTargetToNoResultCount = String.valueOf(counts[4]);
		this.exNoFinDataToNoResultCount = String.valueOf(counts[5]);
		this.goalDelete = String.valueOf(counts[6]);
		this.alterTargetMailAnonymous = String.valueOf(counts[7]);
		this.alterTargetMailFail = String.valueOf(counts[8]);
		this.noResultCount = String.valueOf(counts[9]);
		this.errData = String.valueOf(counts[10]);
	}

}
