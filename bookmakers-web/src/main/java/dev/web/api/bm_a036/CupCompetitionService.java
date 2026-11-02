package dev.web.api.bm_a036;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.api.dashboard.auth.DashboardAuthResolver;
import dev.web.repository.bm.MatchDetailDataWebRepository;
import dev.web.repository.master.CupCompetitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 大会出場チーム登録設定（bm_a036）
 * <ul>
 *   <li>大会（ACL など）を登録する。Flashscore の大会ページ URL から、日程スクレイパーが日程を取る。</li>
 *   <li>大会に出るチームを、スクレイピング対象（b001_country_league.json）のリーグのチームから選んで登録する。</li>
 *   <li>登録・変更のたびに、大会と出場チームを S3 の b026_cup_competition.json へ出力する（日程スクレイパーの入力）。</li>
 *   <li>日程（b026_cup_schedule.json）と、出場選手の取得状況（依頼済み・登録済み）を返す。</li>
 *   <li>出場選手は「試合日の翌日」に Lambda（cup-schedule-runner）が B016 の試合詳細スクレイピングへ依頼する。
 *       取れなかった試合は「取り直す」で、もう一度依頼できる。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CupCompetitionService {

	/** 大会ページの URL の始まり */
	static final String URL_PREFIX = "https://www.flashscore.co.jp/soccer/";

	private final CupCompetitionRepository repository;

	private final CupScheduleStore store;

	private final MatchDetailDataWebRepository matchDetailDataWebRepository;

	private final DashboardAuthResolver authResolver;

	/** GET /api/admin/cup-competition */
	@Transactional(readOnly = true)
	public List<CupCompetitionResponse.Competition> getCompetitions() {
		return repository.findCompetitions();
	}

	/** POST /api/admin/cup-competition/save */
	@Transactional
	public CupCompetitionResponse.Result save(CupCompetitionRequest.Save req) {
		String name = trim(req.getCompetitionName());
		String url = normalizeUrl(req.getCompetitionUrl());
		if (name.isEmpty()) {
			return CupCompetitionResponse.Result.ng("大会名を入力してください");
		}
		if (url.isEmpty() || !url.startsWith(URL_PREFIX)) {
			return CupCompetitionResponse.Result.ng(
					"大会ページの URL は https://www.flashscore.co.jp/soccer/ から始まる URL を入力してください"
							+ "（例: https://www.flashscore.co.jp/soccer/asia/afc-champions-league/）");
		}
		if (!repository.findOtherIdsByUrl(url, req.getId()).isEmpty()) {
			return CupCompetitionResponse.Result.ng("同じ URL の大会がすでに登録されています: " + url);
		}
		String region = trim(req.getRegion());
		String season = trim(req.getSeasonYear());
		int order = req.getDispOrder() == null ? 0 : req.getDispOrder();
		String scrapeFlg = "0".equals(trim(req.getScrapeFlg())) ? "0" : "1";
		String operator = operator();

		CupCompetitionResponse.Result r;
		if (req.getId() == null) {
			Integer id = repository.insertCompetition(name, region, url, season, order, scrapeFlg, operator);
			r = CupCompetitionResponse.Result.ok("大会「" + name + "」を登録しました", 1);
			r.setId(id);
		} else {
			int n = repository.updateCompetition(req.getId(), name, region, url, season, order, scrapeFlg, operator);
			if (n == 0) {
				return CupCompetitionResponse.Result.ng("大会が見つかりません（削除された可能性があります）");
			}
			r = CupCompetitionResponse.Result.ok("大会「" + name + "」を更新しました", n);
			r.setId(req.getId());
		}
		exportJson(r);
		return r;
	}

	/** POST /api/admin/cup-competition/delete */
	@Transactional
	public CupCompetitionResponse.Result delete(CupCompetitionRequest.Delete req) {
		if (req.getId() == null) {
			return CupCompetitionResponse.Result.ng("大会が指定されていません");
		}
		int n = repository.deleteCompetition(req.getId(), operator());
		if (n == 0) {
			return CupCompetitionResponse.Result.ng("大会が見つかりません（削除済みの可能性があります）");
		}
		CupCompetitionResponse.Result r = CupCompetitionResponse.Result.ok("大会を削除しました（論理削除）", n);
		exportJson(r);
		return r;
	}

	/** GET /api/admin/cup-competition/teams?competitionId= */
	@Transactional(readOnly = true)
	public List<CupCompetitionResponse.Team> getTeams(Integer competitionId) {
		if (competitionId == null) {
			return new ArrayList<>();
		}
		return repository.findTeams(competitionId);
	}

	/** GET /api/admin/cup-competition/candidates  スクレイピング対象のリーグのチーム（国 → リーグ → チーム） */
	@Transactional(readOnly = true)
	public List<CupCompetitionResponse.CandidateCountry> getCandidates() {
		Map<String, CupCompetitionResponse.CandidateCountry> countries = new LinkedHashMap<>();
		Map<String, CupCompetitionResponse.CandidateLeague> leagues = new LinkedHashMap<>();
		for (CupCompetitionResponse.CandidateTeam t : repository.findCandidateTeams()) {
			CupCompetitionResponse.CandidateCountry c = countries.computeIfAbsent(t.getCountry(), k -> {
				CupCompetitionResponse.CandidateCountry x = new CupCompetitionResponse.CandidateCountry();
				x.setCountry(k);
				return x;
			});
			CupCompetitionResponse.CandidateLeague l = leagues.computeIfAbsent(t.getCountry() + "\t" + t.getLeague(),
					k -> {
						CupCompetitionResponse.CandidateLeague x = new CupCompetitionResponse.CandidateLeague();
						x.setLeague(t.getLeague());
						c.getLeagues().add(x);
						return x;
					});
			l.getTeams().add(t);
		}
		return new ArrayList<>(countries.values());
	}

	/** POST /api/admin/cup-competition/teams/add */
	@Transactional
	public CupCompetitionResponse.Result addTeams(CupCompetitionRequest.AddTeams req) {
		if (req.getCompetitionId() == null) {
			return CupCompetitionResponse.Result.ng("大会が指定されていません");
		}
		if (req.getTeams() == null || req.getTeams().isEmpty()) {
			return CupCompetitionResponse.Result.ng("チームが選ばれていません");
		}
		boolean exists = repository.findCompetitions().stream().anyMatch(c -> c.getId().equals(req.getCompetitionId()));
		if (!exists) {
			return CupCompetitionResponse.Result.ng("大会が見つかりません（削除された可能性があります）");
		}
		// 候補（スクレイピング対象のリーグのチーム）にあるものだけ登録する
		Set<String> candidates = repository.findCandidateTeams().stream()
				.map(t -> key(t.getCountry(), t.getLeague(), t.getTeam()))
				.collect(Collectors.toSet());
		String operator = operator();
		int n = 0;
		List<String> skipped = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (CupCompetitionRequest.TeamKey t : req.getTeams()) {
			String country = trim(t.getCountry());
			String league = trim(t.getLeague());
			String team = trim(t.getTeam());
			String k = key(country, league, team);
			if (!seen.add(k)) {
				continue;
			}
			if (!candidates.contains(k)) {
				skipped.add(team + "（" + country + " / " + league + "）");
				continue;
			}
			n += repository.upsertTeam(req.getCompetitionId(), country, league, team,
					repository.findTeamLink(country, league, team), operator);
		}
		String msg = n + " チームを出場チームに登録しました";
		if (!skipped.isEmpty()) {
			msg += "。スクレイピング対象のリーグのチームではないため登録しなかったチーム: " + String.join("、", skipped);
		}
		CupCompetitionResponse.Result r = CupCompetitionResponse.Result.ok(msg, n);
		exportJson(r);
		return r;
	}

	/** POST /api/admin/cup-competition/teams/remove */
	@Transactional
	public CupCompetitionResponse.Result removeTeams(CupCompetitionRequest.RemoveTeams req) {
		if (req.getCompetitionId() == null || req.getIds() == null || req.getIds().isEmpty()) {
			return CupCompetitionResponse.Result.ng("チームが選ばれていません");
		}
		int n = repository.removeTeams(req.getCompetitionId(), req.getIds(), operator());
		CupCompetitionResponse.Result r = CupCompetitionResponse.Result.ok(n + " チームを出場チームから外しました（論理削除）", n);
		exportJson(r);
		return r;
	}

	/** POST /api/admin/cup-competition/export  大会と出場チームの JSON を出力し直す */
	@Transactional(readOnly = true)
	public CupCompetitionResponse.Result export() {
		CupCompetitionResponse.Result r = CupCompetitionResponse.Result.ok("", 0);
		exportJson(r);
		if ("0".equals(r.getResponseCode())) {
			r.setMessage("s3://" + store.getBucket() + "/" + CupScheduleStore.COMPETITION_KEY + " を出力しました");
		}
		return r;
	}

	/** GET /api/admin/cup-competition/schedule?competitionId= */
	@Transactional(readOnly = true)
	public CupCompetitionResponse.Schedule getSchedule(Integer competitionId) {
		CupCompetitionResponse.Schedule res = new CupCompetitionResponse.Schedule();
		store.invalidate();
		List<CupScheduleStore.ScheduleItem> items = store.getSchedule();
		res.setExists(store.scheduleExists());
		res.setGeneratedAt(store.getScheduleGeneratedAt());

		List<CupScheduleStore.ScheduleItem> targets = items.stream()
				.filter(it -> competitionId == null || competitionId.equals(it.competitionId))
				.collect(Collectors.toList());
		Set<String> registered = matchDetailDataWebRepository.findRegisteredMatchIds(
				targets.stream().map(it -> it.mid).collect(Collectors.toList()));
		Map<String, String> requests = store.getRequests();
		LocalDateTime now = LocalDateTime.now(CupScheduleStore.JST);

		for (CupScheduleStore.ScheduleItem it : targets) {
			CupCompetitionResponse.ScheduleMatch m = new CupCompetitionResponse.ScheduleMatch();
			m.setMid(it.mid);
			m.setUrl(it.url);
			m.setDate(it.date);
			m.setKickoff(it.kickoff);
			m.setRound(it.round);
			m.setHome(it.home);
			m.setAway(it.away);
			m.setTargetTeams(it.targets.stream().map(t -> t.team).distinct().collect(Collectors.joining("、")));
			String req = requests.get(it.mid);
			m.setStatus(store.statusOf(it, registered.contains(it.mid), req, now));
			m.setRequestedAt(req == null || CupScheduleStore.RETRY.equals(req) ? null : req);
			res.getMatches().add(m);
		}
		return res;
	}

	/** POST /api/admin/cup-competition/schedule/retry */
	public CupCompetitionResponse.Result retry(CupCompetitionRequest.Retry req) {
		if (req.getMids() == null || req.getMids().isEmpty()) {
			return CupCompetitionResponse.Result.ng("試合が選ばれていません");
		}
		try {
			int n = store.markRetry(req.getMids());
			return CupCompetitionResponse.Result.ok(
					n + " 試合を取り直しにしました。次の Lambda（cup-schedule-runner）の実行で、出場選手の取得を依頼します", n);
		} catch (Exception e) {
			log.error("[A036] 取り直しにできませんでした: s3://{}/{}", store.getBucket(), CupScheduleStore.REQUEST_KEY, e);
			return CupCompetitionResponse.Result.ng("取り直しにできませんでした: " + e.getMessage()
					+ "（web のタスクロールに s3:GetObject / s3:PutObject があるか確認してください）");
		}
	}

	/**
	 * 大会と出場チームの JSON を出力し直す（大会一括アップロードの後に呼ぶ）
	 * @return 出力できなければメッセージ、できれば null
	 */
	String exportCompetitionJson() {
		CupCompetitionResponse.Result r = CupCompetitionResponse.Result.ok("", 0);
		exportJson(r);
		return "0".equals(r.getResponseCode()) ? null : r.getMessage();
	}

	/**
	 * 大会URLの一覧を S3 の json/b026_cup_url.json へ出力する（大会一括アップロード画面のボタン。2026-11-01 追加）。
	 * <p>対象は有効（del_flg='0'）で「日程を取る」（scrape_flg='1'）の大会。結果タブのスクレイパー
	 * （CupResultData_aws_S3.py）がこの URL の results/ を読む。</p>
	 * <pre>
	 * {"generatedAt": "2026-11-01 12:00:00", "count": 1,
	 *  "competitions": [{"competitionId": 1, "competitionName": "AFCチャンピオンズリーグエリート",
	 *                    "url": "https://www.flashscore.co.jp/soccer/asia/afc-champions-league/"}]}
	 * </pre>
	 */
	public CupCompetitionResponse.Result exportUrlJson() {
		List<Map<String, Object>> competitions = new ArrayList<>();
		for (CupCompetitionResponse.Competition c : repository.findCompetitions()) {
			if (!"1".equals(c.getScrapeFlg())) {
				continue;
			}
			Map<String, Object> cm = new LinkedHashMap<>();
			cm.put("competitionId", c.getId());
			cm.put("competitionName", c.getCompetitionName());
			cm.put("url", c.getCompetitionUrl());
			competitions.add(cm);
		}
		if (competitions.isEmpty()) {
			return CupCompetitionResponse.Result.ng("出力する大会がありません（先に大会を登録してください）。");
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("generatedAt", LocalDateTime.now(CupScheduleStore.JST).format(CupScheduleStore.DATETIME));
		body.put("count", competitions.size());
		body.put("competitions", competitions);
		try {
			store.putUrlJson(body);
		} catch (Exception e) {
			log.error("[A036] 大会URLの一覧を出力できませんでした: s3://{}/{}", store.getBucket(), CupScheduleStore.URL_KEY, e);
			return CupCompetitionResponse.Result.ng("S3（" + store.getBucket() + "）へ JSON を出力できませんでした: "
					+ e.getMessage() + "（バケットがあるか、web のタスクロールに s3:PutObject があるか確認してください）");
		}
		return CupCompetitionResponse.Result.ok("s3://" + store.getBucket() + "/" + CupScheduleStore.URL_KEY
				+ " に " + competitions.size() + " 大会の URL を出力しました", competitions.size());
	}

	/**
	 * 大会と出場チームを S3 の b026_cup_competition.json へ出力する。
	 * 出力に失敗しても DB の更新は成功のままにして、メッセージに書き足す（「JSON を出力し直す」で出し直せる）。
	 */
	private void exportJson(CupCompetitionResponse.Result r) {
		try {
			Map<Integer, List<CupCompetitionResponse.Team>> teamsByCompetition = repository.findTeams(null).stream()
					.collect(Collectors.groupingBy(CupCompetitionResponse.Team::getCompetitionId, LinkedHashMap::new,
							Collectors.toList()));
			List<Map<String, Object>> competitions = new ArrayList<>();
			for (CupCompetitionResponse.Competition c : repository.findCompetitions()) {
				if (!"1".equals(c.getScrapeFlg())) {
					continue;
				}
				List<Map<String, Object>> teams = new ArrayList<>();
				for (CupCompetitionResponse.Team t : teamsByCompetition.getOrDefault(c.getId(), List.of())) {
					Map<String, Object> tm = new LinkedHashMap<>();
					tm.put("country", t.getCountry());
					tm.put("league", t.getLeague());
					tm.put("team", t.getTeam());
					tm.put("link", t.getLink() == null ? "" : t.getLink());
					tm.put("teamId", CupScheduleStore.teamIdOfLink(t.getLink()));
					teams.add(tm);
				}
				Map<String, Object> cm = new LinkedHashMap<>();
				cm.put("competitionId", c.getId());
				cm.put("competitionName", c.getCompetitionName());
				cm.put("region", c.getRegion() == null ? "" : c.getRegion());
				cm.put("url", c.getCompetitionUrl());
				cm.put("seasonYear", c.getSeasonYear() == null ? "" : c.getSeasonYear());
				cm.put("teams", teams);
				competitions.add(cm);
			}
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("generatedAt", LocalDateTime.now(CupScheduleStore.JST).format(CupScheduleStore.DATETIME));
			body.put("competitions", competitions);
			store.putCompetitionJson(body);
		} catch (Exception e) {
			log.error("[A036] 大会と出場チームの JSON を出力できませんでした: s3://{}/{}", store.getBucket(),
					CupScheduleStore.COMPETITION_KEY, e);
			r.setResponseCode("0".equals(r.getResponseCode()) && r.getMessage().isEmpty() ? "9" : r.getResponseCode());
			r.setMessage((r.getMessage().isEmpty() ? "" : r.getMessage() + "。ただし、")
					+ "S3（" + store.getBucket() + "）へ JSON を出力できませんでした: " + e.getMessage()
					+ "（バケットがあるか、web のタスクロールに s3:PutObject があるか確認し、「JSON を出力し直す」を押してください）");
		}
	}

	private String operator() {
		String op = authResolver.currentUserId();
		return op == null || op.isBlank() ? "ADMIN" : op;
	}

	private static String key(String country, String league, String team) {
		return trim(country) + "\t" + trim(league) + "\t" + trim(team);
	}

	/** URL の前後の空白を取り、? 以降を外し、最後を / にする */
	static String normalizeUrl(String url) {
		String u = trim(url);
		int q = u.indexOf('?');
		if (q >= 0) {
			u = u.substring(0, q);
		}
		int h = u.indexOf('#');
		if (h >= 0) {
			u = u.substring(0, h);
		}
		// 日程・結果のページを貼られたら大会のトップにする
		u = u.replaceAll("/(fixtures|results|standings|draw|archive)/?$", "/");
		if (!u.isEmpty() && !u.endsWith("/")) {
			u = u + "/";
		}
		return u;
	}

	private static String trim(String s) {
		return s == null ? "" : s.trim();
	}
}
