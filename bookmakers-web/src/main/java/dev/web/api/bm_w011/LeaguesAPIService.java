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

import dev.web.api.bm_u003.FavoriteScope;
import dev.web.repository.bm.LeaguesRepository;
import dev.web.repository.bm.LeaguesRepository.LeagueCountRow;
import dev.web.repository.bm.LeaguesRepository.LeagueSeasonRow;
import dev.web.repository.bm.LeaguesRepository.TeamRow;
import dev.web.repository.user.FavoriteRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LeaguesAPIService {

    private static final String SEASON_ENDED_LABEL = "シーズン終了";
    private static final String SEASON_ENDED_MESSAGE =
            "シーズンが終了しています。来シーズンまでお待ちください。";

    private final LeaguesRepository repo;
    private final FavoriteRepository favoriteRepository;

    /** GET /api/leagues (フラット一覧) */
    public List<LeagueFlatItemResponse> getLeaguesFlat() {
        List<LeagueCountRow> rows = repo.findLeagueCounts();
        List<LeagueFlatItemResponse> out = new ArrayList<>();

        for (LeagueCountRow r : rows) {
            LeagueFlatItemResponse item = new LeagueFlatItemResponse();
            item.setCountry(r.country);
            item.setLeague(r.leagueGroup);
            item.setTeamCount(r.getTeamCount() != null ? r.getTeamCount().intValue() : 0);
            String path = "/soccer/" + repo.toPath(r.country) + "/" + repo.toPath(r.leagueGroup);
            item.setPath(path);
            out.add(item);
        }
        return out;
    }

    /** GET /api/leagues/grouped */
    public List<LeagueGroupedResponse> getLeaguesGrouped(Long userId) {
        List<LeagueCountRow> rows = repo.findLeagueCounts();
        rows = applyFavoriteFilter(rows, userId);

        Map<String, LeagueGroupedResponse> countryMap = new LinkedHashMap<>();
        Map<String, Map<String, LeagueInfoDTO>> leagueMapByCountry = new LinkedHashMap<>();
        Map<String, Map<String, Map<String, SubLeagueInfoDTO>>> subLeagueMapByCountryLeague = new LinkedHashMap<>();

        for (LeagueCountRow row : rows) {
            String country = safeTrim(row.getCountry());
            String leagueGroup = safeTrim(row.getLeagueGroup());
            String rawSubLeague = normalizeSubLeague(row.getSubLeague());

            boolean seasonEnded = isSeasonEnded(row.getEndSeasonDate());

            LeagueGroupedResponse countryDto = countryMap.computeIfAbsent(country, c -> {
                LeagueGroupedResponse dto = new LeagueGroupedResponse();
                dto.setCountry(c);
                dto.setLeagues(new ArrayList<>());
                return dto;
            });

            Map<String, LeagueInfoDTO> leagueMap =
                    leagueMapByCountry.computeIfAbsent(country, c -> new LinkedHashMap<>());

            LeagueInfoDTO leagueDto = leagueMap.computeIfAbsent(leagueGroup, lg -> {
                LeagueInfoDTO dto = new LeagueInfoDTO();
                dto.setName(lg);
                dto.setLeagueGroup(lg);
                dto.setSeasonYear(row.getSeasonYear());
                dto.setStartSeasonDate(row.getStartSeasonDate());
                dto.setEndSeasonDate(row.getEndSeasonDate());
                dto.setVariantCount(row.getVariantCount() == null ? 0 : row.getVariantCount().intValue());
                dto.setTeamCount(0);
                dto.setPath(normalizeNoTrailingSlash(row.getPath()));
                dto.setRoutingPath(seasonEnded ? null : normalizeNoTrailingSlash(row.getPath()));
                dto.setSeasonEnded(seasonEnded);
                dto.setLinkEnabled(!seasonEnded);
                dto.setSeasonEndedLabel(seasonEnded ? SEASON_ENDED_LABEL : null);
                dto.setSubLeagues(new ArrayList<>());
                countryDto.getLeagues().add(dto);
                return dto;
            });

            int addTeamCount = row.getTeamCount() == null ? 0 : row.getTeamCount().intValue();
            leagueDto.setTeamCount((leagueDto.getTeamCount() == null ? 0 : leagueDto.getTeamCount()) + addTeamCount);

            // subLeague がないものはサブメニューに出さない
            if (rawSubLeague == null) {
                continue;
            }

            Map<String, Map<String, SubLeagueInfoDTO>> byLeague =
                    subLeagueMapByCountryLeague.computeIfAbsent(country, c -> new LinkedHashMap<>());
            Map<String, SubLeagueInfoDTO> subLeagueMap =
                    byLeague.computeIfAbsent(leagueGroup, lg -> new LinkedHashMap<>());

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

            subLeagueDto.setTeamCount((subLeagueDto.getTeamCount() == null ? 0 : subLeagueDto.getTeamCount()) + addTeamCount);
        }

        return new ArrayList<>(countryMap.values());
    }

    private List<LeagueCountRow> applyFavoriteFilter(List<LeagueCountRow> rows, Long userId) {
        if (userId == null) {
            return rows; // 未ログインは全表示
        }

        FavoriteScope scope = favoriteRepository.findFavoriteScope(userId);
        if (scope == null || scope.isAllowAll()) {
            return rows; // favorites 未設定なら全表示
        }

        List<LeagueCountRow> filtered = new ArrayList<>();
        for (LeagueCountRow row : rows) {
            String country = safeTrim(row.getCountry());
            String leagueGroup = safeTrim(row.getLeagueGroup());

            if (isAllowed(scope, country, leagueGroup)) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    private boolean isAllowed(FavoriteScope scope, String country, String leagueGroup) {
        // level=1 国お気に入り
        if (scope.getAllowedCountries() != null && scope.getAllowedCountries().contains(country)) {
            return true;
        }

        // level=2 国リーグお気に入り
        if (scope.getAllowedLeaguesByCountry() != null) {
            List<String> leagues = scope.getAllowedLeaguesByCountry().get(country);
            if (leagues != null && leagues.contains(leagueGroup)) {
                return true;
            }
        }

        // level=3 チームお気に入りがあるリーグも表示
        if (scope.getAllowedTeamsByCountryLeague() != null) {
            String key = country + "|" + leagueGroup;
            if (scope.getAllowedTeamsByCountryLeague().containsKey(key)) {
                return true;
            }
        }

        return false;
    }

    private String normalizeSubLeague(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        if (s.isEmpty()) {
            return null;
        }
        // 旧URL互換。未設定は「サブリーグなし」とみなす
        if ("未設定".equals(s)) {
            return null;
        }
        return s;
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

    /** GET /api/leagues/{country}/{league} */
    public TeamsInLeagueResponse getTeamsInLeague(String country, String league, String subLeague) {
        validateSeasonOpen(country, league);

        String normalizedSubLeague = normalizeSubLeague(subLeague);
        List<TeamRow> rows = repo.findTeamsInLeagueOnSlug(country, league, normalizedSubLeague);

        TeamsInLeagueResponse res = new TeamsInLeagueResponse();
        res.setCountry(country);
        res.setLeague(league);
        res.setSubLeague(normalizedSubLeague);

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

            String leaguePath = "/soccer/" + repo.toPath(r.country) + "/" + repo.toPath(r.league);
            t.setPath(leaguePath);

            String apiPath = "/api/leagues/" + english + "/" + hash + "/teamDetail";
            t.setApiPath(apiPath);

            t.setRoutingPath(r.link);
            teams.add(t);
        }
        res.setTeams(teams);
        return res;
    }

    /** GET /api/leagues/{teamEnglish}/{teamHash}/teamDetail */
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
        String leaguePage = "/soccer/" + repo.toPath(row.country) + "/" + repo.toPath(row.league);
        String apiSelf = "/api/leagues/" + teamEnglish + "/" + teamHash + "/teamDetail";
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
