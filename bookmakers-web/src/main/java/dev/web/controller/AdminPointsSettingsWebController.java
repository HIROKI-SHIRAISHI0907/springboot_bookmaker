package dev.web.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.common.entity.PointSettingEntity;
import dev.web.api.bm_a015.PointSettingsAPIService;
import dev.web.api.bm_a015.PointSettingsSaveRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminPointsSettingsWebController {

    private final PointSettingsAPIService service;

    @GetMapping("/point-settings")
    public List<PointSettingEntity> getPointSettings() {
        return service.findAll();
    }

    @PostMapping(
        value = "/point-settings",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public List<PointSettingEntity> savePointSettings(@Valid @RequestBody PointSettingsSaveRequest request) {
        return service.save(request);
    }
}
