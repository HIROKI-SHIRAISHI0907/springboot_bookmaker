package dev.web.api.bm_a011;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import dev.common.constant.BookMakersCommonConst;
import dev.web.repository.bm.BookDataRepository;
import dev.web.repository.master.FuturesRepository;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class IngestedDataService {

    private final BookDataRepository bookDataRepository;
    private final FuturesRepository futuresRepository;

    public IngestedDataReferenceResponse search(IngestedDataReferenceRequest req) {

        // groupKey -> 集約結果
        Map<String, IngestedRowDTO> grouped = new LinkedHashMap<>();

        // groupKey -> 同一試合群の data 件数
        Map<String, Integer> sameMatchDataCountByGroupKey = new HashMap<>();

        // ===== future_master =====
        var futures = futuresRepository.findFutureMasterByRegisterTime(req.getCountry());

        for (var r : futures) {
            String gk = buildGroupKeyForFuture(r);
            if (gk == null) {
                // group化できないものは seq ベースで逃がす
                gk = "FUTURE:" + r.seq;
            }

            IngestedRowDTO row = grouped.get(gk);
            if (row == null) {
                row = new IngestedRowDTO();
                row.setSeq(String.valueOf(r.seq));
                row.setTable(IngestedRowDTO.TableName.FUTURE_MASTER);
                row.setMatchKey(extractMidOrNull(r.gameLink));
                row.setFutureExists(true);
                row.setHasFinishedData(false);
                grouped.put(gk, row);
            } else {
                row.setFutureExists(true);
                if ((row.getMatchKey() == null || row.getMatchKey().isBlank()) && extractMidOrNull(r.gameLink) != null) {
                    row.setMatchKey(extractMidOrNull(r.gameLink));
                }
            }

            FutureMasterIngestSummaryDTO current = row.getFuture();
            if (shouldReplaceFuture(current, r)) {
                FutureMasterIngestSummaryDTO s = new FutureMasterIngestSummaryDTO();
                s.setGameTeamCategory(r.gameTeamCategory);
                s.setFutureTime(r.futureTime);
                s.setHomeTeamName(r.homeTeamName);
                s.setAwayTeamName(r.awayTeamName);
                s.setGameLink(r.gameLink);
                row.setFuture(s);
            }
        }

        // ===== data =====
        var dataRows = bookDataRepository.findDataByRegisterTime(req.getCountry());

        for (var r : dataRows) {
            String gk = buildGroupKeyForData(r);
            if (gk == null) {
                gk = "DATA:" + r.seq;
            }

            sameMatchDataCountByGroupKey.merge(gk, 1, Integer::sum);

            IngestedRowDTO row = grouped.get(gk);
            if (row == null) {
                row = new IngestedRowDTO();
                row.setSeq(r.seq);
                row.setTable(IngestedRowDTO.TableName.DATA);
                row.setMatchKey(pickDataMatchKey(r.matchId, r.gameId, r.gameLink));
                row.setFutureExists(false);
                row.setHasFinishedData(false);
                grouped.put(gk, row);
            } else {
                // dataがあるなら代表行は DATA 扱いに寄せる
                row.setTable(IngestedRowDTO.TableName.DATA);

                if (row.getSeq() == null || row.getSeq().isBlank()) {
                    row.setSeq(r.seq);
                }

                if ((row.getMatchKey() == null || row.getMatchKey().isBlank())) {
                    row.setMatchKey(pickDataMatchKey(r.matchId, r.gameId, r.gameLink));
                }
            }

            if (isFinishedLikeTimes(r.times)) {
                row.setHasFinishedData(true);
            }

            DataIngestSummaryDTO current = row.getData();
            if (shouldReplaceData(current, r)) {
                DataIngestSummaryDTO s = new DataIngestSummaryDTO();
                s.setDataCategory(r.dataCategory);
                s.setHomeTeamName(r.homeTeamName);
                s.setAwayTeamName(r.awayTeamName);
                s.setGameId(r.gameId);
                s.setGameLink(r.gameLink);
                row.setData(s);
            }
        }

        // ===== enrich =====
        for (Map.Entry<String, IngestedRowDTO> e : grouped.entrySet()) {
            String gk = e.getKey();
            IngestedRowDTO row = e.getValue();

            if (row.getData() != null) {
                row.getData().setSameMatchDataCount(
                        sameMatchDataCountByGroupKey.getOrDefault(gk, 1)
                );
            }
        }

        List<IngestedRowDTO> merged = new ArrayList<>(grouped.values());

        // ===== チェックボックス条件 =====
        if (req.isOnlyMissingFinishedOrFuture()) {
            merged = merged.stream()
                    .filter(r ->
                            Boolean.FALSE.equals(r.getHasFinishedData())
                         || Boolean.FALSE.equals(r.getFutureExists()))
                    .toList();
        }

        // ===== paging =====
        int fromIdx = Math.min(req.getOffset(), merged.size());
        int toIdx = Math.min(fromIdx + req.getLimit(), merged.size());
        List<IngestedRowDTO> paged = merged.subList(fromIdx, toIdx);

        IngestedDataReferenceResponse res = new IngestedDataReferenceResponse();
        res.setRows(paged);
        res.setTotal(merged.size());
        return res;
    }

    // =========================================================
    // representative selection
    // =========================================================

    private static boolean shouldReplaceFuture(FutureMasterIngestSummaryDTO current,
                                               FuturesRepository.FutureMasterIngestRow candidate) {
        if (current == null) {
            return true;
        }

        // gameLink があるものを優先
        String curLink = trimToNull(current.getGameLink());
        String newLink = trimToNull(candidate.gameLink);
        if (curLink == null && newLink != null) {
            return true;
        }

        // ラウンド付きカテゴリを優先
        int curPriority = categoryPriority(current.getGameTeamCategory());
        int newPriority = categoryPriority(candidate.gameTeamCategory);
        if (newPriority > curPriority) {
            return true;
        }

        return false;
    }

    private static boolean shouldReplaceData(DataIngestSummaryDTO current,
                                             BookDataRepository.DataIngestRow candidate) {
        if (current == null) {
            return true;
        }

        // gameLink があるものを優先
        String curLink = trimToNull(current.getGameLink());
        String newLink = trimToNull(candidate.gameLink);
        if (curLink == null && newLink != null) {
            return true;
        }

        // ラウンド付きカテゴリを優先
        int curPriority = categoryPriority(current.getDataCategory());
        int newPriority = categoryPriority(candidate.dataCategory);
        if (newPriority > curPriority) {
            return true;
        }

        return false;
    }

    // =========================================================
    // groupKey
    // =========================================================

    private static String buildGroupKeyForData(BookDataRepository.DataIngestRow r) {
        String mid = trimToNull(r.matchId);
        if (mid != null) {
            return "MID:" + mid;
        }

        String extractedMid = extractMidOrNull(r.gameLink);
        if (extractedMid != null) {
            return "MID:" + extractedMid;
        }

        String league = normalizeLeagueIgnoringRound(r.dataCategory);
        String home = trimToNull(r.homeTeamName);
        String away = trimToNull(r.awayTeamName);

        if (league == null || home == null || away == null) {
            return null;
        }

        return "PAIR:" + league + "|" + home + "|" + away;
    }

    private static String buildGroupKeyForFuture(FuturesRepository.FutureMasterIngestRow r) {
        String mid = extractMidOrNull(r.gameLink);
        if (mid != null) {
            return "MID:" + mid;
        }

        String league = normalizeLeagueIgnoringRound(r.gameTeamCategory);
        String home = trimToNull(r.homeTeamName);
        String away = trimToNull(r.awayTeamName);

        if (league == null || home == null || away == null) {
            return null;
        }

        return "PAIR:" + league + "|" + home + "|" + away;
    }

    private static String normalizeLeagueIgnoringRound(String category) {
        String dc = trimToNull(category);
        if (dc == null) return null;

        int idx = dc.indexOf(" - ");
        if (idx < 0) return dc.trim();
        return dc.substring(0, idx).trim();
    }

    // =========================================================
    // matchKey helpers
    // =========================================================

    private static String pickDataMatchKey(String matchId, String gameId, String gameLink) {
        String mid = trimToNull(matchId);
        if (mid != null) return mid;

        String extracted = extractMidOrNull(gameLink);
        if (trimToNull(extracted) != null) return extracted;

        String gid = trimToNull(gameId);
        if (gid != null) return gid;

        return null;
    }

    private static String extractMidOrNull(String gameLink) {
        String gl = trimToNull(gameLink);
        if (gl == null) return null;

        var m = java.util.regex.Pattern
                .compile("[?&]mid=([A-Za-z0-9]+)")
                .matcher(gl);

        return m.find() ? m.group(1) : null;
    }

    // =========================================================
    // utils
    // =========================================================

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private static boolean isFinishedLikeTimes(String times) {
        String t = trimToNull(times);
        if (t == null) return false;

        String norm = t.replaceAll("\\s+", "");

        return BookMakersCommonConst.FIN.equals(t)
            || norm.contains("ペナルティ");
    }

    private static int categoryPriority(String category) {
        String dc = trimToNull(category);
        if (dc == null) return 0;
        boolean hasRound = dc.contains("ラウンド") || dc.contains("Round");
        return hasRound ? 2 : 1;
    }
}
