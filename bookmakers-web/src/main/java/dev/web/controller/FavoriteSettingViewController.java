package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_u003.FavoriteScopeResponse;
import dev.web.api.bm_u003.FavoriteService;
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
     * GET /api/favorites/view
     */
    @GetMapping("/view")
    public ResponseEntity<FavoriteScopeResponse> getView(@RequestParam("userId") Long userId) {
    	FavoriteScopeResponse res = new FavoriteScopeResponse();
    	if (userId == null) {
    		res.setResponseCode("9");
            res.setMessage("ログイン情報取得エラー");
            return ResponseEntity.status(401).body(res);
    	}

        res = favoriteService.getView(userId);
        return ResponseEntity.ok(res);
    }

}
