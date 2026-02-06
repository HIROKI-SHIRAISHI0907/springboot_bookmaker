package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_u003.FavoriteScopeResponse;
import dev.web.api.bm_u003.FavoriteService;
import dev.web.api.bm_u003.FavoriteViewRequest;
import lombok.RequiredArgsConstructor;

/**
 * countryLeague取得用
 * @author shiraishitoshio
 *
 */
@RestController
@RequestMapping("/api/favorite")
@RequiredArgsConstructor
public class FavoriteSettingViewController {

	private final FavoriteService favoriteService;

	/**
     * マスタデータから設定用選択肢を構築し画面に描画する（お気に入りに設定されている場合はチェックボックスを入れた状態にする）
     * POST /api/favorites/view
     */
	// TODO: ログインIDに修正しておく
    @PostMapping("/view")
    public ResponseEntity<FavoriteScopeResponse> getView(@RequestBody FavoriteViewRequest req) {
    	FavoriteScopeResponse res = new FavoriteScopeResponse();
    	if (req == null || req.getUserId() == null) {
    		res.setResponseCode("9");
            res.setMessage("ログイン情報取得エラー");
            return ResponseEntity.status(401).body(res);
    	}

        res = favoriteService.getView(req.getUserId());
        return ResponseEntity.ok(res);
    }

}
