package dev.web.api.bm_w008;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import dev.web.repository.bm.CorrelationsRepository;
import dev.web.repository.bm.LeaguesRepository;
import dev.web.repository.bm.LeaguesRepository.TeamRow;
import lombok.RequiredArgsConstructor;

/**
 * CorrelationsAPI用サービス
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class CorrelationsAPIService {

	private final LeaguesRepository leagueRepo;

    private final CorrelationsRepository repo;

    public TeamCorrelationsResponse getCorrelations(
            String teamEnglish, String teamHash, String opponentFilter) {

        TeamRow teamInfo = leagueRepo.findTeamDetailByTeamAndHash(teamEnglish, teamHash);
        if (teamInfo == null) return null;
        List<Map<String, Object>> rows =
                repo.findCorrelationRows(teamInfo.getCountry(), teamInfo.getLeague(),
                		teamInfo.getTeam(), opponentFilter);

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
        res.setTeam(teamInfo.getTeam());
        res.setCountry(teamInfo.getCountry());
        res.setLeague(teamInfo.getLeague());
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

    /**
     * スコア設定
     * @param target
     * @param scoreKey
     * @param row
     */
    private void setScore(ScoreCorrelationsDTO target, String scoreKey, Map<String, Object> row) {
        List<CorrelationsItemDTO> list = new ArrayList<>();

        if (row != null) {
            for (int i = 1; i <= 74; i++) {
                String key = "rank_" + i + "th";
                Object v = row.get(key);
                if (v == null) continue;

                String s = v.toString();
                if (!s.contains(",")) continue;

                String[] p = s.split(",", 2);
                if (p.length != 2) continue;

                String metric = p[0] == null ? null : p[0].trim();
                String valStr = p[1] == null ? null : p[1].trim();

                if (metric == null || metric.isEmpty()) continue;
                if (valStr == null || valStr.isEmpty()) continue;
                if ("null".equalsIgnoreCase(valStr)) continue; // ★ここが効く

                Double val;
                try {
                    val = Double.valueOf(valStr);
                } catch (NumberFormatException ex) {
                    // "NaN" や "—" 等が混ざっても落とさない
                    continue;
                }

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
