package dev.web.api.bm_a034;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.api.dashboard.auth.DashboardAuthResolver;
import dev.web.repository.master.LeagueTeamRepository;
import lombok.RequiredArgsConstructor;

/**
 * リーグ所属チーム付け替え設定（bm_a034）
 * <ul>
 *   <li>国を選ぶと、有効なリーグの一覧と、それぞれの所属チーム（country_league_master）を返す。</li>
 *   <li>リーグ名が変わったとき（新しいリーグ名はまずシーズンのマスタにだけ現れる）に、
 *       旧リーグ名のチームを新リーグ名へ付け替える。country_league_master.league だけを更新する。</li>
 *   <li>国のリーグが1つしかない場合は付け替えできない。</li>
 *   <li>移動先に同じ名前のチームがすでにいる場合は、そのチームは付け替えない（重複を作らない）。</li>
 *   <li>過去の所属が残っている・重複しているチームは、リーグから外せる（論理削除: del_flg = '1'）。</li>
 *   <li>重複チェック: 同じ国の有効なチームで、リンクの「/team/xxx/yyy/」が同じ行が2行以上あれば重複（別リーグでも同じリーグでも）。
 *       重複が1件でもあれば付け替えはできない。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class LeagueTeamService {

	/** リンクからチームのパス（/team/xxx/yyy/）を取り出す */
	private static final Pattern TEAM_PATH = Pattern.compile("/team/[^/?#]+/[^/?#]+/?");

	private final LeagueTeamRepository repository;

	private final DashboardAuthResolver authResolver;

	/** GET /api/admin/league-team/countries */
	@Transactional(readOnly = true)
	public List<LeagueTeamResponse.Country> getCountries() {
		return repository.findCountries();
	}

	/** GET /api/admin/league-team?country= */
	@Transactional(readOnly = true)
	public LeagueTeamResponse getLeagues(String country) {
		LeagueTeamResponse res = new LeagueTeamResponse();
		String c = trim(country);
		res.setCountry(c);
		if (c.isEmpty()) {
			return res;
		}

		Map<String, LeagueTeamResponse.League> byName = new LinkedHashMap<>();
		for (LeagueTeamResponse.League l : repository.findLeagues(c)) {
			byName.put(l.getLeague(), l);
		}
		for (LeagueTeamResponse.Team t : repository.findTeams(c)) {
			LeagueTeamResponse.League l = byName.get(t.getLeague());
			if (l != null) {
				l.getTeams().add(t);
			}
		}
		res.setLeagues(new ArrayList<>(byName.values()));
		res.setDuplicates(findDuplicates(res.getLeagues()));
		return res;
	}

	/**
	 * 重複チェック: リンクのチームのパス（/team/xxx/yyy/）が同じ行が2行以上あるもの。
	 * リンクが空の行は対象外。
	 */
	private static List<LeagueTeamResponse.Duplicate> findDuplicates(List<LeagueTeamResponse.League> leagues) {
		Map<String, List<LeagueTeamResponse.Team>> byLink = new LinkedHashMap<>();
		for (LeagueTeamResponse.League l : leagues) {
			for (LeagueTeamResponse.Team t : l.getTeams()) {
				String key = linkKey(t.getLink());
				if (key.isEmpty()) {
					continue;
				}
				byLink.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
			}
		}

		List<LeagueTeamResponse.Duplicate> list = new ArrayList<>();
		for (Map.Entry<String, List<LeagueTeamResponse.Team>> e : byLink.entrySet()) {
			List<LeagueTeamResponse.Team> rows = e.getValue();
			if (rows.size() < 2) {
				continue;
			}
			LeagueTeamResponse.Duplicate d = new LeagueTeamResponse.Duplicate();
			d.setLink(e.getKey());
			d.setTeam(rows.get(0).getTeam());
			for (LeagueTeamResponse.Team t : rows) {
				d.getLeagues().add(t.getLeague());
				d.getIds().add(t.getId());
			}
			d.setMessage(d.getTeam() + " チームが " + String.join(" と ", d.getLeagues()) + " に重複しています。");
			list.add(d);
		}
		return list;
	}

	/** リンク → 比較キー（/team/xxx/yyy/。小文字・末尾スラッシュあり）。取り出せなければ空 */
	static String linkKey(String link) {
		if (link == null || link.isBlank()) {
			return "";
		}
		Matcher m = TEAM_PATH.matcher(link.trim());
		if (!m.find()) {
			return "";
		}
		String p = m.group().toLowerCase();
		return p.endsWith("/") ? p : p + "/";
	}

	/** POST /api/admin/league-team/move */
	@Transactional
	public LeagueTeamMoveResponse move(LeagueTeamMoveRequest req) {
		LeagueTeamMoveResponse res = new LeagueTeamMoveResponse();
		String country = trim(req == null ? null : req.getCountry());
		String from = trim(req == null ? null : req.getFromLeague());
		String to = trim(req == null ? null : req.getToLeague());
		List<Integer> ids = req == null || req.getTeamIds() == null ? new ArrayList<>() : req.getTeamIds();

		if (country.isEmpty() || from.isEmpty() || to.isEmpty() || ids.isEmpty()) {
			return error(res, "国・移動元リーグ・移動先リーグ・チームを指定してください");
		}
		if (from.equals(to)) {
			return error(res, "移動元と移動先が同じリーグです");
		}

		LeagueTeamResponse current = getLeagues(country);
		if (current.getLeagues().size() < 2) {
			return error(res, country + " のリーグは1つしかないため、付け替えできません");
		}
		if (!current.getDuplicates().isEmpty()) {
			StringBuilder sb = new StringBuilder("重複しているチームがあるため付け替えできません。先に重複を解消してください。");
			for (LeagueTeamResponse.Duplicate d : current.getDuplicates()) {
				sb.append("\n").append(d.getMessage());
			}
			return error(res, sb.toString());
		}
		LeagueTeamResponse.League fromLeague = find(current, from);
		LeagueTeamResponse.League toLeague = find(current, to);
		if (fromLeague == null || toLeague == null) {
			return error(res, "リーグが見つかりません（画面を再取得してください）");
		}

		// 移動先にすでにいるチーム（リンクの /team/xxx/yyy/ が同じ、またはリンクが無ければ名前が同じ）は付け替えない
		Set<String> existingInTo = new HashSet<>();
		for (LeagueTeamResponse.Team t : toLeague.getTeams()) {
			existingInTo.add(sameTeamKey(t));
		}
		Set<Integer> requested = new HashSet<>(ids);
		List<Integer> moveIds = new ArrayList<>();
		for (LeagueTeamResponse.Team t : fromLeague.getTeams()) {
			if (!requested.contains(t.getId())) {
				continue;
			}
			if (existingInTo.contains(sameTeamKey(t))) {
				res.getSkippedTeams().add(t.getTeam());
				continue;
			}
			moveIds.add(t.getId());
		}

		int moved = 0;
		if (!moveIds.isEmpty()) {
			String operator = authResolver.currentUserId();
			moved = repository.moveTeams(country, from, to, moveIds,
					operator == null || operator.isBlank() ? "ADMIN" : operator);
		}

		res.setResponseCode("0");
		res.setMovedCount(moved);
		StringBuilder msg = new StringBuilder();
		msg.append(moved).append(" チームを「").append(from).append("」から「").append(to).append("」へ付け替えました");
		if (!res.getSkippedTeams().isEmpty()) {
			msg.append("（移動先に同じチームがいるため付け替えなかった: ")
					.append(String.join("、", res.getSkippedTeams())).append("）");
		}
		res.setMessage(msg.toString());
		return res;
	}

	/** POST /api/admin/league-team/remove（チームをリーグから外す = 論理削除） */
	@Transactional
	public LeagueTeamMoveResponse remove(LeagueTeamRemoveRequest req) {
		LeagueTeamMoveResponse res = new LeagueTeamMoveResponse();
		String country = trim(req == null ? null : req.getCountry());
		String league = trim(req == null ? null : req.getLeague());
		List<Integer> ids = req == null || req.getTeamIds() == null ? new ArrayList<>() : req.getTeamIds();

		if (country.isEmpty() || league.isEmpty() || ids.isEmpty()) {
			return error(res, "国・リーグ・チームを指定してください");
		}

		String operator = authResolver.currentUserId();
		int n = repository.removeTeams(country, league, ids,
				operator == null || operator.isBlank() ? "ADMIN" : operator);

		res.setResponseCode("0");
		res.setMovedCount(n);
		res.setMessage(n + " チームを「" + league + "」から外しました（論理削除）");
		return res;
	}

	private static LeagueTeamResponse.League find(LeagueTeamResponse r, String league) {
		for (LeagueTeamResponse.League l : r.getLeagues()) {
			if (league.equals(l.getLeague())) {
				return l;
			}
		}
		return null;
	}

	private static LeagueTeamMoveResponse error(LeagueTeamMoveResponse res, String message) {
		res.setResponseCode("9");
		res.setMessage(message);
		return res;
	}

	/** 同じチームかの比較キー（リンクがあればリンクのパス、無ければチーム名） */
	private static String sameTeamKey(LeagueTeamResponse.Team t) {
		String lk = linkKey(t.getLink());
		return lk.isEmpty() ? "name:" + teamKey(t.getTeam()) : "link:" + lk;
	}

	/** チーム名の比較キー（NFKC・空白除去・小文字） */
	private static String teamKey(String team) {
		if (team == null) {
			return "";
		}
		return Normalizer.normalize(team, Normalizer.Form.NFKC).replaceAll("\\s+", "").toLowerCase();
	}

	private static String trim(String s) {
		return s == null ? "" : s.trim();
	}
}
