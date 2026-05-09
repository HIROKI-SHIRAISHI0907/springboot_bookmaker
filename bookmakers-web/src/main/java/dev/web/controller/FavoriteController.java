package dev.web.controller;

import org.springframework.http.ResponseEntity;
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
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final JwtCurrentUserService jwtCurrentUserService;

    @PostMapping("/favorites")
    public ResponseEntity<FavoriteResponse> upsert(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody FavoriteInsertRequest req) {

        JwtCurrentUserService.CurrentUser currentUser =
                jwtCurrentUserService.resolve(authorizationHeader);

        FavoriteResponse res = favoriteService.upsert(
                currentUser.getUserId(),
                currentUser.getEmail(),
                req
        );

        return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
    }

    @PostMapping("/favorite/view")
    public ResponseEntity<FavoriteScopeResponse> view(
            @RequestHeader("Authorization") String authorizationHeader) {

        JwtCurrentUserService.CurrentUser currentUser =
                jwtCurrentUserService.resolve(authorizationHeader);

        FavoriteScopeResponse res = favoriteService.getView(currentUser.getUserId());
        return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
    }

    @DeleteMapping("/favorites/{id}")
    public ResponseEntity<FavoriteResponse> delete(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long id) {

        JwtCurrentUserService.CurrentUser currentUser =
                jwtCurrentUserService.resolve(authorizationHeader);

        FavoriteResponse res = favoriteService.delete(currentUser.getUserId(), id);
        return ResponseEntity.status(parseStatus(res.getResponseCode())).body(res);
    }

    private int parseStatus(String code) {
        try {
            int status = Integer.parseInt(code);
            return (status >= 100 && status <= 599) ? status : 200;
        } catch (Exception e) {
            return 200;
        }
    }
}
