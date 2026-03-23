package dev.web.api.bm_w014;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.bm.BookDataRepository;
import dev.web.repository.bm.LeaguesRepository;
import dev.web.repository.bm.LeaguesRepository.TeamRow;
import dev.web.repository.master.FuturesRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@AllArgsConstructor
@Slf4j
public class EachScoredLostAPIService {

	private final LeaguesRepository leagueRepo;
	private final FuturesRepository futuresRepository;
	private final BookDataRepository bookDataRepository;

	@Transactional(readOnly = true)
	public List<EachScoreLostDataResponseDTO> getEachScoreLoseMatches(String teamEnglish, String teamHash) {
		TeamRow teamInfo = leagueRepo.findTeamDetailByTeamAndHash(teamEnglish, teamHash);
		if (teamInfo == null)
			return null;

		// ① future_master 側（対象リーグ・対象チームの候補）
		List<FuturesRepository.DataEachScoreLostDataResponseDTO> masterChkList = futuresRepository
				.findEachScoreLoseMatchesExistsList(
						teamInfo.getCountry(),
						teamInfo.getLeague(),
						teamInfo.getTeam());

		if (masterChkList == null || masterChkList.isEmpty())
			return null;

		// ② data 側（終了済）を引き当てる
		// ★重複排除しつつ、順序は維持
		Map<String, EachScoreLostDataResponseDTO> out = new LinkedHashMap<>();

		for (FuturesRepository.DataEachScoreLostDataResponseDTO m : masterChkList) {

			if (m.getRoundNo() == null || m.getRoundNo().isBlank())
				continue;

			final int roundNo;
			try {
				roundNo = Integer.parseInt(m.getRoundNo());
			} catch (NumberFormatException e) {
				continue;
			}

			log.info("masterChkList: home: {}, away: {}, roundNo: {}",
					m.getHomeTeamName(), m.getAwayTeamName(), m.getRoundNo());

			// ★試合同一性キー：mid優先 → link → home/away/round
			String key = buildMatchKey(m);

			// 既に処理済みならスキップ（future_master 側重複対策）
			if (out.containsKey(key))
				continue;

			var opt = bookDataRepository.findEachScoreLoseMatchFinishedByRoundAndTeams(
					teamInfo.getCountry(),
					teamInfo.getLeague(),
					m.getHomeTeamName(),
					m.getAwayTeamName(),
					roundNo);

			opt.ifPresent(dto -> {
				// ★ recordTime は「試合日時（future_master.future_time）」で返す
				dto.setRecordTime(m.getRecordTime());

				// master 側を優先して整合（推奨）
				dto.setDataCategory(m.getDataCategory());
				dto.setRoundNo(m.getRoundNo());
				if (dto.getLink() == null || dto.getLink().isBlank()) {
					dto.setLink(m.getLink());
				}

				out.put(key, dto);
			});

		}

		// ★ラウンド降順でソートして返す
		return out.values().stream()
				.sorted((a, b) -> Integer.compare(
						parseRoundNo(b.getRoundNo()),
						parseRoundNo(a.getRoundNo())))
				.collect(Collectors.toList());
	}

	// ★ラウンド番号の安全パース（ソート用）
	private static int parseRoundNo(String roundNo) {
		if (roundNo == null || roundNo.isBlank())
			return -1;
		try {
			return Integer.parseInt(roundNo.trim());
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static String buildMatchKey(FuturesRepository.DataEachScoreLostDataResponseDTO m) {
		String mid = extractMid(m.getLink());
		if (mid != null && !mid.isBlank())
			return "MID:" + mid;

		if (m.getLink() != null && !m.getLink().isBlank())
			return "LINK:" + m.getLink().trim();

		return "TEAMS:" + safe(m.getHomeTeamName()) + "||" + safe(m.getAwayTeamName()) + "||" + safe(m.getRoundNo());
	}

	private static String safe(String s) {
		return (s == null) ? "" : s.trim();
	}

	// https://.../?mid=MVWBv2Hi みたいなやつから MVWBv2Hi を抜く
	private static String extractMid(String link) {
		if (link == null)
			return null;
		int idx = link.indexOf("mid=");
		if (idx < 0)
			return null;
		String tail = link.substring(idx + 4);
		// & があればそこで切る
		int amp = tail.indexOf('&');
		if (amp >= 0)
			tail = tail.substring(0, amp);
		// クエリ終端っぽいのがあれば軽く掃除
		tail = tail.replaceAll("[^A-Za-z0-9]", "");
		return tail.isBlank() ? null : tail;
	}
}
