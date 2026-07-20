package dev.web.api.bm_w011;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import dev.web.api.bm_a005.AllLeagueDTO;
import dev.web.api.bm_u003.FavoriteScope;
import dev.web.repository.bm.LeaguesRepository;
import dev.web.repository.bm.LeaguesRepository.LeagueCountRow;
import dev.web.repository.bm.LeaguesRepository.LeagueSeasonRow;
import dev.web.repository.bm.LeaguesRepository.TeamRow;
import dev.web.repository.master.AllLeagueMasterWebRepository;
import dev.web.repository.user.FavoriteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaguesAPIService {

    private static final String SEASON_ENDED_LABEL = "シーズン終了";
    private static final String SEASON_ENDED_MESSAGE =
            "シーズンが終了しています。来シーズンまでお待ちください。";

    private final LeaguesRepository repo;
    private final FavoriteRepository favoriteRepository;
    private final AllLeagueMasterWebRepository allLeagueMasterWebRepository;

    /** GET /api/leagues (フラット一覧) */
    public List<LeagueFlatItemResponse> getLeaguesFlat() {
        List<LeagueCountRow> rows = repo.findLeagueCounts();
        List<LeagueFlatItemResponse> out = new ArrayList<>();

        for (LeagueCountRow r : rows) {
            String country = safeTrim(r.getCountry());
            String leagueGroup = safeTrim(r.getLeagueGroup());

            if (!hasText(country) || !hasText(leagueGroup)) {
                continue;
            }

            LeagueFlatItemResponse item = new LeagueFlatItemResponse();
            item.setCountry(country);
            item.setLeague(leagueGroup);
            item.setTeamCount(r.getTeamCount() != null ? r.getTeamCount().intValue() : 0);

            String path = "/soccer/" + repo.toPath(country) + "/" + repo.toPath(leagueGroup);
            item.setPath(path);

            out.add(item);
        }
        return out;
    }

    /** GET /api/leagues/grouped */
    public List<LeagueGroupedResponse> getLeaguesGrouped(Long userId) {
        List<LeagueCountRow> rows = repo.findLeagueCounts();
        rows = applyMenuVisibilityFilter(rows, userId);

        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        // country|league ごとに、実際に sub_league が存在するかを先に判定
        Map<String, Set<String>> actualSubLeagueMap = new LinkedHashMap<>();
        for (LeagueCountRow row : rows) {
            String country = safeTrim(row.getCountry());
            String leagueGroup = safeTrim(row.getLeagueGroup());
            String subLeague = normalizeSubLeague(row.getSubLeague());

            if (!hasText(country) || !hasText(leagueGroup)) {
                continue;
            }

            String key = buildCountryLeagueKey(country, leagueGroup);
            actualSubLeagueMap.computeIfAbsent(key, k -> new LinkedHashSet<>());

            if (subLeague != null) {
                actualSubLeagueMap.get(key).add(subLeague);
            }
        }

        Map<String, LeagueGroupedResponse> countryMap = new LinkedHashMap<>();
        Map<String, Map<String, LeagueInfoDTO>> leagueMapByCountry = new LinkedHashMap<>();
        Map<String, Map<String, Map<String, SubLeagueInfoDTO>>> subLeagueMapByCountryLeague = new LinkedHashMap<>();

        for (LeagueCountRow row : rows) {
            String country = safeTrim(row.getCountry());
            String leagueGroup = safeTrim(row.getLeagueGroup());
            String rawSubLeague = normalizeSubLeague(row.getSubLeague());

            if (!hasText(country) || !hasText(leagueGroup)) {
                continue;
            }

            String countryLeagueKey = buildCountryLeagueKey(country, leagueGroup);
            Set<String> subLeagueNames = actualSubLeagueMap.getOrDefault(countryLeagueKey, Set.of());
            boolean hasActualSubLeague = !subLeagueNames.isEmpty();

            boolean seasonEnded = isSeasonEnded(row.getEndSeasonDate());
            String normalizedPath = normalizeNoTrailingSlash(row.getPath());
            boolean linkEnabled = !seasonEnded && hasText(normalizedPath);

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

                // sub_league は補助情報。存在しなければ 1 扱い
                dto.setVariantCount(hasActualSubLeague ? subLeagueNames.size() : 1);

                dto.setTeamCount(0);
                dto.setPath(normalizedPath);
                dto.setRoutingPath(linkEnabled ? normalizedPath : null);
                dto.setSeasonEnded(seasonEnded);
                dto.setLinkEnabled(linkEnabled);
                dto.setSeasonEndedLabel(seasonEnded ? SEASON_ENDED_LABEL : null);
                dto.setSubLeagues(new ArrayList<>());

                countryDto.getLeagues().add(dto);
                return dto;
            });

            int addTeamCount = row.getTeamCount() == null ? 0 : row.getTeamCount().intValue();
            leagueDto.setTeamCount((leagueDto.getTeamCount() == null ? 0 : leagueDto.getTeamCount()) + addTeamCount);

            // sub_league が実際に存在しないリーグはサブメニューを出さない
            if (!hasActualSubLeague) {
                continue;
            }

            // sub_league がある年だけ子要素を作る
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
                dto.setRoutingPath(linkEnabled ? buildSubLeagueRoutingPath(leagueDto.getPath(), sl) : null);
                dto.setTeamCount(0);
                dto.setSeasonEnded(seasonEnded);
                dto.setLinkEnabled(linkEnabled);
                dto.setSeasonEndedLabel(seasonEnded ? SEASON_ENDED_LABEL : null);
                leagueDto.getSubLeagues().add(dto);
                return dto;
            });

            subLeagueDto.setTeamCount(
                    (subLeagueDto.getTeamCount() == null ? 0 : subLeagueDto.getTeamCount()) + addTeamCount);
        }

        return new ArrayList<>(countryMap.values());
    }

    /**
     * 表示対象フィルタ
     * - userId == null  -> 管理者指定のみ
     * - favorites なし  -> 管理者指定のみ
     * - favorites あり  -> favorites対象のみ
     */
    private List<LeagueCountRow> applyMenuVisibilityFilter(List<LeagueCountRow> rows, Long userId) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        if (userId == null) {
            return applyAdminFilter(rows);
        }

        FavoriteScope scope = favoriteRepository.findFavoriteScope(userId);
        if (scope == null || scope.isAllowAll()) {
            return applyAdminFilter(rows);
        }

        return applyFavoriteFilter(rows, scope);
    }

    /**
     * 管理者指定(all_league_scrape_master logic_flg=0 and disp_flg=0) の国リーグのみ表示
     */
    private List<LeagueCountRow> applyAdminFilter(List<LeagueCountRow> rows) {
        Set<String> adminTargetKeys = loadAdminTargetLeagueKeys();
        if (adminTargetKeys.isEmpty()) {
            return List.of();
        }

        List<LeagueCountRow> filtered = new ArrayList<>();
        for (LeagueCountRow row : rows) {
            String country = safeTrim(row.getCountry());
            String leagueGroup = safeTrim(row.getLeagueGroup());

            if (!hasText(country) || !hasText(leagueGroup)) {
                continue;
            }

            String key = buildCountryLeagueKey(country, leagueGroup);
            if (adminTargetKeys.contains(key)) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    /**
     * favorites に応じて表示対象を絞る
     */
    private List<LeagueCountRow> applyFavoriteFilter(List<LeagueCountRow> rows, FavoriteScope scope) {
        List<LeagueCountRow> filtered = new ArrayList<>();

        for (LeagueCountRow row : rows) {
            String country = safeTrim(row.getCountry());
            String leagueGroup = safeTrim(row.getLeagueGroup());

            if (!hasText(country) || !hasText(leagueGroup)) {
                continue;
            }

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
            String key = buildCountryLeagueKey(country, leagueGroup);
            if (scope.getAllowedTeamsByCountryLeague().containsKey(key)) {
                return true;
            }
        }

        return false;
    }

    /**
     * all_league_scrape_master から表示対象(logic_flg=0 and disp_flg=0)を読み込む
     */
    private Set<String> loadAdminTargetLeagueKeys() {
        List<AllLeagueDTO> rows = allLeagueMasterWebRepository.findAll();
        Set<String> keys = new LinkedHashSet<>();

        if (rows == null) {
            return keys;
        }

        for (AllLeagueDTO row : rows) {
            String logicFlg = safeTrim(row.getLogicFlg());
            String dispFlg = safeTrim(row.getDispFlg());

            if (!"0".equals(logicFlg) || !"0".equals(dispFlg)) {
                continue;
            }

            String country = safeTrim(row.getCountry());
            String league = safeTrim(row.getLeague());

            if (!hasText(country) || !hasText(league)) {
                continue;
            }

            keys.add(buildCountryLeagueKey(country, league));
        }

        return keys;
    }

    private String buildCountryLeagueKey(String country, String league) {
        return safeTrim(country) + "|" + safeTrim(league);
    }

    private String normalizeSubLeague(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        if (s.isEmpty()) {
            return null;
        }
        if ("未設定".equals(s)) {
            return null;
        }
        return s;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * end_season_date が「今を過ぎているか」でシーズン終了判定
     * - null/blank は終了扱い
     * - parse できない場合は安全側で終了扱い
     */
    private boolean isSeasonEnded(String endSeasonDate) {
        if (!hasText(endSeasonDate)) {
            return true;
        }

        OffsetDateTime odt = tryParseOffsetDateTime(endSeasonDate);
        if (odt != null) {
            return OffsetDateTime.now(odt.getOffset()).isAfter(odt);
        }

        LocalDateTime ldt = tryParseLocalDateTime(endSeasonDate);
        if (ldt != null) {
            return LocalDateTime.now(ZoneOffset.ofHours(9)).isAfter(ldt);
        }

        return true;
    }

    private OffsetDateTime tryParseOffsetDateTime(String value) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            return null;
        }

        try {
            return OffsetDateTime.parse(v);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return OffsetDateTime.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX"));
        } catch (DateTimeParseException ignored) {
        }

        try {
            return OffsetDateTime.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXX"));
        } catch (DateTimeParseException ignored) {
        }

        try {
            return OffsetDateTime.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX"));
        } catch (DateTimeParseException ignored) {
        }

        return null;
    }

    private LocalDateTime tryParseLocalDateTime(String value) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            return null;
        }

        try {
            return LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        } catch (DateTimeParseException ignored) {
        }

        return null;
    }

    private String normalizeNoTrailingSlash(String p) {
        if (p == null) {
            return null;
        }
        String s = p.trim();
        if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
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
    public TeamsInLeagueResponse getTeamsInLeague(Long userId, String country, String league, String subLeague) {
        validateSeasonOpen(country, league);

        String normalizedSubLeague = normalizeSubLeague(subLeague);
        List<TeamRow> rows = repo.findTeamsInLeagueOnSlug(country, league, normalizedSubLeague);

        log.info("info: {}" + rows);

        // favorites に応じてチーム表示を絞る
        rows = applyTeamVisibilityFilter(rows, userId, country, league);

        log.info("afterinfo: {}" + rows);

        TeamsInLeagueResponse res = new TeamsInLeagueResponse();
        res.setCountry(country);
        res.setLeague(league);
        res.setSubLeague(normalizedSubLeague);

        List<TeamItemDTO> teams = new ArrayList<>();
        for (TeamRow r : rows) {
            String[] parsed = repo.parseTeamLink(r.getLink());
            String english = parsed[0];
            String hash = parsed[1];

            TeamItemDTO t = new TeamItemDTO();
            t.setName(r.getTeam());
            t.setEnglish(english);
            t.setHash(hash);
            t.setLink(r.getLink());

            String leaguePath = "/soccer/" + repo.toPath(r.getCountry()) + "/" + repo.toPath(r.getLeague());
            t.setPath(leaguePath);

            String apiPath = "/api/leagues/" + english + "/" + hash + "/teamDetail";
            t.setApiPath(apiPath);

            t.setRoutingPath(r.getLink());
            teams.add(t);
        }
        res.setTeams(teams);
        return res;
    }

    /** GET /api/leagues/{teamEnglish}/{teamHash}/teamDetail */
    public TeamDetailResponse getTeamDetail(String teamEnglish, String teamHash) {
        TeamRow row = repo.findTeamDetailByTeamAndHash(teamEnglish, teamHash);
        if (row == null) {
            return null;
        }

        TeamDetailResponse res = new TeamDetailResponse();
        res.setId(row.getId());
        res.setCountry(row.getCountry());
        res.setLeague(row.getLeague());
        res.setName(row.getTeam());
        res.setEnglish(teamEnglish);
        res.setHash(teamHash);
        res.setLink(row.getLink());

        TeamPathsDTO paths = new TeamPathsDTO();
        String leaguePage = "/soccer/" + repo.toPath(row.getCountry()) + "/" + repo.toPath(row.getLeague());
        String apiSelf = "/api/leagues/" + teamEnglish + "/" + teamHash + "/teamDetail";
        paths.setLeaguePage(leaguePage);
        paths.setApiSelf(apiSelf);

        res.setPaths(paths);
        return res;
    }

    /**
     * /api/leagues/{country}/{league} のチーム一覧を favorites に応じて絞る
     *
     * ルール:
     * - 未ログイン → 全件表示
     * - favorites なし(allowAll=true) → 全件表示
     * - country(level1) 許可 → 全件表示
     * - country+league(level2) 許可 → 全件表示
     * - team(level3) 指定がそのリーグにある → その team のみ表示
     * - favorites はあるが当該リーグが許可対象外 → 0件
     */
    private List<TeamRow> applyTeamVisibilityFilter(List<TeamRow> rows, Long userId, String country, String league) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        // 未ログインは制限しない
        if (userId == null) {
            return rows;
        }

        FavoriteScope scope = favoriteRepository.findFavoriteScope(userId);

        // favorites 未設定は全件表示
        if (scope == null || scope.isAllowAll()) {
            return rows;
        }

        // usa,mlsをアメリカ,MLS等に変更
        LeagueConvert convert = repo.convertTeamEng(country, league);

        String normalizedCountry = safeTrim(convert.getCountry());
        String normalizedLeague = safeTrim(convert.getLeague());

        // level=1: country 許可ならこのリーグの全チーム表示
        if (scope.getAllowedCountries() != null
                && scope.getAllowedCountries().contains(normalizedCountry)) {
            return rows;
        }

        // level=2: country + league 許可ならこのリーグの全チーム表示
        if (scope.getAllowedLeaguesByCountry() != null) {
            List<String> leagues = scope.getAllowedLeaguesByCountry().get(normalizedCountry);
            if (leagues != null && leagues.contains(normalizedLeague)) {
                return rows;
            }
        }

        // level=3: team 指定がある場合は、その team だけ表示
        if (scope.getAllowedTeamsByCountryLeague() != null) {
            String key = buildCountryLeagueKey(normalizedCountry, normalizedLeague);
            List<String> allowedTeams = scope.getAllowedTeamsByCountryLeague().get(key);

            if (allowedTeams == null || allowedTeams.isEmpty()) {
                return List.of();
            }

            Set<String> allowedTeamSet = new LinkedHashSet<>();
            for (String t : allowedTeams) {
                String normalizedTeam = safeTrim(t);
                if (hasText(normalizedTeam)) {
                    allowedTeamSet.add(normalizedTeam);
                }
            }

            if (allowedTeamSet.isEmpty()) {
                return List.of();
            }

            List<TeamRow> filtered = new ArrayList<>();
            for (TeamRow row : rows) {
                String teamName = safeTrim(row.getTeam());
                if (allowedTeamSet.contains(teamName)) {
                    filtered.add(row);
                }
            }

            return filtered;
        }

        return List.of();
    }

    private void validateSeasonOpen(String country, String league) {
        LeagueSeasonRow row = repo.findLeagueSeasonBySlug(country, league);

        // season master なし ＝ 開催中として扱わない
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.GONE, SEASON_ENDED_MESSAGE);
        }

        if (isSeasonEnded(row.getEndSeasonDate())) {
            throw new ResponseStatusException(HttpStatus.GONE, SEASON_ENDED_MESSAGE);
        }
    }
}
