package dev.application.analyze.bm_m003;

import lombok.Getter;
import lombok.Setter;

/**
 * team_statics_data の取得結果を受け渡すための outputDTO。
 *
 * <h2>何を表すクラスか</h2>
 * <p>
 * 既存行の通番（seq）、更新か新規かのフラグ（updFlg）、月別スコアの文字列配列（scoreList）を
 * まとめて返すための入れ物。旧実装で「既存の月別得点を読み込み → 加算 → 保存」する際に
 * 使われていたと思われる。
 * </p>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>現在の BM_M003 では使われていない</b>: {@link TeamMonthlyScoreSummaryStat} /
 *       {@link TeamMonthlyScoreSummaryWriter} は、修正前・修正後ともにこのクラスを参照していない。
 *       プロジェクト全体で参照がなければ削除してよい（削除前に「TeamStaticDataOutputDTO」で全文検索すること）。</li>
 *   <li><b>名前と実体のずれ</b>: コメントの「team_statics_data」は現在のテーブル名
 *       （team_monthly_score_summary）と異なる。旧テーブル名の名残と思われる。</li>
 *   <li><b>scoreList の並び順が暗黙</b>: 何番目が何月かがクラス上に定義されていない。
 *       使い続ける場合は {@link TeamMonthlyScoreSummaryEntity#toMonthArray()} を使う方が安全。</li>
 *   <li><b>配列をそのまま出し入れする</b>: Lombok の getter/setter は配列の参照をそのまま渡すため、
 *       呼び出し側で書き換えると DTO の中身も変わる。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Setter
@Getter
public class TeamStaticDataOutputDTO {

	/**
	 * 更新用通番（既存行の主キー。新規の場合は null）
	 */
	private String seq;

	/**
	 * 更新フラグ（true: 既存行あり→UPDATE / false: 新規→INSERT）
	 */
	private boolean updFlg;

	/**
	 * 読み込みスコアリスト（月別得点数の文字列配列。並び順はクラス上で未定義）
	 */
	private String[] scoreList;

}
