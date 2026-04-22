package dev.web.api.bm_w011;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import dev.web.repository.bm.LeaguesRepository;
import dev.web.repository.bm.LeaguesRepository.LeagueCountRow;
import dev.web.repository.bm.LeaguesRepository.LeagueSeasonRow;
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

    private static final String SEASON_ENDED_LABEL = "シーズン終了";
    private static final String SEASON_ENDED_MESSAGE =
            "シーズンが終了しています。来シーズンまでお待ちください。";

    private final LeaguesRepository repo;

    /** GET /api/leagues (フラット一覧) */
    public List<LeagueFlatItemResponse> getLeaguesFlat() {
        List<LeagueCountRow> rows = repo.findLeagueCounts();
        List<LeagueFlatItemResponse> out = new ArrayList<>();

        for (LeagueCountRow r : rows) {
            LeagueFlatItemResponse item = new LeagueFlatItemResponse();
            item.setCountry(r.country);
            item.setLeague(r.leagueGroup);
            item.setTeamCount(r.getTeamCount() != null ? r.getTeamCount().intValue() : 0);
            String path = "/" + repo.toPath(r.country) + "/" + repo.toPath(r.leagueGroup);
            item.setPath(path);
            out.add(item);
        }
        return out;
    }

    /** GET /api/leagues/grouped */
    public List<LeagueGroupedResponse> getLeaguesGrouped() {
        List<LeagueCountRow> rows = repo.findLeagueCounts();

        Map<String, LeagueGroupedResponse> countryMap = new LinkedHashMap<>();
        Map<String, Map<String, LeagueInfoDTO>> leagueMapByCountry = new LinkedHashMap<>();
        Map<String, Map<String, Map<String, SubLeagueInfoDTO>>> subLeagueMapByCountryLeague = new LinkedHashMap<>();

        for (LeagueCountRow row : rows) {
            String country = safeTrim(row.getCountry());
            String leagueGroup = safeTrim(row.getLeagueGroup());
            String rawSubLeague = normalizeSubLeague(row.getSubLeague());

            boolean seasonEnded = isSeasonEnded(row.getEndSeasonDate());

            // country作成
            LeagueGroupedResponse countryDto = countryMap.computeIfAbsent(country, c -> {
                LeagueGroupedResponse dto = new LeagueGroupedResponse();
                dto.setCountry(c);
                dto.setLeagues(new ArrayList<>());
                return dto;
            });

            // country配下のleague map
            Map<String, LeagueInfoDTO> leagueMap = leagueMapByCountry.computeIfAbsent(country, c -> new LinkedHashMap<>());

            // league作成
            LeagueInfoDTO leagueDto = leagueMap.computeIfAbsent(leagueGroup, lg -> {
                LeagueInfoDTO dto = new LeagueInfoDTO();
                dto.setName(lg);
                dto.setLeagueGroup(lg);
                dto.setSeasonYear(row.getSeasonYear());
                dto.setStartSeasonDate(row.getStartSeasonDate());
                dto.setEndSeasonDate(row.getEndSeasonDate());
                dto.setVariantCount(row.getVariantCount() == null ? 0 : row.getVariantCount().intValue());
                dto.setTeamCount(row.getTeamCount() == null ? 0 : row.getTeamCount().intValue());
                dto.setPath(normalizeNoTrailingSlash(row.getPath()));
                dto.setRoutingPath(seasonEnded ? null : normalizeNoTrailingSlash(row.getPath()));
                dto.setSeasonEnded(seasonEnded);
                dto.setLinkEnabled(!seasonEnded);
                dto.setSeasonEndedLabel(seasonEnded ? SEASON_ENDED_LABEL : null);
                dto.setSubLeagues(new ArrayList<>());
                countryDto.getLeagues().add(dto);
                return dto;
            });

            // country + league 配下のsubLeague map
            Map<String, Map<String, SubLeagueInfoDTO>> byLeague =
                    subLeagueMapByCountryLeague.computeIfAbsent(country, c -> new LinkedHashMap<>());
            Map<String, SubLeagueInfoDTO> subLeagueMap =
                    byLeague.computeIfAbsent(leagueGroup, lg -> new LinkedHashMap<>());

            // subLeague作成
            SubLeagueInfoDTO subLeagueDto = subLeagueMap.computeIfAbsent(rawSubLeague, sl -> {
                SubLeagueInfoDTO dto = new SubLeagueInfoDTO();
                dto.setRawName(sl);
                dto.setName("▶︎" + sl);
                dto.setRoutingPath(seasonEnded ? null : buildSubLeagueRoutingPath(leagueDto.getPath(), sl));
                dto.setTeamCount(0);
                dto.setSeasonEnded(seasonEnded);
                dto.setLinkEnabled(!seasonEnded);
                dto.setSeasonEndedLabel(seasonEnded ? SEASON_ENDED_LABEL : null);
                leagueDto.getSubLeagues().add(dto);
                return dto;
            });

            // teamCount加算
            int addTeamCount = row.getTeamCount() == null ? 0 : row.getTeamCount().intValue();
            subLeagueDto.setTeamCount(subLeagueDto.getTeamCount() + addTeamCount);
        }

        return new ArrayList<>(countryMap.values());
    }

    private String normalizeSubLeague(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "未設定";
        }
        return value.trim();
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isSeasonEnded(String endSeasonDate) {
        return endSeasonDate == null || endSeasonDate.trim().isEmpty();
    }

    private String normalizeNoTrailingSlash(String p) {
        if (p == null) return null;
        String s = p.trim();
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private String buildSubLeagueRoutingPath(String basePath, String subLeague) {
        String normalizedBase = normalizeNoTrailingSlash(basePath);
        if (normalizedBase == null || normalizedBase.isEmpty()) {
            return null;
        }
        String encoded = URLEncoder.encode(subLeague, StandardCharsets.UTF_8);
        return normalizedBase + "?subLeague=" + encoded;
    }

    /** GET /api/leagues/{country}/{league} country:england, league:premier-league */
    public TeamsInLeagueResponse getTeamsInLeague(String country, String league, String subLeague) {
        validateSeasonOpen(country, league);

        List<TeamRow> rows = repo.findTeamsInLeagueOnSlug(country, league, subLeague);

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
            t.setRoutingPath(r.link);

            teams.add(t);
        }
        res.setTeams(teams);
        return res;
    }

    /** GET /api/leagues/{teamEnglish}/{teamHash} */
    public TeamDetailResponse getTeamDetail(String teamEnglish, String teamHash) {
        TeamRow row = repo.findTeamDetailByTeamAndHash(teamEnglish, teamHash);
        if (row == null) return null;

        TeamDetailResponse res = new TeamDetailResponse();
        res.setId(row.id);
        res.setCountry(row.country);
        res.setLeague(row.league);
        res.setName(row.team);
        res.setEnglish(teamEnglish);
        res.setHash(teamHash);
        res.setLink(row.link);

        TeamPathsDTO paths = new TeamPathsDTO();
        String leaguePage = "/" + repo.toPath(row.country) + "/" + repo.toPath(row.league);
        String apiSelf = "/api/leagues/" + repo.toPath(row.country) + "/" + repo.toPath(row.league) + "/" + teamEnglish;
        paths.setLeaguePage(leaguePage);
        paths.setApiSelf(apiSelf);

        res.setPaths(paths);
        return res;
    }

    private void validateSeasonOpen(String country, String league) {
        LeagueSeasonRow row = repo.findLeagueSeasonBySlug(country, league);

        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "league not found");
        }

        if (isSeasonEnded(row.getEndSeasonDate())) {
            throw new ResponseStatusException(HttpStatus.GONE, SEASON_ENDED_MESSAGE);
        }
    }
}
