package dev.web.api.bm_u003.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.web.api.bm_u003.CountryScope;
import dev.web.api.bm_u003.FavoriteScope;
import dev.web.api.bm_u003.FavoriteScopeResponse;
import dev.web.api.bm_u003.LeagueScope;
import dev.web.api.bm_u003.TeamScope;

/**
 * FavoriteScopeMapper
 * @author shiraishitoshio
 *
 */
@Component
public class FavoriteScopeMapper {

    public FavoriteScopeResponse toResponse(FavoriteScope scope) {

        FavoriteScopeResponse res = new FavoriteScopeResponse();
        res.setAllowAll(scope.isAllowAll());

        // countries
        List<CountryScope> countries = new ArrayList<>();
        for (String c : scope.getAllowedCountries()) {
            CountryScope dto = new CountryScope();
            dto.setCountry(c);
            countries.add(dto);
        }
        res.setAllowedCountries(countries);

        // leagues
        List<LeagueScope> leagues = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : scope.getAllowedLeaguesByCountry().entrySet()) {
            LeagueScope dto = new LeagueScope();
            dto.setCountry(e.getKey());
            dto.setLeagues(e.getValue());
            leagues.add(dto);
        }
        res.setAllowedLeaguesByCountry(leagues);

        // teams
        List<TeamScope> teams = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : scope.getAllowedTeamsByCountryLeague().entrySet()) {
            String[] parts = e.getKey().split("\\|", 2);

            TeamScope dto = new TeamScope();
            dto.setCountry(parts[0]);
            dto.setLeague(parts.length > 1 ? parts[1] : "");
            dto.setTeams(e.getValue());
            teams.add(dto);
        }
        res.setAllowedTeamsByCountryLeague(teams);

        return res;
    }
}
