package dev.application.analyze.common.service;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.domain.repository.bm.b011.BookCsvDataRepository;
import dev.application.domain.repository.master.b011.CountryLeagueSeasonMasterBatchRepository;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.util.ExecuteMainUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 試合日からシーズンを決める（csv_detail_manage のシーズン用。2026-11-25 追加）。
 * <p>B011（ExportCsvService.resolveSeasonSafely）と同じ決め方。B006（CoreStat）・B018（AnalyzeManualStat）でも
 * 同じ値になるようにここにまとめた。</p>
 * <ul>
 *   <li>シーズンマスタ（country_league_season_master）の開始日より前の試合 → 1つ前のシーズン
 *       （2026/2027 → 2025/2026、2026 → 2025）</li>
 *   <li>それ以外（期間内・終了日後の延期試合・試合日不明） → マスタの season_year</li>
 *   <li>国・リーグが分からない・マスタに行がない → 空</li>
 * </ul>
 * 試合日は static_data の最初の記録日（match_id 優先、無ければカテゴリ・チーム）。
 * <p>※ 統計テーブルの各 Writer のシーズン（CountryLeagueSeasonResolver: 今日がシーズン期間内のシーズン）とは別。</p>
 */
@Slf4j
@Component
public class MatchSeasonResolver {

	@Autowired
	private CountryLeagueSeasonMasterBatchRepository seasonMasterRepository;

	@Autowired
	private BookCsvDataRepository bookCsvDataRepository;

	/**
	 * @param dataCategory データカテゴリ（「国: リーグ - ラウンドN」）
	 * @param matchId match_id（無ければ null）
	 * @param home ホーム
	 * @param away アウェー
	 * @return シーズン（決められなければ空文字）
	 */
	public String resolve(String dataCategory, String matchId, String home, String away) {
		String country = "";
		String league = "";
		try {
			List<String> cl = ExecuteMainUtil.getCountryLeagueByRegex(dataCategory);
			if (cl != null && cl.size() >= 2) {
				country = safe(cl.get(0)).trim();
				league = safe(cl.get(1)).trim();
			}
		} catch (Exception e) {
			log.warn("[MatchSeasonResolver] 国・リーグを取り出せません dataCategory={}", dataCategory);
		}
		if (country.isEmpty() || league.isEmpty()) {
			return "";
		}
		try {
			CountryLeagueSeasonMasterEntity row = seasonMasterRepository.findSeasonRange(country, league);
			if (row == null) {
				return "";
			}
			String season = safe(row.getSeasonYear()).trim();
			LocalDate md = parseDateLoose(findMatchDate(dataCategory, matchId, home, away));
			LocalDate start = parseDateLoose(row.getStartSeasonDate());
			if (!season.isEmpty() && md != null && start != null && md.isBefore(start)) {
				String prev = previousSeason(season);
				if (!prev.isEmpty()) {
					log.info("[MatchSeasonResolver] 試合日がシーズン開始前のため前シーズン season={} -> {}, matchDate={}, start={}, {}",
							season, prev, md, start, dataCategory);
					return prev;
				}
			}
			return season;
		} catch (Exception e) {
			log.warn("[MatchSeasonResolver] シーズンを取れません country={}, league={}, e={}", country, league, e.toString());
			return "";
		}
	}

	private String findMatchDate(String dataCategory, String matchId, String home, String away) {
		try {
			String d = null;
			if (matchId != null && !matchId.isBlank()) {
				d = bookCsvDataRepository.findMatchDateByMatchId(matchId.trim());
			}
			if ((d == null || d.isBlank()) && !safe(home).isBlank() && !safe(away).isBlank()) {
				d = bookCsvDataRepository.findMatchDateByTeams(safe(dataCategory), home, away);
			}
			return (d == null || d.isBlank()) ? null : d.trim();
		} catch (Exception e) {
			log.warn("[MatchSeasonResolver] 試合日を取れません matchId={}, e={}", matchId, e.toString());
			return null;
		}
	}

	static LocalDate parseDateLoose(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim().replace('/', '-');
		if (t.length() < 10) {
			return null;
		}
		try {
			return LocalDate.parse(t.substring(0, 10));
		} catch (Exception e) {
			return null;
		}
	}

	static String previousSeason(String season) {
		Matcher m2 = Pattern.compile("^(\\d{4})\\s*/\\s*(\\d{4})$").matcher(season.trim());
		if (m2.matches()) {
			return (Integer.parseInt(m2.group(1)) - 1) + "/" + (Integer.parseInt(m2.group(2)) - 1);
		}
		Matcher m1 = Pattern.compile("^(\\d{4})$").matcher(season.trim());
		if (m1.matches()) {
			return String.valueOf(Integer.parseInt(m1.group(1)) - 1);
		}
		return "";
	}

	private static String safe(String s) {
		return s == null ? "" : s;
	}
}
