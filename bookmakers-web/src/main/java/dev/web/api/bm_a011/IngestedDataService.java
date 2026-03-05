package dev.web.api.bm_a011;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

// ※TxManagerが複数で NoUniqueBeanDefinitionException を踏みやすいので、ここでは外すのを推奨。
// 必要なら @Transactional(transactionManager="bmTxManager", readOnly=true) のように明示してください。
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
        long total = 0;

        // rowId -> groupKey（同一データ群キー）
        Map<String, String> groupKeyByRowId = new HashMap<>();

        // groupKey -> 同一データ群の代表 gameLink（data側に1つでもあれば採用、終了済優先）
        Map<String, LinkPick> bestGameLinkByGroupKey = new HashMap<>();

        // groupKey -> 同一データ群の代表 dataCategory（ラウンド付き優先、無ければラウンド無し）
        Map<String, CategoryPick> bestCategoryByGroupKey = new HashMap<>();

        // ===== future_master =====
        if (req.isIncludeFutureMaster()) {
            var futures = futuresRepository.findFutureMasterByRegisterTime(req.getCountry());
            total += futures.size();

            for (var r : futures) {
                IngestedRowDTO row = new IngestedRowDTO();
                row.setTable(IngestedRowDTO.TableName.FUTURE_MASTER);
                row.setSeq(String.valueOf(r.seq));

                FutureMasterIngestSummaryDTO s = new FutureMasterIngestSummaryDTO();
                s.setSeq(r.seq);
                s.setGameTeamCategory(r.gameTeamCategory);
                s.setFutureTime(r.futureTime);
                s.setHomeTeamName(r.homeTeamName);
                s.setAwayTeamName(r.awayTeamName);
                s.setGameLink(r.gameLink);
                s.setStartFlg(r.startFlg);
                row.setFuture(s);

                // future_master は game_link から mid を抜く
                String mk = extractMidOrNull(r.gameLink);
                row.setMatchKey(mk);

                merged.add(row);
            }
        }

        // ===== data =====
        if (req.isIncludeData()) {
            var dataRows = bookDataRepository.findDataByRegisterTime(req.getCountry());
            total += dataRows.size();

            for (var r : dataRows) {

                IngestedRowDTO row = new IngestedRowDTO();
                row.setTable(IngestedRowDTO.TableName.DATA);
                row.setSeq(r.seq);

                DataIngestSummaryDTO s = new DataIngestSummaryDTO();
                s.setSeq(r.seq);
                s.setDataCategory(r.dataCategory);
                s.setTimes(r.times);
                s.setHomeTeamName(r.homeTeamName);
                s.setAwayTeamName(r.awayTeamName);
                s.setRecordTime(r.recordTime);
                s.setGameId(r.gameId);
                s.setGameLink(r.gameLink);
                row.setData(s);

                // matchKey（基本は match_id。無い場合だけURL/IDから救済）
                String mk = pickDataMatchKey(r.matchId, r.gameId, r.gameLink);
                row.setMatchKey(mk);

                // ★同一データ群キー：match_id があればそれ、無ければ(リーグ正規化 + home + away)
                String gk = buildGroupKeyForData(r);
                if (gk != null) {
                    groupKeyByRowId.put(rowId(row), gk);

                    // 代表カテゴリ（ラウンド付き優先）
                    String rawCategory = trimToNull(r.dataCategory);
                    if (rawCategory != null) {
                        int cprio = categoryPriority(rawCategory); // ラウンド付きが高い
                        CategoryPick cur = bestCategoryByGroupKey.get(gk);
                        if (cur == null || cprio > cur.priority) {
                            bestCategoryByGroupKey.put(gk, new CategoryPick(rawCategory, cprio));
                        }
                    }

                    // 代表リンク（終了済優先）
                    String glNorm = normalizeLink(r.gameLink);
                    if (glNorm != null) {
                        int lprio = priorityForTimes(r.times);
                        LinkPick cur = bestGameLinkByGroupKey.get(gk);
                        if (cur == null || lprio > cur.priority) {
                            bestGameLinkByGroupKey.put(gk, new LinkPick(glNorm, lprio));
                        }
                    }
                }

                merged.add(row);
            }
        }

        // ===== enrich（merged作り終わった後、sort/paging前）=====
        List<String> keys = merged.stream()
                .map(IngestedRowDTO::getMatchKey)
                .filter(k -> k != null && !k.isBlank())
                .distinct()
                .collect(Collectors.toList());

        Map<String, List<String>> timesByKey = keys.isEmpty()
                ? Map.of()
                : bookDataRepository.findDistinctTimesByMatchKeys(keys);

        Map<String, String> linkByKey = keys.isEmpty()
                ? Map.of()
                : futuresRepository.findGameLinksByMatchKeys(keys);

        for (var r : merged) {

            // ---------- times/futureExists ----------
            String k = r.getMatchKey();
            if (k == null || k.isBlank()) {
                r.setFutureExists(false);
                r.setTimesList(List.of());
                r.setHasFinishedTimes(false);
            } else {
                List<String> times = timesByKey.getOrDefault(k, List.of());
                boolean hasFinished = times.stream().anyMatch(t -> BookMakersCommonConst.FIN.equals(t));
                r.setTimesList(times);
                r.setHasFinishedTimes(hasFinished);
                r.setFutureExists(linkByKey.containsKey(k));
            }

            // ---------- ★data_category の代表値を群で揃える（ラウンド有優先） ----------
            if (r.getData() != null) {
                String gk = groupKeyByRowId.get(rowId(r));
                if (gk != null) {
                    CategoryPick cp = bestCategoryByGroupKey.get(gk);
                    if (cp != null && cp.category != null) {
                        // ラウンド無し行でも、群にラウンド付きがあるならラウンド付きに寄せる
                        r.getData().setDataCategory(cp.category);
                    }
                }
            }

            // ---------- ★gameLink 補完（群内優先 → master(mid)） ----------
            if (r.getData() != null) {
                String current = normalizeLink(r.getData().getGameLink());
                if (current == null) {
                    String filled = null;

                    // 1) 同一データ群に1つでもあるgame_linkを採用（要件）
                    String gk = groupKeyByRowId.get(rowId(r));
                    if (gk != null) {
                        LinkPick lp = bestGameLinkByGroupKey.get(gk);
                        filled = (lp == null) ? null : lp.link;
                    }

                    // 2) それでも無ければ master (mid→game_link)
                    if (filled == null && k != null) {
                        filled = normalizeLink(linkByKey.get(k));
                    }

                    if (filled != null) {
                        r.getData().setGameLink(filled);
                    }
                }
            }

            // future側も必要なら補完（任意）
            if (r.getFuture() != null) {
                String cur = normalizeLink(r.getFuture().getGameLink());
                if (cur == null && k != null) {
                    String filled = normalizeLink(linkByKey.get(k));
                    if (filled != null) {
                        r.getFuture().setGameLink(filled);
                    }
                }
            }
        }

        // paging（既存どおり）
        int fromIdx = Math.min(req.getOffset(), merged.size());
        int toIdx = Math.min(fromIdx + req.getLimit(), merged.size());
        List<IngestedRowDTO> paged = merged.subList(fromIdx, toIdx);

        IngestedDataReferenceResponse res = new IngestedDataReferenceResponse();
        res.setRows(paged);
        res.setTotal(total);
        return res;
    }

    // =========================================================
    // 同一データ群キー
    // =========================================================

    /**
     * 同一データ群キー（順序あり）
     * - match_id(mid) があればそれを最優先（最も正確）
     * - 無い場合のみ「リーグ正規化(ラウンド除去) + home + away」で束ねる
     *
     * ※ これにより「ラウンド有/無の data_category が混在しても同一群」になる
     */
    private static String buildGroupKeyForData(BookDataRepository.DataIngestRow r) {
        String mid = trimToNull(r.matchId);
        if (mid != null) {
            return "MID:" + mid;
        }
        String league = normalizeLeagueIgnoringRound(r.dataCategory);
        String home = trimToNull(r.homeTeamName);
        String away = trimToNull(r.awayTeamName);
        if (league == null || home == null || away == null) {
            return null;
        }
        return "PAIR:" + league + "|" + home + "|" + away; // 順序あり
    }

    /**
     * 「日本: J1 リーグ - ラウンド 3」→「日本: J1 リーグ」
     * 「日本: J1 リーグ」→そのまま
     *
     * ＝ラウンド有無があっても同一リーグ扱いにするための正規化。
     */
    private static String normalizeLeagueIgnoringRound(String dataCategory) {
        String dc = trimToNull(dataCategory);
        if (dc == null) return null;

        // " - " 以降を切り捨て（あなたのデータ形に一致）
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
    // small utils
    // =========================================================

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private static String normalizeLink(String link) {
        return trimToNull(link);
    }

    /** 終了済のgame_linkがあれば、それを代表にしたい */
    private static int priorityForTimes(String times) {
        String t = trimToNull(times);
        if (t == null) return 0;
        if (BookMakersCommonConst.FIN.equals(t)) return 2;
        return 1;
    }

    /** data_category はラウンド付きがあればそれを代表にする */
    private static int categoryPriority(String dataCategory) {
        String dc = trimToNull(dataCategory);
        if (dc == null) return 0;
        // "ラウンド" か "Round" を含む方を優先
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
