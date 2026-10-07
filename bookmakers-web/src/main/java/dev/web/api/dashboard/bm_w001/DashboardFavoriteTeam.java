package dev.web.api.dashboard.bm_w001;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * お気に入りチーム（favorites テーブルの1行を詰め替えたもの）
 * @author shiraishitoshio
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardFavoriteTeam {

	/** 国（分からなければ null） */
	private String country;

	/** リーグ（分からなければ null） */
	private String league;

	/** チーム名 */
	private String teamName;
}
