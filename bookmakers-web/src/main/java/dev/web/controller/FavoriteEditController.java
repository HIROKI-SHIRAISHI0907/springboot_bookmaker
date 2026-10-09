package dev.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.favorite.bm_w001.FavoriteEditResponse;
import dev.web.api.favorite.bm_w001.FavoriteEditService;
import dev.web.api.favorite.bm_w001.FavoriteLeagueDTO;
import dev.web.api.favorite.bm_w001.FavoriteTeamAddRequest;
import dev.web.api.favorite.bm_w001.FavoriteTeamCandidateDTO;
import dev.web.repository.user.LoginUserResolverRepository;
import lombok.RequiredArgsConstructor;

/**
 * FavoriteEdit.tsx（お気に入りチームの編集）  ログイン必須（未ログインは 401）
 * <ul>
 *   <li>GET    /v1/api/favorite-edit/teams                      登録済みのチーム</li>
 *   <li>GET    /v1/api/favorite-edit/leagues                    国・リーグの一覧（絞り込み用）</li>
 *   <li>GET    /v1/api/favorite-edit/search?q=&amp;country=&amp;league=  チームを探す</li>
 *   <li>POST   /v1/api/favorite-edit/teams  {country, league, team}  追加</li>
 *   <li>DELETE /v1/api/favorite-edit/teams/{id}                 削除</li>
 * </ul>
 * @author shiraishitoshio
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/favorite-edit")
public class FavoriteEditController {

	private final FavoriteEditService service;

	private final LoginUserResolverRepository loginUserResolver;

	@GetMapping("/teams")
	public ResponseEntity<FavoriteEditResponse> teams() {
		Long userId = userId();
		return userId == null ? unauthorized() : ResponseEntity.ok(service.getTeams(userId));
	}

	@GetMapping("/leagues")
	public ResponseEntity<List<FavoriteLeagueDTO>> leagues() {
		return userId() == null ? unauthorized() : ResponseEntity.ok(service.getLeagues());
	}

	@GetMapping("/search")
	public ResponseEntity<List<FavoriteTeamCandidateDTO>> search(
			@RequestParam(name = "q", required = false) String q,
			@RequestParam(name = "country", required = false) String country,
			@RequestParam(name = "league", required = false) String league) {
		Long userId = userId();
		return userId == null ? unauthorized() : ResponseEntity.ok(service.search(userId, q, country, league));
	}

	@PostMapping("/teams")
	public ResponseEntity<FavoriteEditResponse> add(@RequestBody FavoriteTeamAddRequest req) {
		Long userId = userId();
		return userId == null ? unauthorized() : ResponseEntity.ok(service.add(userId, req));
	}

	@DeleteMapping("/teams/{id}")
	public ResponseEntity<FavoriteEditResponse> delete(@PathVariable("id") Long id) {
		Long userId = userId();
		return userId == null ? unauthorized() : ResponseEntity.ok(service.delete(userId, id));
	}

	/** ログイン中ユーザーの ID（JWT のメール → users.id。未ログインは null） */
	private Long userId() {
		return this.loginUserResolver.currentUserId();
	}

	private static <T> ResponseEntity<T> unauthorized() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
	}
}