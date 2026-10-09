package dev.web.api.favorite.bm_w001;

import lombok.Data;

/**
 * 国・リーグ（絞り込み用）
 * @author shiraishitoshio
 */
@Data
public class FavoriteLeagueDTO {

	private String country;

	private String league;

	private int teamCount;
}
