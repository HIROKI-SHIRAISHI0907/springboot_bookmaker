package dev.web.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a036.CupCompetitionRequest;
import dev.web.api.bm_a036.CupCompetitionResponse;
import dev.web.api.bm_a036.CupCompetitionService;
import lombok.RequiredArgsConstructor;

/**
 * 大会出場チーム登録設定（CupCompetitionPage.tsx）
 * <ul>
 *   <li>GET  /api/admin/cup-competition                     大会の一覧</li>
 *   <li>POST /api/admin/cup-competition/save                大会の登録・更新</li>
 *   <li>POST /api/admin/cup-competition/delete              大会の削除（論理削除）</li>
 *   <li>GET  /api/admin/cup-competition/candidates          出場チームの候補（スクレイピング対象のリーグのチーム）</li>
 *   <li>GET  /api/admin/cup-competition/teams?competitionId= 大会の出場チーム</li>
 *   <li>POST /api/admin/cup-competition/teams/add           出場チームの追加</li>
 *   <li>POST /api/admin/cup-competition/teams/remove        出場チームを外す（論理削除）</li>
 *   <li>GET  /api/admin/cup-competition/schedule?competitionId= 日程と出場選手の取得状況</li>
 *   <li>POST /api/admin/cup-competition/schedule/retry      出場選手を取り直す</li>
 *   <li>POST /api/admin/cup-competition/export              大会と出場チームの JSON を出力し直す</li>
 * </ul>
 * @author shiraishitoshio
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/cup-competition")
public class AdminCupCompetitionController {

	private final CupCompetitionService service;

	@GetMapping
	public ResponseEntity<List<CupCompetitionResponse.Competition>> competitions() {
		return ResponseEntity.ok(service.getCompetitions());
	}

	@PostMapping("/save")
	public ResponseEntity<CupCompetitionResponse.Result> save(@RequestBody CupCompetitionRequest.Save req) {
		return ResponseEntity.ok(service.save(req));
	}

	@PostMapping("/delete")
	public ResponseEntity<CupCompetitionResponse.Result> delete(@RequestBody CupCompetitionRequest.Delete req) {
		return ResponseEntity.ok(service.delete(req));
	}

	@GetMapping("/candidates")
	public ResponseEntity<List<CupCompetitionResponse.CandidateCountry>> candidates() {
		return ResponseEntity.ok(service.getCandidates());
	}

	@GetMapping("/teams")
	public ResponseEntity<List<CupCompetitionResponse.Team>> teams(
			@RequestParam(name = "competitionId") Integer competitionId) {
		return ResponseEntity.ok(service.getTeams(competitionId));
	}

	@PostMapping("/teams/add")
	public ResponseEntity<CupCompetitionResponse.Result> addTeams(@RequestBody CupCompetitionRequest.AddTeams req) {
		return ResponseEntity.ok(service.addTeams(req));
	}

	@PostMapping("/teams/remove")
	public ResponseEntity<CupCompetitionResponse.Result> removeTeams(
			@RequestBody CupCompetitionRequest.RemoveTeams req) {
		return ResponseEntity.ok(service.removeTeams(req));
	}

	@GetMapping("/schedule")
	public ResponseEntity<CupCompetitionResponse.Schedule> schedule(
			@RequestParam(name = "competitionId", required = false) Integer competitionId) {
		return ResponseEntity.ok(service.getSchedule(competitionId));
	}

	@PostMapping("/schedule/retry")
	public ResponseEntity<CupCompetitionResponse.Result> retry(@RequestBody CupCompetitionRequest.Retry req) {
		return ResponseEntity.ok(service.retry(req));
	}

	@PostMapping("/export")
	public ResponseEntity<CupCompetitionResponse.Result> export() {
		return ResponseEntity.ok(service.export());
	}
}
