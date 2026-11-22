package dev.web.api.bm_a038;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import dev.web.api.matchDetail.availability.DetailAvailabilityService;
import dev.web.repository.bm.MatchDetailAvailabilityRepository;
import dev.web.repository.bm.MatchDetailReferenceRepository;
import lombok.RequiredArgsConstructor;

/**
 * 統計詳細データ参照（BM_A038。2026-11 追加）
 * <ul>
 *   <li>{@link #search}: match_detail_match を キーワード（試合ID・カテゴリ・チーム名）・試合日の範囲・取得エラーありで検索する。</li>
 * </ul>
 * 試合ごとの中身は既存の GET /v1/api/match-detail/extra?matchId= で取る（画面のモーダル）。
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class MatchDetailReferenceService {

	/** 1 ページの件数の上限 */
	public static final int MAX_LIMIT = 500;

	/** 1 ページの件数の既定 */
	public static final int DEFAULT_LIMIT = 100;

	private final MatchDetailReferenceRepository repository;

	/** 2026-11-22: リーグで提供されていないデータは「一部なし」にしない */
	private final DetailAvailabilityService detailAvailabilityService;

	/**
	 * 検索
	 * @throws IllegalArgumentException 日付の形式が正しくないとき（画面に 400 で返す）
	 */
	public MatchDetailReferenceResponse search(String keyword, String dateFrom, String dateTo, Boolean errorsOnly,
			Integer offset, Integer limit) {
		String kw = trim(keyword);
		String from = date(dateFrom, "試合日（から）");
		String to = date(dateTo, "試合日（まで）");
		if (from != null && to != null && from.compareTo(to) > 0) {
			throw new IllegalArgumentException("試合日の範囲が逆です（" + from + " 〜 " + to + "）");
		}
		boolean err = Boolean.TRUE.equals(errorsOnly);
		int off = offset == null || offset < 0 ? 0 : offset;
		int lim = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

		int total = repository.count(kw, from, to, err);
		List<MatchDetailReferenceDTO> items = total == 0 ? List.of() : repository.find(kw, from, to, err, off, lim);
		judgeAvailability(items);
		return new MatchDetailReferenceResponse(total, off, lim, items);
	}

	/**
	 * 2026-11-22: 試合ごとに「取れるはずなのに無いデータ」と「このリーグでは提供なし」を入れる。
	 * 画面の「取得」は、取れるはずなのに無いデータがあるときだけ「一部なし」（scrape_errors があっても、
	 * 提供なし（例: J2 のモメンタム）だけなら「OK」）。
	 */
	private void judgeAvailability(List<MatchDetailReferenceDTO> items) {
		if (items.isEmpty()) {
			return;
		}
		Map<String, Set<String>> unavailableByLeague = detailAvailabilityService.unavailableByLeague(
				items.stream().map(d -> MatchDetailAvailabilityRepository.leagueOf(d.getCategory()))
						.collect(Collectors.toSet()));
		for (MatchDetailReferenceDTO d : items) {
			Set<String> has = new HashSet<>();
			has.add("match");
			if (d.getSummaryCount() > 0) has.add("summary");
			if (d.getGoalCount() > 0) has.add("goal");
			if (d.getMomentumCount() > 0) has.add("momentum");
			if (d.getIncidentCount() > 0) has.add("momentumIncident");
			if (d.getFormationCount() > 0) has.add("formation");
			if (d.getLineupCount() > 0) has.add("lineup");
			if (d.getMissingCount() > 0) has.add("missing");
			if (d.getMemberStatCount() > 0) has.add("memberStat");
			DetailAvailabilityService.Judge j = DetailAvailabilityService.judge(has,
					unavailableByLeague.getOrDefault(MatchDetailAvailabilityRepository.leagueOf(d.getCategory()), Set.of()));
			d.setMissingTables(j.missingLabels());
			d.setUnavailableTables(j.unavailableLabels());
		}
	}

	private static String date(String s, String label) {
		String v = trim(s);
		if (v == null) {
			return null;
		}
		try {
			return LocalDate.parse(v).toString();
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException(label + "は yyyy-MM-dd で入力してください（" + v + "）");
		}
	}

	private static String trim(String s) {
		if (s == null) {
			return null;
		}
		String v = s.trim();
		return v.isEmpty() ? null : v;
	}
}
