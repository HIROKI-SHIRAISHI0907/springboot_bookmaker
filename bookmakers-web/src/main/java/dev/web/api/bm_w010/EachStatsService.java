package dev.web.api.bm_w010;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import dev.web.repository.bm.EachStatsRepository;
import lombok.RequiredArgsConstructor;

/**
 * EachStatsServiceクラス
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class EachStatsService {

    private final EachStatsRepository repo;

    public TeamStatsResponse getStats(String country, String league, String teamSlug) {

        // 1) スラッグ → 日本語チーム名
        String teamJa = repo.findTeamName(country, league, teamSlug);

        // 2) 生の行を取得
        List<Map<String, Object>> rows =
                repo.findStatsRows(country, league, teamJa);

        // 3) HOME / AWAY の Map を構築
        Map<String, Map<String, String>> home = new LinkedHashMap<>();
        Map<String, Map<String, String>> away = new LinkedHashMap<>();

        Set<String> situationsSet = new LinkedHashSet<>();
        Set<String> scoresSet = new LinkedHashSet<>();

        String[] STAT_COLUMNS = repo.getStatColumns();

        for (Map<String, Object> r : rows) {
            Object scoreObj = r.get("score");
            if (scoreObj == null) continue;
            String scoreKey = String.valueOf(scoreObj);  // "1st", "2nd", "ALL", "0-1" 等

            scoresSet.add(scoreKey);

            Object sitObj = r.get("situation");
            if (sitObj != null) {
                situationsSet.add(String.valueOf(sitObj));
            }

            Map<String, String> homeBag =
                    home.computeIfAbsent(scoreKey, k -> new LinkedHashMap<>());
            Map<String, String> awayBag =
                    away.computeIfAbsent(scoreKey, k -> new LinkedHashMap<>());

            for (String col : STAT_COLUMNS) {
                Object v = r.get(col);
                String val = (v == null) ? null : String.valueOf(v);

                if (col.startsWith("home_")) {
                    String metric = col.substring("home_".length());
                    homeBag.put(metric, val);
                } else if (col.startsWith("away_")) {
                    String metric = col.substring("away_".length());
                    awayBag.put(metric, val);
                }
            }
        }

        // 4) scores の並び順: 1st,2nd,ALL を先頭、それ以外は日本語ロケールでソート
        List<String> preferred = Arrays.asList("1st", "2nd", "ALL");
        Collator ja = Collator.getInstance(Locale.JAPAN);

        List<String> dynamicScores = scoresSet.stream()
                .filter(s -> !preferred.contains(s))
                .sorted(ja)
                .collect(Collectors.toList());

        List<String> scores = new ArrayList<>();
        for (String p : preferred) {
            if (scoresSet.contains(p)) scores.add(p);
        }
        scores.addAll(dynamicScores);

        // 5) DTO に詰める
        RawStatsDTO rawStats = new RawStatsDTO();
        rawStats.setHOME(home);
        rawStats.setAWAY(away);

        TeamStatsMetaDTO meta = new TeamStatsMetaDTO();
        meta.setTeamJa(teamJa);
        meta.setSituations(new ArrayList<>(situationsSet));
        meta.setScores(scores);

        TeamStatsResponse res = new TeamStatsResponse();
        res.setStats(rawStats);
        res.setMeta(meta);
        return res;
    }
}
