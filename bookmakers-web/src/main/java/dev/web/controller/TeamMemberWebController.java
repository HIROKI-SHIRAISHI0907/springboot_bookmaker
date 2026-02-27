package dev.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a004.TeamMemberDTO;
import dev.web.api.bm_a004.TeamMemberRequest;
import dev.web.api.bm_a004.TeamMemberResponse;
import dev.web.api.bm_a004.TeamMemberSearchCondition;
import dev.web.api.bm_a004.TeamMemberService;
import lombok.RequiredArgsConstructor;

/**
 * TeamMember取得用
 * @author shiraishitoshio
 *
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class TeamMemberWebController {

    private final TeamMemberService service;

    /**
     * team_member_master の未登録項目を更新する（未登録項目のみ反映）。
     *
     * PATCH /api/team-member-master
     */
    @PatchMapping("/team-member-master")
    public ResponseEntity<TeamMemberResponse> patchTeamMember(
            @RequestBody TeamMemberRequest req) {

    	TeamMemberResponse res = service.patchTeamMember(req);

        HttpStatus status = switch (res.getResponseCode()) {
            case "200" -> HttpStatus.OK;
            case "400" -> HttpStatus.BAD_REQUEST;
            case "404" -> HttpStatus.NOT_FOUND;
            case "409" -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        return ResponseEntity.status(status).body(res);
    }

    /**
     * team_member_master を全件取得する。
     *
     * GET /api/team-member-master
     *
     * @return team_member_master 一覧
     */
    @GetMapping("/team-member-master")
    public ResponseEntity<List<TeamMemberDTO>> getAllTeamMembers() {
        return ResponseEntity.ok(service.getAll());
    }

    /**
     * team_member_master をURLから取得する。
     *
     * GET /api/team-member-master/{teamEnglish}/{teamHash}
     *
     * @return team_member_master 一覧
     */
    @GetMapping("/team-member-master/{teamEnglish}/{teamHash}")
    public ResponseEntity<List<TeamMemberDTO>> getEachTeamMembers(
    		@PathVariable String teamEnglish,
            @PathVariable String teamHash) {
        return ResponseEntity.ok(service.patchWebTeamMember(teamEnglish, teamHash));
    }

    /**
     * team_member_master を条件指定で取得する。
     *
     * GET /api/team-member-master/search
     */
    @GetMapping("/team-member-master/search")
    public ResponseEntity<List<TeamMemberDTO>> search(
            @ModelAttribute TeamMemberSearchCondition cond) {

        return ResponseEntity.ok(service.search(cond));
    }

    @PostMapping("/team-member-master/update")
	public ResponseEntity<TeamMemberResponse> update(@RequestBody TeamMemberRequest dto) {
		return ResponseEntity.ok(service.update(dto));
	}
}
