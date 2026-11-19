package dev.web.api.bm_a005;

import lombok.Data;

/**
 * AllLeagueDTO
 * @author shiraishitoshio
 *
 */
@Data
public class AllLeagueDTO {

	/** ID */
	private String id;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** サブリーグ */
	private String subLeague;

	/** 論理フラグ */
	private String logicFlg;

	/** 表示フラグ */
	private String dispFlg;


	/** 登録日時（日本時間 yyyy-MM-dd HH:mm:ss。2026-11-20 追加） */
	private String registerTime;

	/** 新しく入ってきたリーグか（登録から NEW_LEAGUE_DAYS 日以内。2026-11-20 追加） */
	private Boolean newLeague;
}
