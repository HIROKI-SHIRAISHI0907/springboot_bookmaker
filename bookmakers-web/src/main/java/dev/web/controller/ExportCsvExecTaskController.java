package dev.web.controller;

import java.io.IOException;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w020.ExportCsvResponse;
import dev.web.api.bm_w020.ExportCsvService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/export")
@RequiredArgsConstructor
public class ExportCsvController {

    private final ExportCsvService service;

    /**
     * POST /api/admin/export/statCsv
     * @throws IOException
     *
     */
    @PostMapping("/statCsv")
    public ExportCsvResponse createCsv() throws IOException {
        return service.createCsv();
    }
}
