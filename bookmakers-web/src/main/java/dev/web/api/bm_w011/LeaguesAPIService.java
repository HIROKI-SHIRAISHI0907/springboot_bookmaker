// dev/web/api/bm_w011/LeaguesService.java
package dev.web.api.bm_w011;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import dev.web.repository.bm.LeaguesRepository;
import dev.web.repository.bm.LeaguesRepository.LeagueCountRow;
import dev.web.repository.bm.LeaguesRepository.TeamRow;
import lombok.RequiredArgsConstructor;

/**
 * LeaguesAPI用サービス
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class LeaguesAPIService {

    private final LeaguesRepository repo;

    /** GET /api/leagues (フラット一覧) */
    public List<LeagueFlatItemResponse> getLeaguesFlat() {
        List<LeagueCountRow> rows = repo.findLeagueCounts();
        List<LeagueFlatItemResponse> out = new ArrayList<>();

        for (LeagueCountRow r : rows) {
            LeagueFlatItemResponse item = new LeagueFlatItemResponse();
            item.setCountry(r.country);
            item.setLeague(r.league);
            item.setTeamCount(r.getTeamCount() != null ? r.getTeamCount().intValue() : 0);
            String path = "/" + repo.toPath(r.country) + "/" + repo.toPath(r.league);
            item.setPath(path);
            out.add(item);
        }
        return out;
    }

    /** GET /api/leagues/grouped */
    public List<LeagueGroupedResponse> getLeaguesGrouped() {
        List<LeagueCountRow> rows = repo.findLeagueCounts();

        Map<String, LeagueGroupedResponse> map = new LinkedHashMap<>();

        for (LeagueCountRow r : rows) {
            LeagueGroupedResponse group = map.computeIfAbsent(r.country, c -> {
                LeagueGroupedResponse g = new LeagueGroupedResponse();
                g.setCountry(c);
                g.setLeagues(new ArrayList<>());
                return g;
            });

            LeagueInfoDTO info = new LeagueInfoDTO();
            info.setName(r.league);
            info.setTeamCount(r.getTeamCount() != null ? r.getTeamCount().intValue() : 0);
            info.setPath("/" + repo.toPath(r.country) + "/" + repo.toPath(r.league));
            info.setRoutingPath(r.path);

            group.getLeagues().add(info);
        }

        return new ArrayList<>(map.values());
    }

    /** GET /api/leagues/{country}/{league} country:england, league:premier-league*/
    public TeamsInLeagueResponse getTeamsInLeague(String country, String league) {
        List<TeamRow> rows = repo.findTeamsInLeagueOnSlug(country, league);
        System.out.println(rows);

        TeamsInLeagueResponse res = new TeamsInLeagueResponse();
        res.setCountry(country);
        res.setLeague(league);

        List<TeamItemDTO> teams = new ArrayList<>();
        for (TeamRow r : rows) {
            String[] parsed = repo.parseTeamLink(r.link);
            String english = parsed[0];
            String hash = parsed[1];

            TeamItemDTO t = new TeamItemDTO();
            t.setName(r.team);
            t.setEnglish(english);
            t.setHash(hash);
            t.setLink(r.link);
            String path = "/" + repo.toPath(r.country) + "/" + repo.toPath(r.league);
            t.setPath(path);
            String apiPath = "/api/leagues/" + repo.toPath(r.country) + "/" + repo.toPath(r.league) + "/" + english;
            t.setApiPath(apiPath);
            // ルーティングをlinkにする
            t.setRoutingPath(r.link);

            teams.add(t);
        }
        res.setTeams(teams);
        return res;
    }

    /** GET /api/leagues/{country}/{league}/{team} */
    public TeamDetailResponse getTeamDetail(String country, String league, String teamEnglish) {
        TeamRow row = repo.findTeamDetail(country, league, teamEnglish);
        if (row == null) return null;

        String[] parsed = repo.parseTeamLink(row.link);
        String english = parsed[0];
        String hash = parsed[1];

        TeamDetailResponse res = new TeamDetailResponse();
        res.setId(row.id);
        res.setCountry(row.country);
        res.setLeague(row.league);
        res.setName(row.team);
        res.setEnglish(english);
        res.setHash(hash);
        res.setLink(row.link);

        TeamPathsDTO paths = new TeamPathsDTO();
        String leaguePage = "/" + repo.toPath(row.country) + "/" + repo.toPath(row.league);
        String apiSelf = "/api/leagues/" + repo.toPath(row.country) + "/" + repo.toPath(row.league) + "/" + english;
        paths.setLeaguePage(leaguePage);
        paths.setApiSelf(apiSelf);

        res.setPaths(paths);
        return res;
    }
}
