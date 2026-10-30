package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a035.SetupStatusResponse;
import dev.web.api.bm_a035.SetupStatusService;
import lombok.RequiredArgsConstructor;

/**
 * 管理画面の未対応状況（AdminLayout.tsx の入口チェック）
 * <ul>
 *   <li>GET /api/admin/setup-status  新規シーズン・新規チームの未確認、チームの重複など</li>
 * </ul>
 * @author shiraishitoshio
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminSetupStatusController {

	private final SetupStatusService service;

	@GetMapping("/setup-status")
	public ResponseEntity<SetupStatusResponse> status() {
		return ResponseEntity.ok(service.getStatus());
	}
}
