package dev.web.api.bm_u003;

import lombok.Data;

/**
 * FavoriteRequestAPIリクエスト
 * @author shiraishitoshio
 *
 */
@Data
public class FavoriteRequest {

	/** ユーザーID */
	private Integer userId;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** チーム */
	private String team;

}
