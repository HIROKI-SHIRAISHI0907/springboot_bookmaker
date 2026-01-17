package dev.web.api.bm_u003;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.user.FavoriteRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FavoriteService {

	private final FavoriteRepository favoriteRepository;

	public FavoriteScope getScope(Long userId) {
		return favoriteRepository.findFavoriteScope(userId);
	}

	/**
	 * お気に入りをまとめて登録（country / league / team を item の内容で判定）
	 */
	@Transactional
	public FavoriteResponse upsert(FavoriteRequest req) {
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
	    if (operatorId.isEmpty()) operatorId = "system";

	    java.util.LinkedHashSet<FavKey> keys = new java.util.LinkedHashSet<>();

	    for (FavoriteItem item : req.getItems()) {

	        String country = normalize(item.getCountry());
	        String league  = normalize(item.getLeague());
	        String team    = normalize(item.getTeam());

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
	                operatorId
	            );
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
	        if (this == o) return true;
	        if (!(o instanceof FavKey)) return false;
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
	private static void requireUser(FavoriteRequest req) {
		if (req == null || req.getUserId() == null) {
			throw new IllegalArgumentException("userId is required");
		}
	}

	private static String normalize(String v) {
		if (v == null) return "";
	    String s = v.trim();
	    if (s.equalsIgnoreCase("null")) return "";
	    return s;
	}
}
