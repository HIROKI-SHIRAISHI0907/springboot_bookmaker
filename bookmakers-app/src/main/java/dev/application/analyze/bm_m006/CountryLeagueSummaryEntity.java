package dev.application.analyze.bm_m006;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * country_league_summary テーブルに対応するエンティティ（BM_M006 国・リーグ別 処理件数）。
 *
 * <h2>何を表すクラスか</h2>
 * <p>
 * 1行 = 国 × リーグ × シーズン。csvCount はその国・リーグのデータを処理した回数の合計
 * （処理のたびに、その回に流れてきた試合数を加算する。同じ試合でも流れてくるたびに数える）。
 * </p>
 *
 * <h2>修正履歴</h2>
 * <ul>
 *   <li>id → seq（「&lt;シーズン&gt;-&lt;6桁枝番&gt;」、seq_counter 採番）。</li>
 *   <li>season を追加（国 × リーグ × シーズン単位に変更）。</li>
 *   <li>未使用の dataCount を削除。</li>
 *   <li>csvCount を String → Integer（数値以外が入って黙って 0 に戻る問題の解消）。</li>
 *   <li>クラスコメントの「output_通番.xlsx から読み込んだ…」は古い説明のため削除。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class CountryLeagueSummaryEntity extends MetaEntity {

	/** seq（主キー。「<シーズン>-<6桁枝番>」。Writer で seq_counter から採番する） */
	private String seq;

	/** シーズン（country_league_season_master.season_year） */
	private String season;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** 処理した回数の合計（UPSERT 時は「今回の加算分」を入れる） */
	private Integer csvCount;

}