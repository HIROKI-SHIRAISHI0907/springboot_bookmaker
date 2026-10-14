package dev.application.analyze.common.error;

import java.sql.Timestamp;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * analyze_error_match テーブルに対応するエンティティ（統計処理で登録できなかった試合の記録）。
 *
 * <h2>何を表すクラスか</h2>
 * <p>
 * 1行 = 1つの BM × エラー種別 × 試合（または国・リーグ、チーム）× 原因の項目（error_field）。
 * シーズンが取得できない等の理由で統計テーブルに登録できなかった試合を記録し、画面で「何が・なぜ登録できなかったか」を確認する。
 * </p>
 * <ul>
 *   <li>同じ BM・エラー種別・試合のエラーが繰り返し起きた場合は1行にまとめ、occurredCount を増やし lastOccurredAt を更新する
 *       （ストリーミングで同じ試合が何度流れてきても行が増え続けない）。</li>
 *   <li>画面で対応済みにした行（resolvedFlg = true）で同じエラーがまた起きた場合は、未対応（false）に戻す。</li>
 *   <li>キーにあたる項目（国・リーグ・カテゴリ・チーム名）は、値が無い場合は空文字で持つ（一意制約のため NULL にしない）。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class AnalyzeErrorMatchEntity extends MetaEntity {

	/** seq（&lt;発生年&gt;-&lt;6桁枝番&gt;。seq_counter で採番） */
	private String seq;

	/** BM 番号（例: BM_M004） */
	private String bmNumber;

	/** エラー種別（{@link AnalyzeErrorType} の名前） */
	private String errorType;

	/** エラー内容 */
	private String errorMessage;

	/** 国（無ければ空文字） */
	private String country;

	/** リーグ（無ければ空文字） */
	private String league;

	/** 元のキー（「国: リーグ - ラウンドN」。無ければ空文字） */
	private String dataCategory;

	/** ホームチーム（チーム単位の BM はそのチーム。無ければ空文字） */
	private String homeTeamName;

	/** アウェーチーム（無ければ空文字） */
	private String awayTeamName;

	/** マッチID（参照用） */
	private String matchId;

	/** シーズン（取得できていれば） */
	private String season;

	/** 【追加】エラーの原因になった項目名（カンマ区切り。例: homeScore / country,league。無ければ空文字。一意キーの一部） */
	private String errorField;

	/** 【追加】原因の項目のそのときの値（「項目名=値」を "; " 区切り） */
	private String errorValue;

	/** 補足（H/A・年など、キー以外の情報） */
	private String detail;

	/** 例外クラス名 */
	private String exceptionClass;

	/** スタックトレース（先頭のみ） */
	private String stackTrace;

	/** 発生回数 */
	private Integer occurredCount;

	/** 最初の発生日時 */
	private Timestamp firstOccurredAt;

	/** 最後の発生日時 */
	private Timestamp lastOccurredAt;

	/** 対応済みか（画面で更新） */
	private Boolean resolvedFlg;

	/** 対応日時（画面で更新） */
	private Timestamp resolvedAt;

	/** 対応者（画面で更新） */
	private String resolvedBy;

	/** メモ（画面で更新） */
	private String note;
}
