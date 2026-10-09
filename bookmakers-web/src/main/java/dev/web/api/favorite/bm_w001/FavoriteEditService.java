package dev.web.api.favorite.bm_w001;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import dev.web.repository.user.FavoriteEditMasterRepository;
import dev.web.repository.user.FavoriteRepository;
import lombok.RequiredArgsConstructor;

/**
 * お気に入りチームの編集（FavoriteEdit.tsx）
 *
 * <ul>
 *   <li>favorites の level = 3（国・リーグ・チーム）だけを扱う。国・リーグ単位（level 1/2）のお気に入りは触らない。</li>
 *   <li>追加できるのは country_league_master（未削除）にあるチームだけ。最大 {@value #MAX_TEAMS} チーム。</li>
 * </ul>
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class FavoriteEditService {

	/** 登録できるチーム数 */
	public static final int MAX_TEAMS = 30;

	/** 検索結果の最大件数 */
	static final int SEARCH_LIMIT = 50;

	private final FavoriteRepository favoriteRepository;

	private final FavoriteEditMasterRepository masterRepository;

	/** GET /v1/api/favorite-edit/teams */
	public FavoriteEditResponse getTeams(Long userId) {
		return new FavoriteEditResponse(this.favoriteRepository.findTeams(userId), MAX_TEAMS, null);
	}

	/** GET /v1/api/favorite-edit/leagues */
	public List<FavoriteLeagueDTO> getLeagues() {
		return this.masterRepository.findLeagues();
	}

	/** GET /v1/api/favorite-edit/search（登録済みのチームには favoriteId を付ける） */
	public List<FavoriteTeamCandidateDTO> search(Long userId, String keyword, String country, String league) {
		List<FavoriteTeamCandidateDTO> list = this.masterRepository.searchTeams(keyword, country, league, SEARCH_LIMIT);
		Map<String, Long> registered = new HashMap<>();
		for (FavoriteTeamDTO f : this.favoriteRepository.findTeams(userId)) {
			registered.put(key(f.getCountry(), f.getLeague(), f.getTeam()), f.getId());
		}
		for (FavoriteTeamCandidateDTO c : list) {
			c.setFavoriteId(registered.get(key(c.getCountry(), c.getLeague(), c.getTeam())));
		}
		return list;
	}

	/** POST /v1/api/favorite-edit/teams */
	public FavoriteEditResponse add(Long userId, FavoriteTeamAddRequest req) {
		String country = trim(req.getCountry());
		String league = trim(req.getLeague());
		String team = trim(req.getTeam());
		if (country.isEmpty() || league.isEmpty() || team.isEmpty()) {
			return new FavoriteEditResponse(this.favoriteRepository.findTeams(userId), MAX_TEAMS, "国・リーグ・チームを指定してください");
		}
		if (!this.masterRepository.exists(country, league, team)) {
			return new FavoriteEditResponse(this.favoriteRepository.findTeams(userId), MAX_TEAMS, "マスタに無いチームです");
		}
		if (this.favoriteRepository.countTeams(userId) >= MAX_TEAMS) {
			return new FavoriteEditResponse(this.favoriteRepository.findTeams(userId), MAX_TEAMS,
					"お気に入りは " + MAX_TEAMS + " チームまでです");
		}
		int n = this.favoriteRepository.insert(userId, 3, country, league, team, String.valueOf(userId));
		return new FavoriteEditResponse(this.favoriteRepository.findTeams(userId), MAX_TEAMS,
				n > 0 ? team + " を追加しました" : "登録済みです");
	}

	/** DELETE /v1/api/favorite-edit/teams/{id} */
	public FavoriteEditResponse delete(Long userId, Long id) {
		int n = this.favoriteRepository.deleteById(userId, id);
		return new FavoriteEditResponse(this.favoriteRepository.findTeams(userId), MAX_TEAMS,
				n > 0 ? "削除しました" : null);
	}

	private static String key(String c, String l, String t) {
		return trim(c) + "|" + trim(l) + "|" + trim(t);
	}

	private static String trim(String s) {
		return s == null ? "" : s.trim();
	}
}
