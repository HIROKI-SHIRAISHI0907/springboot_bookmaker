package dev.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a005.NotFoundException;
import dev.web.api.bm_a005.NoticeAPIService;
import dev.web.api.bm_a005.NoticeRequest;
import dev.web.api.bm_a005.NoticeResponse;
import lombok.RequiredArgsConstructor;

/**
 * お知らせ通知用コントローラー
 * @author shiraishitoshio
 *
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeAPIService noticeService;

    // 管理
    @PostMapping("/admin/notices")
    public NoticeResponse create(@RequestBody NoticeRequest req) throws NotFoundException {
        return noticeService.create(req);
    }

    @PatchMapping("/admin/notices")
    public NoticeResponse update(@RequestBody NoticeRequest req) throws NotFoundException {
        return noticeService.update(req);
    }

    @PostMapping("/admin/notices/{id}/publish")
    public NoticeResponse publish(@PathVariable Long id) throws NotFoundException {
        return noticeService.publish(id);
    }

    @PostMapping("/admin/notices/{id}/archive")
    public NoticeResponse archive(@PathVariable Long id) throws NotFoundException {
        return noticeService.archive(id);
    }

    @GetMapping("/admin/notices")
    public List<NoticeResponse> listAll() {
        return noticeService.listAll();
    }

    // フロント表示
    @GetMapping("/notices")
    public List<NoticeResponse> listForFront(
            @RequestParam(name = "active", defaultValue = "true") boolean active
    ) {
        return active ? noticeService.listActiveForFront() : noticeService.listPublishedForFront();
    }

    @GetMapping("/notices/{id}")
    public NoticeResponse getForFront(@PathVariable Long id) throws NotFoundException {
        return noticeService.getPublishedDetailForFront(id);
    }
}
