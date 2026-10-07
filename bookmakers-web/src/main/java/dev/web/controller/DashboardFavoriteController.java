package dev.web.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.dashboard.auth.DashboardAuthResolver;
import dev.web.api.dashboard.bm_w001.DashboardFavoriteResponse;
import dev.web.api.dashboard.bm_w001.DashboardFavoriteService;
import lombok.RequiredArgsConstructor;

/**
 * Dashboard.tsx（トップ画面）: お気に入り
 * <ul>
 *   <li>GET /v1/api/dashboard/favorites  ログイン必須（未ログインは 401）</li>
 * </ul>
 * @author shiraishitoshio
 *
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboard")
public class DashboardFavoriteController {

	private final DashboardFavoriteService service;

	private final DashboardAuthResolver authResolver;

	@GetMapping("/favorites")
	public ResponseEntity<DashboardFavoriteResponse> favorites() {
		String userId = authResolver.currentUserId();
		if (userId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		return ResponseEntity.ok(service.getFavorites(userId));
	}
}
