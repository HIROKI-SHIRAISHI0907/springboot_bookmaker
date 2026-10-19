package dev.batch.bm_b015;

import lombok.Data;

/**
 * country_league_master の登録・更新の値（更新では null の項目は変更しない）
 * @author shiraishitoshio
 */
@Data
public class TeamUploadParam {
	private Long id;
	private String country;
	private String league;
	private String team;
	private String link;
	/** register_id / update_id */
	private String user;
}
