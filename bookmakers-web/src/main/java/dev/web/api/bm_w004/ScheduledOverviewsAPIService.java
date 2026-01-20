package dev.web.api.bm_w004;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import dev.web.repository.bm.ScheduledOverviewsRepository;
import lombok.RequiredArgsConstructor;

/**
 * ScheduledOverviewsAPI用サービス
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class ScheduledOverviewsAPIService {

    private final ScheduledOverviewsRepository scheduledOverviewsRepository;

    /**
     * 開催予定 詳細取得（Service）
     *
     * @param country decoded済みを想定（decodeはcontrollerでもserviceでもどちらでもOK）
     * @param league  decoded済みを想定
     * @param seq
     * @param homeTeam クエリ home（任意）
     * @param awayTeam クエリ away（任意）
     */
    public ScheduledOverviewsResponse getScheduledOverview(
            String country,
            String league,
            long seq,
            String homeTeam,
            String awayTeam
    ) {
        // -------- validate --------
        if (!StringUtils.hasText(country) || !StringUtils.hasText(league)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "country/league are required");
        }
        if (seq <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seq must be positive");
        }

        String home = trimSafe(homeTeam);
        String away = trimSafe(awayTeam);

        if (!StringUtils.hasText(home) && !StringUtils.hasText(away)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "home or away query parameter is required (at least one)"
            );
        }

        // -------- fetch snapshots --------
        ScheduledSurfaceSnapshotDTO homeSnap = null;
        ScheduledSurfaceSnapshotDTO awaySnap = null;

        if (StringUtils.hasText(home)) {
            homeSnap = scheduledOverviewsRepository.findLatestSnapshot(country, league, home, "home");
        }
        if (StringUtils.hasText(away)) {
            awaySnap = scheduledOverviewsRepository.findLatestSnapshot(country, league, away, "away");
        }

        if (homeSnap == null && awaySnap == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "no surface_overview snapshot for given team(s)"
            );
        }

        // -------- pick latest (year, month) among available snaps --------
        Integer latestYear = null;
        Integer latestMonth = null;

        List<ScheduledSurfaceSnapshotDTO> snapsForYm = new ArrayList<>();
        if (homeSnap != null) snapsForYm.add(homeSnap);
        if (awaySnap != null) snapsForYm.add(awaySnap);

        var latestOpt = snapsForYm.stream()
                .filter(s -> s.getGameYear() != null && s.getGameMonth() != null)
                .max(Comparator
                        .comparingInt((ScheduledSurfaceSnapshotDTO s) -> s.getGameYear())
                        .thenComparingInt(ScheduledSurfaceSnapshotDTO::getGameMonth));

        if (latestOpt.isPresent()) {
            latestYear = latestOpt.get().getGameYear();
            latestMonth = latestOpt.get().getGameMonth();
        }

        // -------- build match --------
        ScheduledOverviewsMatchDTO match = new ScheduledOverviewsMatchDTO();
        match.setSeq(seq);
        match.setCountry(country);
        match.setLeague(league);

        // 「見つかった snapshot の team 名」を優先。無ければクエリ値（trim後）を入れる。
        match.setHomeTeam(homeSnap != null ? homeSnap.getTeam() : (home != null ? home : ""));
        match.setAwayTeam(awaySnap != null ? awaySnap.getTeam() : (away != null ? away : ""));

        // 既存仕様に合わせて null 固定
        match.setFutureTime(null);
        match.setRoundNo(null);

        match.setGameYear(latestYear);
        match.setGameMonth(latestMonth);

        // -------- build surfaces (ALWAYS home -> away order) --------
        List<ScheduledSurfaceSnapshotDTO> surfaces = new ArrayList<>(2);
        if (homeSnap != null) surfaces.add(homeSnap);
        if (awaySnap != null) surfaces.add(awaySnap);

        return new ScheduledOverviewsResponse(match, surfaces);
    }

    private String trimSafe(String s) {
        return s == null ? null : s.trim();
    }
}
