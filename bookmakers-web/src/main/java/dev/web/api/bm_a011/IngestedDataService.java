package dev.web.api.bm_a011;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import dev.web.repository.bm.BookDataRepository;
import dev.web.repository.bm.MatchDetailDataWebRepository;
import dev.web.repository.master.FuturesRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IngestedDataService {

	private final BookDataRepository bookDataRepository;
	private final FuturesRepository futuresRepository;
	private final MatchDetailDataWebRepository matchDetailDataWebRepository;

	/** 集計結果をキャッシュする時間（ページ切り替え・一括起動のページ取得で使い回す） */
	private static final long CACHE_TTL_MILLIS = 60_000L;

	/** 国・絞り込み条件 → 集計結果（ページ分けする前の全件） */
	private final Map<String, CachedResult> cache = new ConcurrentHashMap<>();

	private static final class CachedResult {
		private final long createdAt;
		private final List<IngestedRowDTO> rows;

		private CachedResult(long createdAt, List<IngestedRowDTO> rows) {
			this.createdAt = createdAt;
			this.rows = rows;
		}
	}

	/**
	 * 検索。集計結果（全件）は国・条件ごとに CACHE_TTL_MILLIS の間キャッシュし、ページ分けだけを毎回行う。
	 * 1ページ目（offset=0）は「検索」ボタンなので、キャッシュを使わず作り直す。
	 */
	public IngestedDataReferenceResponse search(IngestedDataReferenceRequest req) {
		String cacheKey = String.valueOf(trimToNull(req.getCountry())) + "|" + req.isOnlyMissingFinishedOrFuture();
		long now = System.currentTimeMillis();

		// 古いキャッシュを捨てる
		cache.values().removeIf(c -> now - c.createdAt > CACHE_TTL_MILLIS);

		CachedResult cached = (req.getOffset() > 0) ? cache.get(cacheKey) : null;
		List<IngestedRowDTO> merged;
		if (cached != null) {
			merged = cached.rows;
		} else {
			merged = buildAll(req);
			cache.put(cacheKey, new CachedResult(now, merged));
		}

		// ===== paging =====
		int fromIdx = Math.min(Math.max(req.getOffset(), 0), merged.size());
		int toIdx = Math.min(fromIdx + Math.max(req.getLimit(), 0), merged.size());
		List<IngestedRowDTO> paged = new ArrayList<>(merged.subList(fromIdx, toIdx));

		IngestedDataReferenceResponse res = new IngestedDataReferenceResponse();
		res.setRows(paged);
		res.setTotal(merged.size());
		return res;
	}

	/**
	 * 条件に合う行を全件集計する（ページ分けなし）。
	 */
	private List<IngestedRowDTO> buildAll(IngestedDataReferenceRequest req) {

		// groupKey -> 集約結果
		Map<String, IngestedRowDTO> grouped = new LinkedHashMap<>();

		// groupKey -> 同一試合群の data 件数
		Map<String, Integer> sameMatchDataCountByGroupKey = new HashMap<>();

		// ===== future_master =====
		// 「国:」で始まるカテゴリの行と、その国のチーム（country_league_master）が出る行を1回の SQL で取得
		// （data_category に国が付与なしの場合を考慮。以前はチームごとに検索していた）
		List<FuturesRepository.FutureMasterIngestRow> futures =
				futuresRepository.findFutureMasterForIngest(req.getCountry());

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
				if ((row.getMatchKey() == null || row.getMatchKey().isBlank())
						&& extractMidOrNull(r.gameLink) != null) {
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
		// 同じ試合の行は SQL 側でまとめて取得（件数・終了済の有無つき）
		var dataRows = bookDataRepository.findDataGroupedForIngest(req.getCountry());

		for (var r : dataRows) {
			String gk = buildGroupKeyForData(r);
			if (gk == null) {
				gk = "DATA:" + r.seq;
			}

			sameMatchDataCountByGroupKey.merge(gk, r.rowCount, Integer::sum);

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

			if (r.finished) {
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
						sameMatchDataCountByGroupKey.getOrDefault(gk, 1));
			}
		}

		List<IngestedRowDTO> merged = new ArrayList<>(grouped.values());

		// ===== 未開催（試合時間がまだ来ていない）行は、チェックボックスの状態に関わらず常に除外 =====
		merged = merged.stream()
		        .filter(r -> !isNotYetStarted(r))
		        .collect(Collectors.toList());

		// ===== 統計詳細データ（match_detail_match）の登録有無。matchKey（mid）で判定 =====
		Set<String> registeredDetailIds = matchDetailDataWebRepository.findRegisteredMatchIds(
				merged.stream()
						.map(r -> trimToNull(r.getMatchKey()))
						.filter(Objects::nonNull)
						.collect(Collectors.toList()));
		for (IngestedRowDTO r : merged) {
			String mk = trimToNull(r.getMatchKey());
			r.setHasDetailData(mk != null && registeredDetailIds.contains(mk));
		}

		// ===== チェックボックス条件: 終了済・未来（試合予定）・統計詳細のいずれかが無いものだけ =====
		if (req.isOnlyMissingFinishedOrFuture()) {
		    merged = merged.stream()
		            .filter(r -> Boolean.FALSE.equals(r.getHasFinishedData())
		                    || Boolean.FALSE.equals(r.getFutureExists())
		                    || Boolean.FALSE.equals(r.getHasDetailData()))
		            .collect(Collectors.toList());
		}

		// ===== 試合予定時間（futureTime）が新しい順にソート。futureTimeが無い行は末尾に回す =====
		merged.sort((a, b) -> {
			Instant ta = parseFutureInstantOrNull(a);
			Instant tb = parseFutureInstantOrNull(b);
			if (ta == null && tb == null) {
				return 0;
			}
			if (ta == null) {
				return 1;
			}
			if (tb == null) {
				return -1;
			}
			return tb.compareTo(ta);
		});

		return java.util.Collections.unmodifiableList(merged);
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
		if (dc == null)
			return null;

		int idx = dc.indexOf(" - ");
		if (idx < 0)
			return dc.trim();
		return dc.substring(0, idx).trim();
	}

	// =========================================================
	// matchKey helpers
	// =========================================================

	private static String pickDataMatchKey(String matchId, String gameId, String gameLink) {
		String mid = trimToNull(matchId);
		if (mid != null)
			return mid;

		String extracted = extractMidOrNull(gameLink);
		if (trimToNull(extracted) != null)
			return extracted;

		String gid = trimToNull(gameId);
		if (gid != null)
			return gid;

		return null;
	}

	private static String extractMidOrNull(String gameLink) {
		String gl = trimToNull(gameLink);
		if (gl == null)
			return null;

		var m = java.util.regex.Pattern
				.compile("[?&]mid=([A-Za-z0-9]+)")
				.matcher(gl);

		return m.find() ? m.group(1) : null;
	}

	/**
	 * row の futureTime を Instant にパースする。取得できない／パース失敗の場合は null を返す。
	 */
	private static Instant parseFutureInstantOrNull(IngestedRowDTO row) {
		String futureTime = (row.getFuture() != null) ? row.getFuture().getFutureTime() : null;
		if (futureTime == null || futureTime.isBlank()) {
			return null;
		}
		try {
			return OffsetDateTime.parse(futureTime).toInstant();
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	/**
	 * まだ試合開始時刻を迎えていない（futureTime が現在時刻より未来の）行かどうかを判定する。
	 * Instant（絶対時刻）同士の比較のため、タイムゾーンの取り違えは発生しない。
	 * futureTime が取得できない場合は false（除外しない）とする。
	 */
	private static boolean isNotYetStarted(IngestedRowDTO row) {
		Instant matchInstant = parseFutureInstantOrNull(row);
		if (matchInstant == null) {
			return false;
		}
		return matchInstant.isAfter(Instant.now());
	}

	// =========================================================
	// utils
	// =========================================================

	private static String trimToNull(String s) {
		if (s == null)
			return null;
		String t = s.trim();
		return t.isBlank() ? null : t;
	}

	private static int categoryPriority(String category) {
		String dc = trimToNull(category);
		if (dc == null)
			return 0;
		boolean hasRound = dc.contains("ラウンド") || dc.contains("Round");
		return hasRound ? 2 : 1;
	}
}