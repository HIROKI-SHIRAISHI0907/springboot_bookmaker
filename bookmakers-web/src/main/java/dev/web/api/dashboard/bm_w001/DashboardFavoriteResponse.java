package dev.web.api.dashboard.bm_w001;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * お気に入りのレスポンス（GET /v1/api/dashboard/favorites）
 * @author shiraishitoshio
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardFavoriteResponse {

	private List<DashboardFavoriteItemDTO> items;
}
