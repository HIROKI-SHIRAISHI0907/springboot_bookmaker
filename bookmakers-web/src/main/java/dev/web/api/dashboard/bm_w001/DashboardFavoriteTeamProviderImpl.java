package dev.web.api.dashboard.bm_w001;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.web.api.favorite.bm_w001.FavoriteTeamDTO;
import dev.web.repository.user.FavoriteRepository;
import dev.web.repository.user.LoginUserResolverRepository;
import lombok.RequiredArgsConstructor;

/**
 * お気に入りチームの取得（favorites の level = 3）
 * ※ パッケージは DashboardFavoriteTeamProvider と同じにすること（今の構成に合わせて変更）
 * @author shiraishitoshio
 */
@Component
@RequiredArgsConstructor
public class DashboardFavoriteTeamProviderImpl implements DashboardFavoriteTeamProvider {

	private final FavoriteRepository favoriteRepository;

	private final LoginUserResolverRepository loginUserResolver;

	@Override
	public List<DashboardFavoriteTeam> findByUserId(String userId) {
		List<DashboardFavoriteTeam> list = new ArrayList<>();
		// JWT の名前はメールアドレスのことがあるので、users.id に変換する
		Long id = this.loginUserResolver.toUserId(userId);
		if (id == null) {
			return list;
		}
		for (FavoriteTeamDTO f : this.favoriteRepository.findTeams(id)) {
			DashboardFavoriteTeam t = new DashboardFavoriteTeam();
			t.setCountry(f.getCountry());
			t.setLeague(f.getLeague());
			t.setTeamName(f.getTeam());
			list.add(t);
		}
		return list;
	}
}