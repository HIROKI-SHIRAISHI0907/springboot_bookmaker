package dev.application.analyze.interf;

/**
 * 国・リーグから「現在のシーズン」を返すインターフェース。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * country_league_season_master の season 項目を返す。seq の採番（{@link SeqNumberingService}）で使う。
 * </p>
 *
 * <h2>実装について（未連携）</h2>
 * <p>
 * country_league_season_master との連携は後日。実装クラスでは次の点を決めること。
 * </p>
 * <ul>
 *   <li>同じ国・リーグにシーズンが複数行ある場合、どれを「現在」とするか（開始日・終了日・フラグなど）。</li>
 *   <li>マスタに見つからない場合の扱い（例外にするのが安全。空のシーズンで採番すると
 *       {@link SeqNumberingService} が例外にする）。</li>
 *   <li>1回の実行中に何度も呼ばれるため、実行単位でのキャッシュを検討すること。</li>
 * </ul>
 */
public interface SeasonResolverIF {

	/**
	 * 国・リーグの現在のシーズンを返す。
	 *
	 * @param country 国
	 * @param league リーグ
	 * @return シーズン（例: "2025-2026"）
	 * @throws IllegalStateException マスタに見つからない場合
	 */
	String resolveSeason(String country, String league);
}
