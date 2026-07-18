package dev.web.api.bm_u003;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.api.bm_a002.CountryLeagueSeasonDTO;
import dev.web.api.bm_a003.CountryLeagueDTO;
import dev.web.repository.master.CountryLeagueMasterWebRepository;
import dev.web.repository.master.CountryLeagueSeasonMasterWebRepository;
import dev.web.repository.user.FavoriteRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final CountryLeagueMasterWebRepository countryLeagueMasterWebRepository;
    private final CountryLeagueSeasonMasterWebRepository countryLeagueSeasonMasterWebRepository;

    public FavoriteScope getScope(Long userId) {
        return favoriteRepository.findFavoriteScope(userId);
    }

    /**
     * お気に入りをまとめて登録（country / league / team を item の内容で判定）
     * userId / operatorId は JWT から解決した値を controller から渡す
     */
    @Transactional
    public FavoriteResponse upsert(Long userId, String operatorId, FavoriteInsertRequest req) {
        FavoriteResponse res = new FavoriteResponse();

        if (userId == null) {
            res.setResponseCode("401");
            res.setMessage("認証情報がありません。");
            return res;
        }

        if (req == null || req.getItems() == null || req.getItems().isEmpty()) {
            res.setResponseCode("400");
            res.setMessage("必須項目が未入力です。");
            return res;
        }

        String normalizedOperatorId = normalize(operatorId);
        if (normalizedOperatorId.isEmpty()) {
            normalizedOperatorId = "system";
        }

        java.util.LinkedHashSet<FavKey> keys = new java.util.LinkedHashSet<>();

        for (FavoriteItem item : req.getItems()) {
            String country = normalize(item.getCountry());
            String league = normalize(item.getLeague());
            String team = normalize(item.getTeam());

            if (country.isEmpty()) {
                res.setResponseCode("400");
                res.setMessage("必須項目が未入力です。（country）");
                return res;
            }

            if (!team.isEmpty() && league.isEmpty()) {
                res.setResponseCode("400");
                res.setMessage("必須項目が未入力です。(league)");
                return res;
            }

            // 親を自動補完
            keys.add(new FavKey(1, country, "", ""));
            if (!league.isEmpty()) {
                keys.add(new FavKey(2, country, league, ""));
            }
            if (!team.isEmpty()) {
                keys.add(new FavKey(3, country, league, team));
            }
        }

        java.util.ArrayList<FavKey> ordered = new java.util.ArrayList<>(keys);
        ordered.sort(java.util.Comparator.comparingInt(k -> k.level)); // 1→2→3

        for (FavKey k : ordered) {
            try {
                favoriteRepository.insert(
                    userId,
                    k.level,
                    k.country,
                    k.league,
                    k.team,
                    normalizedOperatorId
                );
            } catch (Exception e) {
                res.setResponseCode("404");
                res.setMessage("登録処理が失敗しました。");
                return res;
            }
        }

        res.setResponseCode("200");
        res.setMessage("登録処理が成功しました。");
        return res;
    }

    @Transactional
    public FavoriteResponse delete(Long userId, Long id) {
        FavoriteResponse res = new FavoriteResponse();

        if (userId == null) {
            res.setResponseCode("401");
            res.setMessage("認証情報がありません。");
            return res;
        }

        try {
            favoriteRepository.deleteById(userId, id);
        } catch (Exception e) {
            res.setResponseCode("404");
            res.setMessage("削除処理が失敗しました。");
            return res;
        }

        res.setResponseCode("200");
        res.setMessage("削除処理が成功しました。");
        return res;
    }

    /**
     * お気に入り画面設定用マスターデータ取得
     */
    @Transactional(readOnly = true)
    public FavoriteScopeResponse getView(Long userId) {
        FavoriteScopeResponse res = new FavoriteScopeResponse();

        if (userId == null) {
            res.setResponseCode("401");
            res.setMessage("認証情報がありません。");
            return res;
        }

        // -----------------------------
        // 1. シーズン対象の country + league を収集
        // -----------------------------
        List<CountryLeagueSeasonDTO> seasonDtos = countryLeagueSeasonMasterWebRepository.findAll();

        java.util.Set<String> seasonCountryLeague = new java.util.HashSet<>();
        if (seasonDtos != null) {
            for (CountryLeagueSeasonDTO s : seasonDtos) {
                String country = normalize(s.getCountry());
                String league = normalize(s.getLeague());

                if (country.isEmpty() || league.isEmpty()) {
                    continue;
                }

                seasonCountryLeague.add(country + "\u0001" + league);
            }
        }

        // -----------------------------
        // 2. 画面表示用マスタを収集
        //    （シーズン対象の country+league のみ採用）
        // -----------------------------
        List<CountryLeagueDTO> masterDtos = countryLeagueMasterWebRepository.findAllActive();

        java.util.LinkedHashSet<String> countries = new java.util.LinkedHashSet<>();
        java.util.Map<String, java.util.LinkedHashSet<String>> leaguesByCountry = new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.LinkedHashSet<String>> teamsByCountryLeague = new java.util.LinkedHashMap<>();

        if (masterDtos != null) {
            for (CountryLeagueDTO m : masterDtos) {
                String country = normalize(m.getCountry());
                String league = normalize(m.getLeague());
                String team = normalize(m.getTeam());

                if (country.isEmpty() || league.isEmpty() || team.isEmpty()) {
                    continue;
                }

                String clKey = country + "\u0001" + league;

                // season master に存在する国・リーグのみ対象
                if (!seasonCountryLeague.contains(clKey)) {
                    continue;
                }

                countries.add(country);
                leaguesByCountry
                        .computeIfAbsent(country, k -> new java.util.LinkedHashSet<>())
                        .add(league);

                teamsByCountryLeague
                        .computeIfAbsent(clKey, k -> new java.util.LinkedHashSet<>())
                        .add(team);
            }
        }

        // -----------------------------
        // 3. userId に紐づくお気に入り取得
        //    ただし current master と整合するものだけ selectedItems に採用
        // -----------------------------
        List<FavoriteItem> rawSelected = favoriteRepository.findSelectedItems(userId);
        java.util.List<FavoriteItem> selected = new java.util.ArrayList<>();

        if (rawSelected != null) {
            for (FavoriteItem item : rawSelected) {
                String country = normalize(item.getCountry());
                String league = normalize(item.getLeague());
                String team = normalize(item.getTeam());

                if (country.isEmpty()) {
                    continue;
                }

                // level1: country のみ
                if (league.isEmpty() && team.isEmpty()) {
                    if (countries.contains(country)) {
                        FavoriteItem fi = new FavoriteItem();
                        fi.setCountry(country);
                        fi.setLeague(null);
                        fi.setTeam(null);
                        selected.add(fi);
                    }
                    continue;
                }

                // level2: country + league
                if (!league.isEmpty() && team.isEmpty()) {
                    java.util.Set<String> leagues = leaguesByCountry.get(country);
                    if (leagues != null && leagues.contains(league)) {
                        FavoriteItem fi = new FavoriteItem();
                        fi.setCountry(country);
                        fi.setLeague(league);
                        fi.setTeam(null);
                        selected.add(fi);
                    }
                    continue;
                }

                // level3: country + league + team
                if (!league.isEmpty() && !team.isEmpty()) {
                    String clKey = country + "\u0001" + league;
                    java.util.Set<String> teams = teamsByCountryLeague.get(clKey);
                    if (teams != null && teams.contains(team)) {
                        FavoriteItem fi = new FavoriteItem();
                        fi.setCountry(country);
                        fi.setLeague(league);
                        fi.setTeam(team);
                        selected.add(fi);
                    }
                }
            }
        }

        // お気に入り未登録なら全件許可扱い
        boolean noFavorite = selected.isEmpty();
        res.setAllowAll(noFavorite);

        // -----------------------------
        // 4. Response 用 CountryScope
        // -----------------------------
        java.util.List<CountryScope> countryScopes = new java.util.ArrayList<>();
        for (String c : countries) {
            CountryScope cs = new CountryScope();
            cs.setCountry(c);
            countryScopes.add(cs);
        }

        // -----------------------------
        // 5. Response 用 LeagueScope
        // -----------------------------
        java.util.List<LeagueScope> leagueScopes = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, java.util.LinkedHashSet<String>> e : leaguesByCountry.entrySet()) {
            LeagueScope ls = new LeagueScope();
            ls.setCountry(e.getKey());
            ls.setLeagues(new java.util.ArrayList<>(e.getValue()));
            leagueScopes.add(ls);
        }

        // -----------------------------
        // 6. Response 用 TeamScope
        // -----------------------------
        java.util.List<TeamScope> teamScopes = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, java.util.LinkedHashSet<String>> e : teamsByCountryLeague.entrySet()) {
            String[] parts = e.getKey().split("\u0001", 2);
            if (parts.length < 2) {
                continue;
            }

            TeamScope ts = new TeamScope();
            ts.setCountry(parts[0]);
            ts.setLeague(parts[1]);
            ts.setTeams(new java.util.ArrayList<>(e.getValue()));
            teamScopes.add(ts);
        }

        // -----------------------------
        // 7. Response へ設定
        // -----------------------------
        res.setAllowedCountries(countryScopes);
        res.setAllowedLeaguesByCountry(leagueScopes);
        res.setAllowedTeamsByCountryLeague(teamScopes);
        res.setSelectedItems(selected);

        res.setResponseCode("200");
        res.setMessage("OK");
        return res;
    }

    private static final class FavKey {
        final int level;
        final String country;
        final String league;
        final String team;

        FavKey(int level, String country, String league, String team) {
            this.level = level;
            this.country = country;
            this.league = league;
            this.team = team;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FavKey)) return false;
            FavKey other = (FavKey) o;
            return level == other.level
                && country.equals(other.country)
                && league.equals(other.league)
                && team.equals(other.team);
        }

        @Override
        public int hashCode() {
            int h = Integer.hashCode(level);
            h = 31 * h + country.hashCode();
            h = 31 * h + league.hashCode();
            h = 31 * h + team.hashCode();
            return h;
        }
    }

    private static String normalize(String v) {
        if (v == null) return "";
        String s = v.trim();
        if (s.equalsIgnoreCase("null")) return "";
        return s;
    }
}
