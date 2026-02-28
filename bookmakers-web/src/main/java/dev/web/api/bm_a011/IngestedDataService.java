package dev.web.api.bm_a011;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.common.constant.BookMakersCommonConst;
import dev.web.repository.bm.BookDataRepository;
import dev.web.repository.master.FuturesRepository;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class IngestedDataService {

	private final BookDataRepository bookDataRepository;

	private final FuturesRepository futuresRepository;

	@Transactional(readOnly = true)
	public IngestedDataReferenceResponse search(IngestedDataReferenceRequest req) {

		OffsetDateTime to = (req.getTo() != null)
				? req.getTo()
				: OffsetDateTime.now(ZoneId.of("Asia/Tokyo"));

		OffsetDateTime from = (req.getFrom() != null)
				? req.getFrom()
				: to.minusDays(7);

		List<IngestedRowDTO> merged = new ArrayList<>();
		long total = 0;

		// ===== future_master =====
		if (req.isIncludeFutureMaster()) {
			var futures = futuresRepository.findFutureMasterByRegisterTime(from, to);
			total += futures.size();

			for (var r : futures) {
				IngestedRowDTO row = new IngestedRowDTO();
				row.setTable(IngestedRowDTO.TableName.FUTURE_MASTER);
				row.setSeq(String.valueOf(r.seq));
				row.setRegisterTime(r.registerTime);
				row.setUpdateTime(r.updateTime);

				FutureMasterIngestSummaryDTO s = new FutureMasterIngestSummaryDTO();
				s.setSeq(r.seq);
				s.setGameTeamCategory(r.gameTeamCategory);
				s.setFutureTime(r.futureTime);
				s.setHomeTeamName(r.homeTeamName);
				s.setAwayTeamName(r.awayTeamName);
				s.setGameLink(r.gameLink);
				s.setStartFlg(r.startFlg);
				row.setFuture(s);

				// ★ここ：matchKey を必ずセット（future_master は game_link から mid 抜く）
				String mk = extractMidOrNull(r.gameLink);
				row.setMatchKey(mk);

				merged.add(row);
			}
		}

		// ===== data =====
		if (req.isIncludeData()) {
			var dataRows = bookDataRepository.findDataByRegisterTime(from, to);
			total += dataRows.size();

			for (var r : dataRows) {
				IngestedRowDTO row = new IngestedRowDTO();
				row.setTable(IngestedRowDTO.TableName.DATA);
				row.setSeq(r.seq);
				row.setRegisterTime(r.registerTime);
				row.setUpdateTime(r.updateTime);

				DataIngestSummaryDTO s = new DataIngestSummaryDTO();
				s.setSeq(r.seq);
				s.setDataCategory(r.dataCategory);
				s.setTimes(r.times);
				s.setHomeTeamName(r.homeTeamName);
				s.setAwayTeamName(r.awayTeamName);
				s.setRecordTime(r.recordTime);

				// ★追加：data側のキー材料
				s.setGameId(r.gameId);
				s.setGameLink(r.gameLink);

				row.setData(s);

				// ★ここ：matchKey を必ずセット（dataは game_id 優先、なければ game_link）
				String mk = pickDataMatchKey(r.matchId, r.gameId, r.gameLink);
				row.setMatchKey(mk);

				merged.add(row);
			}
		}

		// ===== enrich（merged作り終わった後、sort/paging前）=====
		List<String> keys = merged.stream()
				.map(IngestedRowDTO::getMatchKey)
				.filter(k -> k != null && !k.isBlank())
				.distinct()
				.collect(Collectors.toList());

		var timesByKey = bookDataRepository.findDistinctTimesByMatchKeys(keys);
		var linkByKey = futuresRepository.findGameLinksByMatchKeys(keys);

		for (var r : merged) {
			String k = r.getMatchKey();
			if (k == null || k.isBlank()) {
				r.setFutureExists(false);
				r.setTimesList(List.of());
				r.setHasFinishedTimes(false);
				continue;
			}

			// times 系は現状のままでOK
			List<String> times = timesByKey.getOrDefault(k, List.of());
			boolean hasFinished = times.stream().anyMatch(t -> BookMakersCommonConst.FIN.equals(t));
			r.setTimesList(times);
			r.setHasFinishedTimes(hasFinished);

			// ★futureExists は「リンクが引けたら true」で実用的（必要に応じて調整）
			boolean hasFutureLink = linkByKey.containsKey(k);
			r.setFutureExists(hasFutureLink);

			// ★ここが本題：data.gameLink を補完（matchUrl として使えるようにする）
			if (r.getData() != null) {
				String gl = r.getData().getGameLink();
				if (gl == null || gl.isBlank()) {
					String filled = linkByKey.get(k);
					if (filled != null && !filled.isBlank()) {
						r.getData().setGameLink(filled);
					}
				}
			}

			// includeFutureMaster=true で future DTO を出してるときは future 側も補完してよい
			if (r.getFuture() != null) {
				String fgl = r.getFuture().getGameLink();
				if (fgl == null || fgl.isBlank()) {
					String filled = linkByKey.get(k);
					if (filled != null && !filled.isBlank()) {
						r.getFuture().setGameLink(filled);
					}
				}
			}
		}

		// 新しい順
		merged.sort(
				Comparator.comparing(IngestedRowDTO::getRegisterTime, Comparator.nullsLast(Comparator.naturalOrder()))
						.reversed());

		// paging
		int fromIdx = Math.min(req.getOffset(), merged.size());
		int toIdx = Math.min(fromIdx + req.getLimit(), merged.size());
		List<IngestedRowDTO> paged = merged.subList(fromIdx, toIdx);

		IngestedDataReferenceResponse res = new IngestedDataReferenceResponse();
		res.setFrom(from);
		res.setTo(to);
		res.setRows(paged);
		res.setTotal(total);
		return res;
	}

	// ===== helpers =====
	private static String pickDataMatchKey(String matchId, String gameId, String gameLink) {
		String mid = (matchId == null) ? "" : matchId.trim();
		if (!mid.isBlank())
			return mid; // ★これが正

		// 互換用：古いデータで match_id が無い場合だけ mid 抽出を許可（ただし返すのは mid のみ）
		String extracted = extractMidOrNull(gameLink);
		if (extracted != null && !extracted.isBlank())
			return extracted;

		// gameId を採用するかどうかは運用次第。
		// 「matchKeyはmatchIdだけ」と割り切るなら return null が安全
		String gid = (gameId == null) ? "" : gameId.trim();
		if (!gid.isBlank())
			return gid; // ←残すならここ。消すなら return null

		return null;
	}

	private static String extractMidOrNull(String gameLink) {
		if (gameLink == null)
			return null;
		String gl = gameLink.trim();
		if (gl.isBlank())
			return null;

		var m = java.util.regex.Pattern.compile("[?&]mid=([A-Za-z0-9]+)").matcher(gl);
		if (m.find())
			return m.group(1);

		return null; // ★URLは返さない
	}

}
