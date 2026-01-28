package dev.web.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w020.ExportCsvResponse;
import dev.web.api.bm_w020.ExportCsvService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportCsvController {

    private final ExportCsvService service;

    /**
     * POST /api/export/csv
     *
     */
    @PostMapping("/csv")
    public ExportCsvResponse createCsv() {
        return service.createCsv();
    }
}
