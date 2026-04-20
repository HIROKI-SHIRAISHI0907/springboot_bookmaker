package dev.web.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a015.PointSettingsAPIService;
import dev.web.api.bm_a015.PointSettingsResponse;
import dev.web.api.bm_a015.PointSettingsSaveRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminPointsSettingsWebController {

    private final PointSettingsAPIService service;

    /**
     * GET /api/admin/point-settings
     */
    @GetMapping("/point-settings")
    public PointSettingsResponse getPointSettings() {
        return new PointSettingsResponse(service.findAll());
    }

    /**
     * POST /api/admin/point-settings
     */
    @PostMapping(
        value = "/point-settings",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public PointSettingsResponse savePointSettings(@RequestBody PointSettingsSaveRequest request) {
        return new PointSettingsResponse(service.save(request));
    }
}
