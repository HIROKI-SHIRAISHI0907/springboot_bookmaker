package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_u003.FavoriteInsertRequest;
import dev.web.api.bm_u003.FavoriteResponse;
import dev.web.api.bm_u003.FavoriteScopeResponse;
import dev.web.api.bm_u003.FavoriteService;
import dev.web.jwt.JwtCurrentUserService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@CrossOrigin(origins = "${app.cors.allowed-origins:*}")
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final JwtCurrentUserService jwtCurrentUserService;

    @PostMapping("/favorites")
    public ResponseEntity<FavoriteResponse> upsert(
        @RequestHeader("Authorization") String authorization,
        @RequestBody FavoriteInsertRequest req
    ) {
        var currentUser = jwtCurrentUserService.resolve(authorization);

        FavoriteResponse res = favoriteService.upsert(
            currentUser.getUserId(),
            currentUser.getEmail(),
            req
        );

        return ResponseEntity.ok(res);
    }

    @PostMapping("/favorite/view")
    public ResponseEntity<FavoriteScopeResponse> view(
        @RequestHeader("Authorization") String authorization
    ) {
        var currentUser = jwtCurrentUserService.resolve(authorization);

        FavoriteScopeResponse res = favoriteService.getView(currentUser.getUserId());
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/favorites/{id}")
    public ResponseEntity<FavoriteResponse> delete(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("id") Long id
    ) {
        var currentUser = jwtCurrentUserService.resolve(authorization);

        FavoriteResponse res = favoriteService.delete(currentUser.getUserId(), id);
        return ResponseEntity.ok(res);
    }
}
