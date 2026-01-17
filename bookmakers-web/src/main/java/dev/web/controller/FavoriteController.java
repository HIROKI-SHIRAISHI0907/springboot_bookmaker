package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_u003.FavoriteRequest;
import dev.web.api.bm_u003.FavoriteResponse;
import dev.web.api.bm_u003.FavoriteScope;
import dev.web.api.bm_u003.FavoriteScopeResponse;
import dev.web.api.bm_u003.FavoriteService;
import dev.web.api.bm_u003.mapper.FavoriteScopeMapper;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final FavoriteScopeMapper favoriteScopeMapper;

    /**
     * フィルタ条件取得（空なら allowAll=true）
     * GET /api/favorites/scope?userId=1
     */
    @GetMapping("/scope")
    public ResponseEntity<FavoriteScopeResponse> getScope(@RequestParam Long userId) {
        FavoriteScope scope = favoriteService.getScope(userId);
        FavoriteScopeResponse res = favoriteScopeMapper.toResponse(scope);
        return ResponseEntity.ok(res);
    }

    /**
     * お気に入りをまとめて登録
     * POST /api/favorites
     */
    @PostMapping
    public ResponseEntity<FavoriteResponse> upsert(@RequestBody FavoriteRequest req) {
        FavoriteResponse res = null;
        try {
            res = favoriteService.upsert(req);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            res.setResponseCode("9");
            res.setMessage("お気に入り登録に失敗しました: " + e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
    }

    /**
     * 削除（id指定）
     * DELETE /api/favorites/{id}?userId=1
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<FavoriteResponse> delete(
            @RequestParam Long userId,
            @PathVariable Long id
    ) {
        FavoriteResponse res = null;
        try {
        	res = favoriteService.delete(userId, id);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            res.setResponseCode("9");
            res.setMessage("お気に入り削除に失敗しました: " + e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
    }
}
