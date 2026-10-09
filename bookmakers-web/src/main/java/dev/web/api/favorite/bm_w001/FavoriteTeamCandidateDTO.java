package dev.web.api.favorite.bm_w001;

import lombok.Data;

/**
 * お気に入りに追加できるチーム（country_league_master）
 * @author shiraishitoshio
 */
@Data
public class FavoriteTeamCandidateDTO {

	private String country;

	private String league;

	private String team;

	/** 登録済みなら favorites.id（未登録は null） */
	private Long favoriteId;
}
