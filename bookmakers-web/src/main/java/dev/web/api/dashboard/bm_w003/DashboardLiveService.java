package dev.web.api.dashboard.bm_w003;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.api.dashboard.bm_w002.DashboardForecastService;
import dev.web.api.dashboard.matchDTO.DashboardMatchRow;
import dev.web.api.dashboard.support.DashboardSupport;
import dev.web.repository.bm.DashboardMatchRepository;
import lombok.RequiredArgsConstructor;

/**
 * トップ画面のライブ（試合一覧・注目の試合）
 *
 * <ul>
 *   <li>data テーブルの試合ごとの最新行のうち、直近 {@value #LIVE_WINDOW_HOURS} 時間以内に記録があり、
 *       終了・延期・中止でない試合をライブとする。</li>
 *   <li>注目の試合: ゴール数の見込みが一番多い試合（点差 1 以内の試合を優先）。</li>
 *   <li>今日（JST）終了した試合も集計（数字カード）用に返す（{@link #snapshot()}）。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class DashboardLiveService {

	/** この時間より前の記録で止まっている試合はライブとしない */
	static final int LIVE_WINDOW_HOURS = 4;

	private final DashboardMatchRepository matchRepository;

	private final DashboardForecastService forecastService;

	/** ライブ1試合（計算済み） */
	public static final class LiveItem {
		public DashboardMatchRow row;
		public String country;
		public String league;
		public String round;
		public DashboardSupport.MatchClock clock;
		public int homeScore;
		public int awayScore;
		public DashboardForecastService.Result result;
	}

	/** ライブと今日終了した試合 */
	public static final class Snapshot {
		public final List<LiveItem> live = new ArrayList<>();
		public final List<DashboardMatchRow> finishedToday = new ArrayList<>();
	}

	/**
	 * GET /v1/api/dashboard/live
	 */
	@Transactional(readOnly = true)
	public DashboardLiveResponse getLive(boolean loggedIn) {
		Snapshot snap = snapshot();

		Map<String, List<DashboardLiveMatchDTO>> byLeague = new LinkedHashMap<>();
		Map<String, String> countryOf = new LinkedHashMap<>();
		List<LiveItem> sorted = new ArrayList<>(snap.live);
		sorted.sort(Comparator.comparing((LiveItem i) -> leagueLabelOf(i)).thenComparing(i -> -i.clock.progress));
		for (LiveItem item : sorted) {
			String label = leagueLabelOf(item);
			byLeague.computeIfAbsent(label, k -> new ArrayList<>()).add(toDTO(item, loggedIn));
			countryOf.putIfAbsent(label, item.country);
		}
		List<DashboardLiveLeagueDTO> leagues = new ArrayList<>();
		byLeague.forEach((label, list) -> leagues.add(new DashboardLiveLeagueDTO(label, countryOf.get(label), list)));

		LiveItem featured = snap.live.stream()
				.max(Comparator.comparingDouble(i -> i.result.expectedTotal
						+ (Math.abs(i.homeScore - i.awayScore) <= 1 ? 1.0 : 0.0)))
				.orElse(null);

		return new DashboardLiveResponse(loggedIn, ZonedDateTime.now(DashboardSupport.JST).toOffsetDateTime().toString(),
				snap.live.size(), featured == null ? null : toDTO(featured, loggedIn), leagues);
	}

	/**
	 * ライブ（計算済み）と今日終了した試合。数字カード・お気に入りでも使う。
	 */
	@Transactional(readOnly = true)
	public Snapshot snapshot() {
		LocalDateTime now = LocalDateTime.now(DashboardSupport.JST);
		LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
		LocalDateTime liveFrom = now.minusHours(LIVE_WINDOW_HOURS);
		LocalDateTime since = todayStart.isBefore(liveFrom) ? todayStart : liveFrom;

		Snapshot snap = new Snapshot();
		for (DashboardMatchRow row : this.matchRepository.findLatestSince(since)) {
			LocalDateTime recorded = DashboardSupport.toLocalDateTime(row.getRecordTime());
			if (DashboardSupport.isFinished(row.getTimes())) {
				if (recorded == null || !recorded.isBefore(todayStart)) {
					snap.finishedToday.add(row);
				}
				continue;
			}
			if (DashboardSupport.isNotLive(row.getTimes())) {
				continue;
			}
			if (recorded != null && recorded.isBefore(liveFrom)) {
				continue;
			}
			snap.live.add(toItem(row));
		}
		return snap;
	}

	private LiveItem toItem(DashboardMatchRow row) {
		LiveItem i = new LiveItem();
		i.row = row;
		String[] cl = DashboardSupport.splitCategory(row.getDataCategory());
		i.country = cl == null ? "その他" : cl[0];
		i.league = cl == null ? DashboardSupport.norm(row.getDataCategory()) : cl[1];
		i.round = cl == null ? null : cl[2];
		i.clock = DashboardSupport.readClock(row.getTimes());
		i.homeScore = nz(DashboardSupport.toInt(row.getHomeScore()));
		i.awayScore = nz(DashboardSupport.toInt(row.getAwayScore()));
		i.result = this.forecastService.forecast(i.country, i.league, row.getHomeTeamName(), row.getAwayTeamName(),
				i.homeScore, i.awayScore, i.clock.remaining, true);
		return i;
	}

	/**
	 * 画面用に変換（未ログインは数値を null）
	 */
	public DashboardLiveMatchDTO toDTO(LiveItem i, boolean loggedIn) {
		DashboardMatchRow r = i.row;
		DashboardLiveMatchDTO dto = new DashboardLiveMatchDTO();
		dto.setSeq(r.getSeq());
		dto.setMatchId(r.getMatchId());
		dto.setCountry(i.country);
		dto.setLeague(i.league);
		dto.setLeagueLabel(leagueLabelOf(i));
		dto.setRoundLabel(i.round == null ? null : "ラウンド " + i.round);
		dto.setTimes(r.getTimes());
		dto.setStatus(i.clock.status);
		dto.setMinuteLabel(i.clock.label);
		dto.setProgress(i.clock.progress);
		dto.setHomeScore(i.homeScore);
		dto.setAwayScore(i.awayScore);
		dto.setHalftimeHomeScore(DashboardSupport.toInt(r.getHtHomeScore()));
		dto.setHalftimeAwayScore(DashboardSupport.toInt(r.getHtAwayScore()));
		dto.setHome(DashboardForecastService.toTeamDTO(r.getHomeTeamName(), DashboardSupport.toInt(r.getHomeRank()),
				i.result.homeTeam, loggedIn));
		dto.setAway(DashboardForecastService.toTeamDTO(r.getAwayTeamName(), DashboardSupport.toInt(r.getAwayRank()),
				i.result.awayTeam, loggedIn));
		dto.setHomeXg(DashboardSupport.toDouble(r.getHomeExp()));
		dto.setAwayXg(DashboardSupport.toDouble(r.getAwayExp()));
		dto.setHomeShotsOnTarget(DashboardSupport.toInt(r.getHomeShootIn()));
		dto.setAwayShotsOnTarget(DashboardSupport.toInt(r.getAwayShootIn()));
		dto.setHomePossession(DashboardSupport.toInt(r.getHomeDonation()));
		dto.setAwayPossession(DashboardSupport.toInt(r.getAwayDonation()));
		dto.setRecordTime(r.getRecordTime());
		dto.setForecast(DashboardForecastService.toForecastDTO(i.result, loggedIn));
		return dto;
	}

	private static String leagueLabelOf(LiveItem i) {
		return DashboardSupport.leagueLabel(i.country, i.league);
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}
}
