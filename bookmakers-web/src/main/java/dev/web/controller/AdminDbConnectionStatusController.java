package dev.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a019.DbConnectionStatusResponse;
import dev.web.api.bm_a019.DbConnectionStatusService;

/**
 * DBコネクション監視情報を取得するコントローラー。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminDbConnectionStatusController {

    private final DbConnectionStatusService dbConnectionStatusService;

    public AdminDbConnectionStatusController(DbConnectionStatusService dbConnectionStatusService) {
        this.dbConnectionStatusService = dbConnectionStatusService;
    }

    @GetMapping("/db/connections")
    public DbConnectionStatusResponse getDbConnections() {
        return dbConnectionStatusService.getStatus();
    }
}
