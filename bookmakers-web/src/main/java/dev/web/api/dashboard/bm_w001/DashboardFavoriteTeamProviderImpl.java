package dev.web.api.dashboard.bm_w001;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * お気に入りチームの取得（仮実装）。
 * <p>
 * TODO: favorites テーブルの Repository を注入して、userId のお気に入りチームを
 * {@link DashboardFavoriteTeam}（国・リーグ・チーム名）に詰め替えて返す。
 * </p>
 * @author shiraishitoshio
 *
 */
@Component
public class DashboardFavoriteTeamProviderImpl implements DashboardFavoriteTeamProvider {

	@Override
	public List<DashboardFavoriteTeam> findByUserId(String userId) {
		return new ArrayList<>();
	}
}
