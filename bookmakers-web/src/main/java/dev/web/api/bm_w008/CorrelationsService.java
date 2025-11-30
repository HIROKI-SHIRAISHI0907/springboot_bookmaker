package dev.web.api.bm_w008;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import dev.web.repository.CorrelationsRepository;
import lombok.RequiredArgsConstructor;

/**
 * CorrelationsServiceクラス
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class CorrelationsService {

    private final CorrelationsRepository repo;

    public TeamCorrelationsResponse getCorrelations(
            String country, String league, String teamSlug, String opponentFilter) {

        String teamName = repo.findTeamName(country, league, teamSlug);
        if (teamName == null) return null;

        List<Map<String, Object>> rows =
                repo.findCorrelationRows(country, league, teamName, opponentFilter);

        // opponent 候補
        List<String> opponents = rows.stream()
            .map(r -> (String) r.get("opponent"))
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .collect(Collectors.toList());

        // HOME / AWAY / 1st / 2nd / ALL の構築
        CorrelationsBySideDTO bySide = buildCorrelations(rows);

        TeamCorrelationsResponse res = new TeamCorrelationsResponse();
        res.setTeam(teamName);
        res.setCountry(country);
        res.setLeague(league);
        res.setOpponent(opponentFilter);
        res.setOpponents(opponents);
        res.setCorrelations(bySide);

        return res;
    }

    private CorrelationsBySideDTO buildCorrelations(List<Map<String, Object>> rows) {
        CorrelationsBySideDTO out = new CorrelationsBySideDTO();
        out.setHome(new ScoreCorrelationsDTO());
        out.setAway(new ScoreCorrelationsDTO());

        String[] scores = { "1st", "2nd", "ALL" };

        for (String sc : scores) {
            Map<String, Object> homeRow = rows.stream()
                    .filter(r -> sc.equals(r.get("score")) && "home".equals(r.get("side")))
                    .findFirst().orElse(null);

            Map<String, Object> awayRow = rows.stream()
                    .filter(r -> sc.equals(r.get("score")) && "away".equals(r.get("side")))
                    .findFirst().orElse(null);

            setScore(out.getHome(), sc, homeRow);
            setScore(out.getAway(), sc, awayRow);
        }
        return out;
    }

    private void setScore(ScoreCorrelationsDTO target, String scoreKey, Map<String, Object> row) {
        List<CorrelationsItemDTO> list = new ArrayList<>();
        if (row != null) {
            for (int i = 1; i <= 74; i++) {
                String key = "rank_" + i + "th";
                Object v = row.get(key);
                if (v == null) continue;

                String s = v.toString();
                if (!s.contains(",")) continue;
                String[] p = s.split(",");
                if (p.length != 2) continue;

                String metric = p[0];
                Double val = Double.valueOf(p[1]);

                list.add(new CorrelationsItemDTO(metric, val));

                if (list.size() >= 5) break;
            }
        }

        switch (scoreKey) {
            case "1st" -> target.setFirst(list);
            case "2nd" -> target.setSecond(list);
            case "ALL" -> target.setAll(list);
        }
    }
}
