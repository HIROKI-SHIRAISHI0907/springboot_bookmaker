package dev.web.api.bm_a011;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

        List<IngestedRowDTO> merged = new ArrayList<>();

        // rowId -> groupKey
        Map<String, String> groupKeyByRowId = new HashMap<>();

        // rowId -> この data 行自身が終了済か
        Map<String, Boolean> finishedByRowId = new HashMap<>();

        // groupKey -> future_master が存在するか
        Map<String, Boolean> futureExistsByGroupKey = new HashMap<>();

        // groupKey -> 同一試合群に終了済 data があるか
        Map<String, Boolean> hasFinishedDataByGroupKey = new HashMap<>();

        // groupKey -> 同一試合群の data 件数
        Map<String, Integer> sameMatchDataCountByGroupKey = new HashMap<>();

        // groupKey -> 同一データ群の代表 gameLink（data側に1つでもあれば採用、終了済優先）
        Map<String, LinkPick> bestGameLinkByGroupKey = new HashMap<>();

        // groupKey -> 同一データ群の代表 dataCategory（ラウンド付き優先）
        Map<String, CategoryPick> bestCategoryByGroupKey = new HashMap<>();

        // ===== future_master =====
        var futures = futuresRepository.findFutureMasterByRegisterTime(req.getCountry());

        for (var r : futures) {
            IngestedRowDTO row = new IngestedRowDTO();
            row.setTable(IngestedRowDTO.TableName.FUTURE_MASTER);
            row.setSeq(String.valueOf(r.seq));

            FutureMasterIngestSummaryDTO s = new FutureMasterIngestSummaryDTO();
            s.setGameTeamCategory(r.gameTeamCategory);
            s.setFutureTime(r.futureTime);
            s.setHomeTeamName(r.homeTeamName);
            s.setAwayTeamName(r.awayTeamName);
            s.setGameLink(r.gameLink);
            row.setFuture(s);

            String mk = extractMidOrNull(r.gameLink);
            row.setMatchKey(mk);

            String gk = buildGroupKeyForFuture(r);
            if (gk != null) {
                groupKeyByRowId.put(rowId(row), gk);
                futureExistsByGroupKey.put(gk, true);
            }

            merged.add(row);
        }

        // ===== data =====
        var dataRows = bookDataRepository.findDataByRegisterTime(req.getCountry());

        for (var r : dataRows) {
            IngestedRowDTO row = new IngestedRowDTO();
            row.setTable(IngestedRowDTO.TableName.DATA);
            row.setSeq(r.seq);

            DataIngestSummaryDTO s = new DataIngestSummaryDTO();
            s.setDataCategory(r.dataCategory);
            s.setHomeTeamName(r.homeTeamName);
            s.setAwayTeamName(r.awayTeamName);
            s.setGameId(r.gameId);
            s.setGameLink(r.gameLink);
            row.setData(s);

            String mk = pickDataMatchKey(r.matchId, r.gameId, r.gameLink);
            row.setMatchKey(mk);

            String gk = buildGroupKeyForData(r);
            boolean finished = isFinishedLikeTimes(r.times);

            if (gk != null) {
                groupKeyByRowId.put(rowId(row), gk);
                finishedByRowId.put(rowId(row), finished);

                sameMatchDataCountByGroupKey.merge(gk, 1, Integer::sum);

                if (finished) {
                    hasFinishedDataByGroupKey.put(gk, true);
                }

                String rawCategory = trimToNull(r.dataCategory);
                if (rawCategory != null) {
                    int cprio = categoryPriority(rawCategory);
                    CategoryPick cur = bestCategoryByGroupKey.get(gk);
                    if (cur == null || cprio > cur.priority) {
                        bestCategoryByGroupKey.put(gk, new CategoryPick(rawCategory, cprio));
                    }
                }

                String glNorm = normalizeLink(r.gameLink);
                if (glNorm != null) {
                    int lprio = priorityForTimes(r.times);
                    LinkPick cur = bestGameLinkByGroupKey.get(gk);
                    if (cur == null || lprio > cur.priority) {
                        bestGameLinkByGroupKey.put(gk, new LinkPick(glNorm, lprio));
                    }
                }
            } else {
                finishedByRowId.put(rowId(row), finished);
            }

            merged.add(row);
        }

        // master 側の game_link 補完用
        List<String> keys = merged.stream()
                .map(IngestedRowDTO::getMatchKey)
                .filter(k -> k != null && !k.isBlank())
                .distinct()
                .collect(Collectors.toList());

        Map<String, String> linkByKey = keys.isEmpty()
                ? Map.of()
                : futuresRepository.findGameLinksByMatchKeys(keys);

        // ===== enrich =====
        for (var r : merged) {
            String rid = rowId(r);
            String gk = groupKeyByRowId.get(rid);

            boolean futureExists;
            boolean hasFinishedData;

            if (gk != null) {
                futureExists = futureExistsByGroupKey.getOrDefault(gk, false);
                hasFinishedData = hasFinishedDataByGroupKey.getOrDefault(gk, false);
            } else {
                futureExists = (r.getFuture() != null);
                hasFinishedData = finishedByRowId.getOrDefault(rid, false);
            }

            r.setFutureExists(futureExists);
            r.setHasFinishedData(hasFinishedData);

            if (r.getData() != null) {
                int sameMatchCount = (gk != null)
                        ? sameMatchDataCountByGroupKey.getOrDefault(gk, 1)
                        : 1;
                r.getData().setSameMatchDataCount(sameMatchCount);

                // data_category は群で代表値に寄せる
                if (gk != null) {
                    CategoryPick cp = bestCategoryByGroupKey.get(gk);
                    if (cp != null && cp.category != null) {
                        r.getData().setDataCategory(cp.category);
                    }
                }

                // data.gameLink 補完
                String current = normalizeLink(r.getData().getGameLink());
                if (current == null) {
                    String filled = null;

                    if (gk != null) {
                        LinkPick lp = bestGameLinkByGroupKey.get(gk);
                        filled = (lp == null) ? null : lp.link;
                    }

                    if (filled == null && r.getMatchKey() != null) {
                        filled = normalizeLink(linkByKey.get(r.getMatchKey()));
                    }

                    if (filled != null) {
                        r.getData().setGameLink(filled);
                    }
                }
            }

            // future 側の gameLink 補完（必要時のみ）
            if (r.getFuture() != null) {
                String current = normalizeLink(r.getFuture().getGameLink());
                if (current == null && r.getMatchKey() != null) {
                    String filled = normalizeLink(linkByKey.get(r.getMatchKey()));
                    if (filled != null) {
                        r.getFuture().setGameLink(filled);
                    }
                }
            }
        }

        // ===== チェックボックス条件 =====
        if (req.isOnlyMissingFinishedOrFuture()) {
            merged = merged.stream()
                    .filter(r ->
                            Boolean.FALSE.equals(r.getHasFinishedData())
                         || Boolean.FALSE.equals(r.getFutureExists()))
                    .collect(Collectors.toList());
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

    private static String rowId(IngestedRowDTO row) {
        return row.getTable() + ":" + row.getSeq();
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
    // time / utils
    // =========================================================

    private static long toEpochMilli(String iso) {
        try {
            return iso == null ? 0L : OffsetDateTime.parse(iso).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private static String normalizeLink(String link) {
        return trimToNull(link);
    }

    private static int priorityForTimes(String times) {
        return isFinishedLikeTimes(times) ? 2 : 1;
    }

    private static boolean isFinishedLikeTimes(String times) {
        String t = trimToNull(times);
        if (t == null) return false;

        String norm = t.replaceAll("\\s+", "");

        return BookMakersCommonConst.FIN.equals(t)
            || norm.contains("ペナルティ");
    }

    private static int categoryPriority(String dataCategory) {
        String dc = trimToNull(dataCategory);
        if (dc == null) return 0;
        boolean hasRound = dc.contains("ラウンド") || dc.contains("Round");
        return hasRound ? 2 : 1;
    }

    private static class LinkPick {
        final String link;
        final int priority;

        LinkPick(String link, int priority) {
            this.link = link;
            this.priority = priority;
        }
    }

    private static class CategoryPick {
        final String category;
        final int priority;

        CategoryPick(String category, int priority) {
            this.category = category;
            this.priority = priority;
        }
    }
}
