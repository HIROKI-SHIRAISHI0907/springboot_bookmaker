package dev.web.api.bm_u003;

import java.util.List;

import lombok.Data;

//返却DTO（フィルタ用）
@Data
public class FavoriteScopeResponse {

	/** お気に入り未登録 */
	private boolean allowAll;

	/** 許可国 */
    private List<CountryScope> allowedCountries;

    /** 許可国リーグ */
    private List<LeagueScope> allowedLeaguesByCountry;

    /** 許可国リーグチーム */
    private List<TeamScope> allowedTeamsByCountryLeague;

    /** ★追加：お気に入り済み（チェック復元用） */
    private List<FavoriteItem> selectedItems;

    /** レスポンスコード */
	private String responseCode; // "0"=成功, "9"=失敗 など運用に合わせて

	/** メッセージ */
    private String message;

}
