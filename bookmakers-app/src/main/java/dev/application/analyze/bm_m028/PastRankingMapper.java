package dev.application.analyze.bm_m028;

import java.util.Objects;

public final class PastRankingMapper {

    private PastRankingMapper() {}

    public static PastRankingEntity toEntity(PastRankingQueryParam p) {
        Objects.requireNonNull(p, "param is null");

        PastRankingEntity e = new PastRankingEntity();
        // id は param に無いので触らない（DB採番想定）
        e.setCountry(trimToNull(p.getCountry()));
        e.setLeague(trimToNull(p.getLeague()));
        e.setSeasonYear(trimToNull(p.getSeasonYear()));
        e.setMatch(p.getMatch());
        e.setTeam(trimToNull(p.getTeam()));
        e.setWin(p.getWin());
        e.setLose(p.getLose());
        e.setDraw(p.getDraw());
        e.setWinningPoints(p.getWinningPoints());
        return e;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
