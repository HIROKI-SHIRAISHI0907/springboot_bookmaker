package dev.web.api.dashboard.bm_w001;

import java.util.List;

/**
 * お気に入りチームの取得（favorites テーブルとの接続口）。
 * <p>
 * 既存の favorites の Repository を使う実装に差し替えること（{@link DashboardFavoriteTeamProviderImpl}）。
 * </p>
 * @author shiraishitoshio
 *
 */
public interface DashboardFavoriteTeamProvider {

	/**
	 * ユーザーのお気に入りチーム
	 *
	 * @param userId JWT から取ったユーザー（{@link DashboardAuthResolver#currentUserId()}）
	 * @return お気に入りチーム（無ければ空）
	 */
	List<DashboardFavoriteTeam> findByUserId(String userId);
}
