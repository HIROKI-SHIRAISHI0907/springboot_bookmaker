package dev.batch.bm_b015;

import lombok.Data;

/**
 * country_league_season_master の登録・更新の値
 * <p>更新では null の項目は変更しない（CSV で空欄の列）。日時は「yyyy-MM-ddTHH:mm:ss+09:00」。</p>
 * @author shiraishitoshio
 */
@Data
public class SeasonUploadParam {
	private Long id;
	private String country;
	private String league;
	private String seasonYear;
	private String startSeasonDate;
	private String endSeasonDate;
	private String round;
	private String path;
	private String icon;
	private String validFlg;
	/** register_id / update_id */
	private String user;
}
