package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a005.ForceAdminAPIService;
import dev.web.api.bm_a005.ForceAdminRequest;
import dev.web.api.bm_a005.ForceAdminResponse;
import lombok.RequiredArgsConstructor;

/**
 * 管理者国リーグ強制制御画面用コントローラー
 * @author shiraishitoshio
 *
 */
@RestController
@RequestMapping("/api/admin/force")
@RequiredArgsConstructor
public class CountryLeagueForceAdminController {

	private final ForceAdminAPIService service;

    /**
     * 管理者国リーグ強制制御
     * POST /api/admin/force/update/control
     */
    @PostMapping("/update/control")
    public ResponseEntity<ForceAdminResponse> upsert(@RequestBody ForceAdminRequest req) {
        return ResponseEntity.ok(service.upsert(req.getCountry(), req.getLeague(),
        		req.getTeam(), req.getDelFlg()));
    }

}
