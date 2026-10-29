package dev.web.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a003.CountryLeagueDTO;
import dev.web.api.bm_a006.ForceAdminAPIService;
import dev.web.api.bm_a006.ForceAdminRequest;
import dev.web.api.bm_a006.ForceAdminResponse;
import lombok.RequiredArgsConstructor;

/**
 * 管理者国リーグ強制制御画面用コントローラー
 * @author shiraishitoshio
 *
 */
@RestController
@RequestMapping("/api/admin/force")
@RequiredArgsConstructor
public class AdminCountryLeagueForceAdminController {

	private final ForceAdminAPIService service;

    /**
     * 画面の一覧（country_league_master の全チーム。delFlg は admin_favorites_master で非表示か）
     * GET /api/admin/force/teams
     */
    @GetMapping("/teams")
    public ResponseEntity<List<CountryLeagueDTO>> teams() {
        return ResponseEntity.ok(service.findTeams());
    }

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
