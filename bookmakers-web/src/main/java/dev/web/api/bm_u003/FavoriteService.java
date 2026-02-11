package dev.web.api.bm_u003;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.api.bm_a002.CountryLeagueSeasonDTO;
import dev.web.api.bm_a003.CountryLeagueDTO;
import dev.web.repository.master.CountryLeagueMasterWebRepository;
import dev.web.repository.master.CountryLeagueSeasonMasterWebRepository;
import dev.web.repository.user.FavoriteRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FavoriteService {

	private final FavoriteRepository favoriteRepository;

	private final CountryLeagueMasterWebRepository countryLeagueMasterWebRepository;

	private final CountryLeagueSeasonMasterWebRepository countryLeagueSeasonMasterWebRepository;

	public FavoriteScope getScope(Long userId) {
		return favoriteRepository.findFavoriteScope(userId);
	}

	/**
	 * お気に入りをまとめて登録（country / league / team を item の内容で判定）
	 */
	@Transactional
	public FavoriteResponse upsert(FavoriteInsertRequest req) {
		FavoriteResponse res = new FavoriteResponse();

		try {
			requireUser(req);
		} catch (Exception e) {
			res.setResponseCode("400");
			res.setMessage("必須項目が未入力です。");
			return res;
		}

		if (req.getItems() == null || req.getItems().isEmpty()) {
			res.setResponseCode("400");
			res.setMessage("必須項目が未入力です。");
			return res;
		}

		String operatorId = normalize(req.getOperatorId());
		if (operatorId.isEmpty())
			operatorId = "system";

		java.util.LinkedHashSet<FavKey> keys = new java.util.LinkedHashSet<>();

		for (FavoriteItem item : req.getItems()) {

			String country = normalize(item.getCountry());
			String league = normalize(item.getLeague());
			String team = normalize(item.getTeam());

			if (country.isEmpty()) {
				res.setResponseCode("400");
				res.setMessage("必須項目が未入力です。（country）");
				return res;
			}

			// team ありなら league 必須
			if (!team.isEmpty() && league.isEmpty()) {
				res.setResponseCode("400");
				res.setMessage("必須項目が未入力です。(league)");
				return res;
			}

			// 親を自動補完
			keys.add(new FavKey(1, country, "", ""));
			if (!league.isEmpty()) {
				keys.add(new FavKey(2, country, league, ""));
			}
			if (!team.isEmpty()) {
				keys.add(new FavKey(3, country, league, team));
			}
		}

		java.util.ArrayList<FavKey> ordered = new java.util.ArrayList<>(keys);
		ordered.sort(java.util.Comparator.comparingInt(k -> k.level)); // 1→2→3

		for (FavKey k : ordered) {
			try {
				favoriteRepository.insert(
						req.getUserId(),
						k.level,
						k.country,
						k.league,
						k.team,
						operatorId);
			} catch (Exception e) {
				res.setResponseCode("404");
				res.setMessage("登録処理が失敗しました。");
				return res;
			}
		}

		res.setResponseCode("200");
		res.setMessage("登録処理が成功しました。");
		return res;
	}

	@Transactional
	public FavoriteResponse delete(Long userId, Long id) {
		FavoriteResponse res = new FavoriteResponse();
		try {
			favoriteRepository.deleteById(userId, id);
		} catch (Exception e) {
			res.setResponseCode("404");
			res.setMessage("削除処理が失敗しました。");
			return res;
		}
		res.setResponseCode("200");
		res.setMessage("削除処理が成功しました。");
		return res;
	}

	/**
	 * お気に入り画面設定用マスターデータ取得
	 * @param userId
	 * @return
	 */
	@Transactional(readOnly = true)
	public FavoriteScopeResponse getView(Long userId) {
		FavoriteScopeResponse res = new FavoriteScopeResponse();

		if (userId == null) {
			res.setResponseCode("9");
			res.setMessage("フィルタ条件取得エラー");
			return res;
		}

		// ① season master（表示対象になり得る country+league の集合を作る）
		List<CountryLeagueSeasonDTO> seasonDtos = countryLeagueSeasonMasterWebRepository.findAll();

		java.util.HashSet<String> seasonCountryLeague = new java.util.HashSet<>();
		for (CountryLeagueSeasonDTO s : seasonDtos) {
			String country = normalize(s.getCountry());
			String league = normalize(s.getLeague());

			if (country.isEmpty() || league.isEmpty())
				continue;

			// del_flg があるならここで弾く（なければ不要）
			// if (!"0".equals(normalize(s.getDelFlg()))) continue;

			seasonCountryLeague.add(country + "\u0001" + league);
		}

		// ② league master（del_flg=0 の country+league+team）
		List<CountryLeagueDTO> masterDtos = countryLeagueMasterWebRepository.findAllActive();

		// ③ ①に存在する country+league のものだけ残す（＝表示対象）
		java.util.LinkedHashSet<String> countries = new java.util.LinkedHashSet<>();
		java.util.Map<String, java.util.LinkedHashSet<String>> leaguesByCountry = new java.util.LinkedHashMap<>();
		java.util.Map<String, java.util.LinkedHashSet<String>> teamsByCountryLeague = new java.util.LinkedHashMap<>();

		for (CountryLeagueDTO m : masterDtos) {
			String country = normalize(m.getCountry());
			String league = normalize(m.getLeague());
			String team = normalize(m.getTeam());

			if (country.isEmpty() || league.isEmpty() || team.isEmpty())
				continue;

			String clKey = country + "\u0001" + league;
			if (!seasonCountryLeague.contains(clKey)) {
				// season master に (country,league) が無い → 画面表示対象外
				continue;
			}

			countries.add(country);
	        leaguesByCountry.computeIfAbsent(country, k -> new java.util.LinkedHashSet<>()).add(league);
	        teamsByCountryLeague.computeIfAbsent(clKey, k -> new java.util.LinkedHashSet<>()).add(team);
		}
		// ここまででどちらも存在していればお気に入り画面表示対象

		// お気に入りに設定していればチェックボックスを入れる
		// ④ Favorite（チェック復元用）
	    List<FavoriteItem> selected = favoriteRepository.findSelectedItems(userId);
	    boolean noFavorite = (selected == null || selected.isEmpty());
	    res.setAllowAll(noFavorite);

	    // ⑤ FavoriteScopeResponse に詰める
	    java.util.List<CountryScope> countryScopes = new java.util.ArrayList<>();
	    for (String c : countries) {
	        CountryScope cs = new CountryScope();
	        cs.setCountry(c);
	        countryScopes.add(cs);
	    }

	    java.util.List<LeagueScope> leagueScopes = new java.util.ArrayList<>();
	    for (var e : leaguesByCountry.entrySet()) {
	        LeagueScope ls = new LeagueScope();
	        ls.setCountry(e.getKey());
	        ls.setLeagues(new java.util.ArrayList<>(e.getValue()));
	        leagueScopes.add(ls);
	    }

	    java.util.List<TeamScope> teamScopes = new java.util.ArrayList<>();
	    for (var e : teamsByCountryLeague.entrySet()) {
	        String[] parts = e.getKey().split("\u0001", 2);

	        TeamScope ts = new TeamScope();
	        ts.setCountry(parts[0]);
	        ts.setLeague(parts[1]);
	        ts.setTeams(new java.util.ArrayList<>(e.getValue()));
	        teamScopes.add(ts);
	    }

	    res.setAllowedCountries(countryScopes);
	    res.setAllowedLeaguesByCountry(leagueScopes);
	    res.setAllowedTeamsByCountryLeague(teamScopes);
	    res.setSelectedItems(selected);

	    res.setResponseCode("0");
	    res.setMessage("OK");
	    return res;
	}

	private static final class FavKey {
		final int level;
		final String country;
		final String league;
		final String team;

		FavKey(int level, String country, String league, String team) {
			this.level = level;
			this.country = country;
			this.league = league;
			this.team = team;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof FavKey))
				return false;
			FavKey other = (FavKey) o;
			return level == other.level
					&& country.equals(other.country)
					&& league.equals(other.league)
					&& team.equals(other.team);
		}

		@Override
		public int hashCode() {
			int h = Integer.hashCode(level);
			h = 31 * h + country.hashCode();
			h = 31 * h + league.hashCode();
			h = 31 * h + team.hashCode();
			return h;
		}
	}

	// -------------------
	// validation helpers
	// -------------------
	private static void requireUser(FavoriteInsertRequest req) {
		if (req == null || req.getUserId() == null) {
			throw new IllegalArgumentException("userId is required");
		}
	}

	private static String normalize(String v) {
		if (v == null)
			return "";
		String s = v.trim();
		if (s.equalsIgnoreCase("null"))
			return "";
		return s;
	}
}
