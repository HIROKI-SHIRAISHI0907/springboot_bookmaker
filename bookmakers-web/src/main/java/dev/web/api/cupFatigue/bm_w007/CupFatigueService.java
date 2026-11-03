package dev.web.api.cupFatigue.bm_w007;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.api.bm_a036.CupCompetitionResponse;
import dev.web.api.bm_a036.CupScheduleStore;
import dev.web.repository.bm.CupFatigueRepository;
import dev.web.repository.master.CupCompetitionRepository;
import lombok.RequiredArgsConstructor;

/**
 * 直近の大会（ACL など）出場（bm_w007）
 * <ul>
 *   <li>リーグ戦の試合（今後の試合予定・試合中）のチームが、基準の日時（その試合の開始日時）の前後 days 日に
 *       大会の試合があるかを返す。「2 日前に ACL に出ていたので疲れているかも」を見るため。</li>
 *   <li>大会の試合 = S3 の b026_cup_schedule.json（日程スクレイパー）と json/b026_cup_result.json（結果スクレイパー）。
 *       チームはチームID（/team/xxx/&lt;ID&gt;/）で突き合わせる。</li>
 *   <li>大会に出るチーム = 大会出場チーム登録設定で選んだチーム、または日程・結果の targets にいるチーム
 *       （結果スクレイパーの対象 = json/b001_country_league.json（チーム名つき） のスクレイピング対象の国リーグのチーム）。</li>
 *   <li>出場選手 = B016 が登録した match_detail_lineup（出場=1）。出場時間は match_detail_member_stat にあればそれ、
 *       無ければ交代の分から計算する（90 分として）。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class CupFatigueService {

	private static final int DEFAULT_DAYS = 7;
	private static final int MAX_DAYS = 14;
	private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private static final Pattern NUM = Pattern.compile("(\\d+)");

	private final CupScheduleStore store;

	private final CupCompetitionRepository competitionRepository;

	private final CupFatigueRepository fatigueRepository;

	/** GET /api/cup-fatigue?country=&league=&home=&away=&at=&days= */
	@Transactional(readOnly = true)
	public CupFatigueResponse get(String country, String league, String home, String away, String at, Integer days) {
		int d = days == null || days <= 0 ? DEFAULT_DAYS : Math.min(days, MAX_DAYS);
		LocalDateTime base = parseTime(at);

		CupFatigueResponse res = new CupFatigueResponse();
		res.setDays(d);
		res.setBaseTime(base.format(MINUTE));

		List<CupCompetitionResponse.Team> cupTeams = competitionRepository.findTeams(null);
		Map<Integer, String> competitionNames = new HashMap<>();
		for (CupCompetitionResponse.Competition c : competitionRepository.findCompetitions()) {
			competitionNames.put(c.getId(), c.getCompetitionName());
		}
		List<CupScheduleStore.ScheduleItem> schedule = store.getSchedule();

		res.setHome(team(trim(country), trim(league), trim(home), base, d, cupTeams, competitionNames, schedule));
		res.setAway(team(trim(country), trim(league), trim(away), base, d, cupTeams, competitionNames, schedule));
		res.setFound(res.getHome().isCupTeam() || res.getAway().isCupTeam());

		// 出場選手（過去の試合だけ）
		List<CupFatigueResponse.CupMatch> past = new ArrayList<>(res.getHome().getRecent());
		past.addAll(res.getAway().getRecent());
		fillPlayers(past);
		return res;
	}

	private CupFatigueResponse.TeamFatigue team(String country, String league, String team, LocalDateTime base, int days,
			List<CupCompetitionResponse.Team> cupTeams, Map<Integer, String> competitionNames,
			List<CupScheduleStore.ScheduleItem> schedule) {
		CupFatigueResponse.TeamFatigue tf = new CupFatigueResponse.TeamFatigue();
		tf.setTeam(team);
		if (team.isEmpty()) {
			return tf;
		}
		// 大会に出るチームか（2026-11-03 変更: どちらかに当てはまれば対象）
		//  ① 大会出場チーム登録設定で選んだ出場チーム（国・チーム名が同じ行。リーグが変わっていてもよい）
		//  ② 日程・結果の JSON の targets にいるチーム（結果スクレイパーは json/b001_country_league.json（チーム名つき） =
		//     スクレイピング対象の国リーグのチームが出た試合を拾う。画面で選んでいなくても対象になる）
		List<CupCompetitionResponse.Team> mine = cupTeams.stream()
				.filter(t -> team.equals(t.getTeam()) && (country.isEmpty() || country.equals(t.getCountry())))
				.collect(Collectors.toList());
		Set<String> competitions = new LinkedHashSet<>();
		Set<String> teamIds = new HashSet<>();
		for (CupCompetitionResponse.Team t : mine) {
			String name = competitionNames.getOrDefault(t.getCompetitionId(), "");
			if (!name.isEmpty()) {
				competitions.add(name);
			}
			String id = CupScheduleStore.teamIdOfLink(t.getLink());
			if (!id.isEmpty()) {
				teamIds.add(id);
			}
		}
		boolean inTargets = false;
		for (CupScheduleStore.ScheduleItem it : schedule) {
			for (CupScheduleStore.ScheduleTarget t : it.targets) {
				if (team.equals(t.team) && (country.isEmpty() || country.equals(t.country))) {
					inTargets = true;
					String name = it.competitionName.isEmpty()
							? competitionNames.getOrDefault(it.competitionId, "")
							: it.competitionName;
					if (!name.isEmpty()) {
						competitions.add(name);
					}
					if (t.teamId != null && !t.teamId.isEmpty()) {
						teamIds.add(t.teamId);
					}
				}
			}
		}
		if (mine.isEmpty() && !inTargets) {
			return tf;
		}
		tf.setCupTeam(true);
		tf.setCompetitions(String.join("、", competitions));
		if (teamIds.isEmpty() && !league.isEmpty()) {
			String id = CupScheduleStore.teamIdOfLink(competitionRepository.findTeamLink(country, league, team));
			if (!id.isEmpty()) {
				teamIds.add(id);
			}
		}

		LocalDateTime from = base.minusDays(days);
		LocalDateTime to = base.plusDays(days);
		for (CupScheduleStore.ScheduleItem it : schedule) {
			String side = sideOf(it, teamIds, country, team);
			if (side == null) {
				continue;
			}
			LocalDateTime k = it.kickoffTime();
			if (k == null || k.isBefore(from) || k.isAfter(to) || k.equals(base)) {
				continue;
			}
			CupFatigueResponse.CupMatch m = new CupFatigueResponse.CupMatch();
			m.setMid(it.mid);
			m.setCompetitionName(it.competitionName.isEmpty()
					? competitionNames.getOrDefault(it.competitionId, "")
					: it.competitionName);
			m.setRound(it.round);
			m.setKickoff(k.format(MINUTE));
			m.setSide(side);
			m.setOpponent("HOME".equals(side) ? it.away : it.home);
			long hours = Math.abs(Duration.between(k, base).toHours());
			m.setHours(hours);
			m.setDays(Math.abs(ChronoUnit.DAYS.between(k.toLocalDate(), base.toLocalDate())));
			if (k.isBefore(base)) {
				tf.getRecent().add(m);
			} else {
				tf.getUpcoming().add(m);
			}
		}
		tf.getRecent().sort(Comparator.comparing(CupFatigueResponse.CupMatch::getKickoff).reversed());
		tf.getUpcoming().sort(Comparator.comparing(CupFatigueResponse.CupMatch::getKickoff));
		return tf;
	}

	/** この試合にチームが出るなら HOME / AWAY、出ないなら null */
	private static String sideOf(CupScheduleStore.ScheduleItem it, Set<String> teamIds, String country, String team) {
		if (!teamIds.isEmpty()) {
			if (teamIds.contains(it.homeTeamId)) {
				return "HOME";
			}
			if (teamIds.contains(it.awayTeamId)) {
				return "AWAY";
			}
		}
		for (CupScheduleStore.ScheduleTarget t : it.targets) {
			if (team.equals(t.team) && (country.isEmpty() || country.equals(t.country))) {
				return "AWAY".equalsIgnoreCase(t.side) ? "AWAY" : "HOME";
			}
		}
		return null;
	}

	/** 過去の大会の試合に、スコアと出場選手を入れる */
	private void fillPlayers(List<CupFatigueResponse.CupMatch> matches) {
		if (matches.isEmpty()) {
			return;
		}
		Set<String> ids = matches.stream().map(CupFatigueResponse.CupMatch::getMid).collect(Collectors.toCollection(LinkedHashSet::new));
		Map<String, Map<String, Object>> info = new HashMap<>();
		for (Map<String, Object> r : fatigueRepository.findMatches(ids)) {
			info.put(str(r.get("match_id")), r);
		}
		Map<String, List<Map<String, Object>>> members = fatigueRepository.findPlayedMembers(ids).stream()
				.collect(Collectors.groupingBy(r -> str(r.get("match_id")) + "\t" + str(r.get("side")).toUpperCase()));

		for (CupFatigueResponse.CupMatch m : matches) {
			Map<String, Object> mi = info.get(m.getMid());
			if (mi == null) {
				continue; // まだ登録されていない
			}
			m.setRegistered(true);
			Integer hs = toInt(mi.get("home_score"));
			Integer as = toInt(mi.get("away_score"));
			boolean home = "HOME".equals(m.getSide());
			m.setTeamScore(home ? hs : as);
			m.setOpponentScore(home ? as : hs);

			List<Map<String, Object>> rows = members.getOrDefault(m.getMid() + "\t" + m.getSide(), List.of());
			List<CupFatigueResponse.Player> starters = new ArrayList<>();
			List<CupFatigueResponse.Player> subs = new ArrayList<>();
			for (Map<String, Object> r : rows) {
				CupFatigueResponse.Player p = new CupFatigueResponse.Player();
				p.setPlayerName(str(r.get("player_name")));
				p.setJerseyNumber(str(r.get("jersey_number")));
				p.setRole(str(r.get("role")));
				p.setLineupType(str(r.get("lineup_type")));
				p.setSubInMinute(str(r.get("sub_in_minute")));
				p.setSubOutMinute(str(r.get("sub_out_minute")));
				p.setRating(str(r.get("final_rating")));
				boolean starter = "スタメン".equals(p.getLineupType());
				Integer minutes = toInt(r.get("match_minutes_played"));
				if (minutes == null) {
					minutes = minutesFromSubs(starter, p.getSubInMinute(), p.getSubOutMinute());
				}
				p.setMinutes(minutes);
				(starter ? starters : subs).add(p);
			}
			m.setStarterCount(starters.size());
			m.setPlayedCount(starters.size() + subs.size());
			m.getPlayers().addAll(starters);
			m.getPlayers().addAll(subs);
		}
	}

	/** 交代の分から出場時間を出す（90 分の試合として。延長は考えない） */
	private static Integer minutesFromSubs(boolean starter, String subIn, String subOut) {
		Integer in = starter ? Integer.valueOf(0) : minuteOf(subIn);
		if (in == null) {
			return null;
		}
		Integer out = minuteOf(subOut);
		int end = out == null ? 90 : Math.min(out, 120);
		return Math.max(0, end - in);
	}

	/** "63'" / "90+2'" → 63 / 90 */
	private static Integer minuteOf(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		Matcher m = NUM.matcher(s);
		return m.find() ? Integer.valueOf(m.group(1)) : null;
	}

	/** "2026-11-05 19:00" / "2026-11-05T19:00:00+09:00" / "2026-11-05" → JST の日時。読めなければ今 */
	private static LocalDateTime parseTime(String at) {
		String s = trim(at).replace('T', ' ');
		try {
			if (s.length() >= 16) {
				return LocalDateTime.parse(s.substring(0, 16), MINUTE);
			}
			if (s.length() >= 10) {
				return LocalDateTime.parse(s.substring(0, 10) + " 12:00", MINUTE);
			}
		} catch (Exception e) {
			// 下へ
		}
		return LocalDateTime.now(CupScheduleStore.JST).truncatedTo(ChronoUnit.MINUTES);
	}

	private static Integer toInt(Object o) {
		if (o == null) {
			return null;
		}
		if (o instanceof Number) {
			return ((Number) o).intValue();
		}
		Matcher m = NUM.matcher(o.toString());
		return m.find() ? Integer.valueOf(m.group(1)) : null;
	}

	private static String str(Object o) {
		return o == null ? "" : o.toString().trim();
	}

	private static String trim(String s) {
		return s == null ? "" : s.trim();
	}
}
