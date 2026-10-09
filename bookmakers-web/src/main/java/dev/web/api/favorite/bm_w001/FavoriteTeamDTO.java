package dev.web.api.favorite.bm_w001;

import lombok.Data;

/**
 * お気に入りチーム（favorites の level = 3）
 * @author shiraishitoshio
 */
@Data
public class FavoriteTeamDTO {

	/** favorites.id（削除用） */
	private Long id;

	private String country;

	private String league;

	private String team;
}
