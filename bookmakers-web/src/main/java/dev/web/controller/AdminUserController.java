package dev.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_u005.AdminUserActionResponse;
import dev.web.api.bm_u005.AdminUserListResponse;
import dev.web.api.bm_u005.AdminUserService;
import dev.web.api.bm_u005.UpdateUserAuthFlgRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
@CrossOrigin(origins = "${app.cors.allowed-origins:*}")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<AdminUserListResponse> getUsers() {
        AdminUserListResponse res = adminUserService.getUsers();
        return ResponseEntity.ok(res);
    }

    @PostMapping("/auth-flg")
    public ResponseEntity<AdminUserActionResponse> updateAuthFlg(
            @RequestBody UpdateUserAuthFlgRequest req) {

        AdminUserActionResponse res = adminUserService.updateAuthFlg(req);

        int status;
        try {
            status = Integer.parseInt(res.getResponseCode());
        } catch (Exception e) {
            status = 500;
        }

        return ResponseEntity.status(status).body(res);
    }
}
