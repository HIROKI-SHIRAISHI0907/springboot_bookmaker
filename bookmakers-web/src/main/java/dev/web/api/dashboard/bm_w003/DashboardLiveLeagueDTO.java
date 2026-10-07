package dev.web.api.dashboard.bm_w003;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ライブのリーグごとのまとまり
 * @author shiraishitoshio
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardLiveLeagueDTO {

	/** 「国 / リーグ」 */
	private String leagueLabel;

	/** 国（絞り込み用） */
	private String country;

	private List<DashboardLiveMatchDTO> matches;
}
