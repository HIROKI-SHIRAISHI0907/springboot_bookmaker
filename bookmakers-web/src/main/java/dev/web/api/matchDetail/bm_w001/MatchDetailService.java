package dev.web.api.matchDetail.bm_w001;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import dev.web.api.dashboard.bm_w002.DashboardForecastService;
import dev.web.api.dashboard.bm_w003.DashboardLiveService;
import dev.web.api.dashboard.support.DashboardSupport;
import dev.web.api.matchDetail.bm_w002.MatchVenueService;
import dev.web.api.matchDetail.matchDetailDTO.MatchGoalDTO;
import dev.web.api.matchDetail.matchDetailDTO.MatchInfoDTO;
import dev.web.api.matchDetail.matchDetailDTO.MatchRankChangeDTO;
import dev.web.api.matchDetail.matchDetailDTO.MatchStatDTO;
import dev.web.api.matchDetail.matchDetailDTO.MatchTimelinePointDTO;
import dev.web.repository.bm.MatchDetailRepository;
import lombok.RequiredArgsConstructor;

/**
 * 試合詳細（MatchDetail.tsx）
 * <ul>
 *   <li>static_data の同じ試合の全ての行（同じ match_id / 同じ seq_key の接頭辞）から作る。</li>
 *   <li>スコア・スタッツ・試合情報: 最新の行（試合情報は空でない一番新しい値）。</li>
 *   <li>試合の流れ: 試合時間ごとに1点（同じ時間は最後の行）。ゴールはスコアが増えたところ。</li>
 *   <li>順位の変動表: どちらかの順位が変わったところ（最初の行は試合開始時点）。</li>
 *   <li>スタジアムの地理情報: team_location_master（MatchVenueService）。</li>
 *   <li>勝敗確率（このままなら）: ライブ中の試合だけ。トップ画面のライブと同じ計算（未ログインは数値 null）。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class MatchDetailService {

	/** 試合の流れの点の上限（多いときは間引く） */
	static final int MAX_POINTS = 200;

	private static final Pattern NUM = Pattern.compile("-?\\d+(?:\\.\\d+)?");

	/** スタッツ比較に出す項目: key, ラベル, home 列, away 列 */
	private static final String[][] STATS = {
			{ "xg", "ゴール期待値（xG）", "home_exp", "away_exp" },
			{ "xgot", "枠内ゴール期待値", "home_in_goal_exp", "away_in_goal_exp" },
			{ "possession", "ボール支配率", "home_donation", "away_donation" },
			{ "shots", "シュート", "home_shoot_all", "away_shoot_all" },
			{ "shotsOnTarget", "枠内シュート", "home_shoot_in", "away_shoot_in" },
			{ "bigChance", "ビッグチャンス", "home_big_chance", "away_big_chance" },
			{ "corner", "コーナーキック", "home_corner", "away_corner" },
			{ "pass", "パス", "home_pass_count", "away_pass_count" },
			{ "save", "キーパーセーブ", "home_keeper_save", "away_keeper_save" },
			{ "foul", "ファウル", "home_foul", "away_foul" },
			{ "offside", "オフサイド", "home_offside", "away_offside" },
			{ "yellow", "イエローカード", "home_yellow_card", "away_yellow_card" },
			{ "red", "レッドカード", "home_red_card", "away_red_card" },
	};

	private final MatchDetailRepository repository;

	private final DashboardLiveService liveService;

	/** スタジアムの地理情報（team_location_master） */
	private final MatchVenueService venueService;

	public MatchDetailResponse getDetail(String seqKey, boolean loggedIn) {
		MatchDetailResponse res = new MatchDetailResponse();
		res.setSeqKey(seqKey);
		res.setLoggedIn(loggedIn);
		if (seqKey == null || seqKey.isBlank()) {
			return res;
		}
		List<Map<String, String>> rows = this.repository.findMatchRows(seqKey.trim());
		if (rows.isEmpty()) {
			return res;
		}
		res.setFound(true);
		Map<String, String> last = rows.get(rows.size() - 1);

		// ===== ヘッダー =====
		res.setMatchId(firstNonBlankFromEnd(rows, "match_id"));
		res.setHomeTeam(firstNonBlankFromEnd(rows, "home_team_name"));
		res.setAwayTeam(firstNonBlankFromEnd(rows, "away_team_name"));
		res.setHomeScore(toInt(last.get("home_score")));
		res.setAwayScore(toInt(last.get("away_score")));
		res.setHomeRank(toInt(firstNonBlankFromEnd(rows, "home_rank")));
		res.setAwayRank(toInt(firstNonBlankFromEnd(rows, "away_rank")));
		res.setMinuteLabel(last.get("times"));
		res.setRecordTime(last.get("record_time"));

		String category = firstValidCategory(rows);
		String[] cl = category == null ? null : DashboardSupport.splitCategory(category, null);
		if (cl != null) {
			res.setCountry(cl[0]);
			res.setLeague(cl[1]);
			res.setLeagueLabel(DashboardSupport.leagueLabel(cl[0], cl[1]));
			res.setRoundLabel(cl[2] == null ? null : "ラウンド " + cl[2]);
		} else {
			res.setLeagueLabel(category);
		}

		// ===== 勝敗確率（ライブ中だけ） =====
		DashboardLiveService.LiveItem liveItem = findLive(res);
		if (liveItem != null) {
			res.setStatus(isHalfTime(last.get("times")) ? "HT" : "LIVE");
			res.setForecast(DashboardForecastService.toForecastDTO(liveItem.result, loggedIn));
			if (liveItem.clock != null && liveItem.clock.label != null) {
				res.setMinuteLabel(liveItem.clock.label);
			}
		} else {
			res.setStatus(statusOf(last.get("times")));
		}

		// ===== スタッツ比較（最新の行。両方空の項目は出さない） =====
		for (String[] s : STATS) {
			String h = latestNonBlank(rows, s[2]);
			String a = latestNonBlank(rows, s[3]);
			if (h == null && a == null) {
				continue;
			}
			MatchStatDTO d = new MatchStatDTO();
			d.setKey(s[0]);
			d.setLabel(s[1]);
			d.setHome(h);
			d.setAway(a);
			d.setHomeValue(toDouble(h));
			d.setAwayValue(toDouble(a));
			res.getStats().add(d);
		}

		// ===== 試合の流れ（試合時間ごとに1点） =====
		Map<String, MatchTimelinePointDTO> byMinute = new LinkedHashMap<>();
		for (Map<String, String> r : rows) {
			Double minute = minuteOf(r);
			if (minute == null) {
				continue;
			}
			MatchTimelinePointDTO p = new MatchTimelinePointDTO();
			p.setMinute(minute);
			p.setLabel(r.get("times"));
			p.setHomeScore(toInt(r.get("home_score")));
			p.setAwayScore(toInt(r.get("away_score")));
			p.setHomeXg(toDouble(r.get("home_exp")));
			p.setAwayXg(toDouble(r.get("away_exp")));
			p.setHomeShots(toInt(r.get("home_shoot_all")));
			p.setAwayShots(toInt(r.get("away_shoot_all")));
			p.setHomeRank(toInt(r.get("home_rank")));
			p.setAwayRank(toInt(r.get("away_rank")));
			byMinute.put(String.valueOf(minute), p); // 同じ時間は後の行で上書き
		}
		List<MatchTimelinePointDTO> points = new ArrayList<>(byMinute.values());
		points.sort((x, y) -> Double.compare(x.getMinute(), y.getMinute()));
		res.setTimeline(thin(points, MAX_POINTS));

		// ===== ゴール（スコアが増えたところ） =====
		int ph = 0;
		int pa = 0;
		for (MatchTimelinePointDTO p : points) {
			Integer h = p.getHomeScore();
			Integer a = p.getAwayScore();
			if (h == null || a == null) {
				continue;
			}
			for (int g = ph + 1; g <= h; g++) {
				res.getGoals().add(goal(p, "H", g, Math.min(a, pa)));
			}
			for (int g = pa + 1; g <= a; g++) {
				res.getGoals().add(goal(p, "A", h, g));
			}
			// スコアが減った（取り消し・データの揺れ）ときはそこから数え直す
			ph = h;
			pa = a;
		}

		// ===== 順位の変動表 =====
		Integer lh = null;
		Integer la = null;
		for (MatchTimelinePointDTO p : points) {
			Integer h = p.getHomeRank();
			Integer a = p.getAwayRank();
			if (h == null && a == null) {
				continue;
			}
			boolean first = res.getRankChanges().isEmpty();
			boolean changed = (h != null && !h.equals(lh)) || (a != null && !a.equals(la));
			if (!first && !changed) {
				continue;
			}
			MatchRankChangeDTO c = new MatchRankChangeDTO();
			c.setMinute(p.getMinute());
			c.setLabel(first ? "試合開始時点" : p.getLabel());
			c.setHomeRank(h != null ? h : lh);
			c.setAwayRank(a != null ? a : la);
			c.setHomeDelta(first || h == null || lh == null ? null : h - lh);
			c.setAwayDelta(first || a == null || la == null ? null : a - la);
			c.setHomeScore(p.getHomeScore());
			c.setAwayScore(p.getAwayScore());
			res.getRankChanges().add(c);
			if (h != null) {
				lh = h;
			}
			if (a != null) {
				la = a;
			}
		}

		// ===== 試合情報（空でない一番新しい値） =====
		MatchInfoDTO info = res.getInfo();
		info.setStadium(latestNonBlank(rows, "studium"));
		info.setLocation(latestNonBlank(rows, "location"));
		info.setCapacity(latestNonBlank(rows, "capacity"));
		info.setAudience(latestNonBlank(rows, "audience"));
		info.setReferee(latestNonBlank(rows, "judge_member"));
		info.setWeather(latestNonBlank(rows, "weather"));
		info.setTemperature(latestNonBlank(rows, "temparature"));
		info.setHumidity(latestNonBlank(rows, "humid"));
		info.setHomeManager(latestNonBlank(rows, "home_manager"));
		info.setAwayManager(latestNonBlank(rows, "away_manager"));
		info.setHomeFormation(latestNonBlank(rows, "home_formation"));
		info.setAwayFormation(latestNonBlank(rows, "away_formation"));
		info.setGoalTime(latestNonBlank(rows, "goal_time"));
		info.setGoalMember(latestNonBlank(rows, "goal_team_member"));

		// ===== スタジアムの地理情報（スタジアム名 → 無ければホームチームのホームスタジアム） =====
		res.setVenue(this.venueService.find(info.getStadium(), info.getLocation(), res.getHomeTeam(), res.getCountry()));
		return res;
	}

	// ===================== ライブ =====================

	/** トップ画面のライブにこの試合があればそれ（seq_key の接頭辞 / match_id / チーム名で探す） */
	private DashboardLiveService.LiveItem findLive(MatchDetailResponse res) {
		String prefix = prefixOf(res.getSeqKey());
		String home = DashboardSupport.norm(res.getHomeTeam());
		String away = DashboardSupport.norm(res.getAwayTeam());
		try {
			for (DashboardLiveService.LiveItem i : this.liveService.snapshot().live) {
				String key = i.row.getSeqKey();
				if (key != null && (prefixOf(key).equals(prefix)
						|| (res.getMatchId() != null && key.startsWith(res.getMatchId() + "-")))) {
					return i;
				}
				if (home != null && home.equals(DashboardSupport.norm(i.row.getHomeTeamName()))
						&& away != null && away.equals(DashboardSupport.norm(i.row.getAwayTeamName()))) {
					return i;
				}
			}
		} catch (RuntimeException e) {
			// ライブが取れなくても詳細は出す
		}
		return null;
	}

	// ===================== 補助 =====================

	private static MatchGoalDTO goal(MatchTimelinePointDTO p, String side, int h, int a) {
		MatchGoalDTO g = new MatchGoalDTO();
		g.setMinute(p.getMinute());
		g.setLabel(p.getLabel());
		g.setSide(side);
		g.setHomeScore(h);
		g.setAwayScore(a);
		return g;
	}

	/** 点が多いときは等間隔に間引く（最初と最後・スコアが変わった点は残す） */
	private static List<MatchTimelinePointDTO> thin(List<MatchTimelinePointDTO> points, int max) {
		if (points.size() <= max) {
			return points;
		}
		List<MatchTimelinePointDTO> out = new ArrayList<>();
		double step = (double) points.size() / max;
		double next = 0;
		MatchTimelinePointDTO prev = null;
		for (int i = 0; i < points.size(); i++) {
			MatchTimelinePointDTO p = points.get(i);
			boolean scoreChanged = prev != null
					&& (!same(prev.getHomeScore(), p.getHomeScore()) || !same(prev.getAwayScore(), p.getAwayScore()));
			if (i == 0 || i == points.size() - 1 || i >= next || scoreChanged) {
				out.add(p);
				if (i >= next) {
					next += step;
				}
			}
			prev = p;
		}
		return out;
	}

	private static boolean same(Integer a, Integer b) {
		return a == null ? b == null : a.equals(b);
	}

	/** 試合時間（分）: time_sort_seconds があればそれ、無ければ times の数字（"45+2'" は 47） */
	private static Double minuteOf(Map<String, String> r) {
		Double sec = toDouble(r.get("time_sort_seconds"));
		if (sec != null && sec > 0) {
			return Math.round(sec / 60.0 * 10) / 10.0;
		}
		String t = r.get("times");
		if (t == null) {
			return null;
		}
		if (t.contains("終了")) {
			return 90.0;
		}
		if (isHalfTime(t)) {
			return 45.0;
		}
		Matcher m = Pattern.compile("(\\d+)\\s*\\+\\s*(\\d+)").matcher(t);
		if (m.find()) {
			return (double) (Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(2)));
		}
		Integer v = toInt(t);
		return v == null ? null : v.doubleValue();
	}

	private static boolean isHalfTime(String times) {
		return times != null && (times.contains("ハーフ") || times.equalsIgnoreCase("HT"));
	}

	private static String statusOf(String times) {
		if (times == null) {
			return "OTHER";
		}
		String t = times.replace(" ", "");
		if (t.contains("終了")) {
			return "FINISHED";
		}
		if (isHalfTime(t)) {
			return "HT";
		}
		return "OTHER";
	}

	/** "XXX: YYY …" などの仮の値を除いた、一番新しいカテゴリ */
	private static String firstValidCategory(List<Map<String, String>> rows) {
		for (int i = rows.size() - 1; i >= 0; i--) {
			String c = rows.get(i).get("data_category");
			if (c != null && !c.isBlank() && !c.trim().toUpperCase().startsWith("XXX")) {
				return c.trim();
			}
		}
		return null;
	}

	private static String latestNonBlank(List<Map<String, String>> rows, String col) {
		return firstNonBlankFromEnd(rows, col);
	}

	private static String firstNonBlankFromEnd(List<Map<String, String>> rows, String col) {
		for (int i = rows.size() - 1; i >= 0; i--) {
			String v = rows.get(i).get(col);
			if (v != null && !v.isBlank() && !"-".equals(v.trim())) {
				return v.trim();
			}
		}
		return null;
	}

	private static String prefixOf(String seqKey) {
		return seqKey == null ? "" : seqKey.replaceFirst("-\\d+$", "");
	}

	/** 最初の数字（"55%" → 55、"34%（31/90）" → 34、"1.23" → 1.23） */
	private static Double toDouble(String s) {
		if (s == null) {
			return null;
		}
		Matcher m = NUM.matcher(s.replace(",", ""));
		if (!m.find()) {
			return null;
		}
		try {
			return Double.valueOf(m.group());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Integer toInt(String s) {
		Double d = toDouble(s);
		return d == null ? null : (int) Math.round(d);
	}
}
