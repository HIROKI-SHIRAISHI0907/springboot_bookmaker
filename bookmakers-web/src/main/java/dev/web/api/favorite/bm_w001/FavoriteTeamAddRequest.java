package dev.web.api.favorite.bm_w001;

import lombok.Data;

/**
 * お気に入りチームの追加（POST /v1/api/favorite-edit/teams）
 * @author shiraishitoshio
 */
@Data
public class FavoriteTeamAddRequest {

	private String country;

	private String league;

	private String team;
}
