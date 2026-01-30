package dev.web.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w022.S3FileCountResponse;
import dev.web.api.bm_w022.S3FileCountService;

/**
 * S3 に出力されたバッチ成果物の件数取得 API。
 * <p>
 * batchCode ごとに設定された S3 bucket / prefix 配下のファイルについて、
 * 以下の件数を返却する。
 * </p>
 *
 * <ul>
 *   <li>prefix 配下の全ファイル件数</li>
 *   <li>指定日（JST）に作成・更新（LastModified）されたファイル件数</li>
 * </ul>
 *
 * <p>
 * 日付を指定しない場合は「今日（JST）」を基準に件数を算出する。
 * </p>
 *
 * <h3>注意事項</h3>
 * <ul>
 *   <li>S3 には厳密な作成日時の概念がないため、LastModified を基準とする</li>
 *   <li>同一キーの上書き更新も「作成・更新件数」としてカウントされる</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/s3/files")
public class S3FileCountController {

    private final S3FileCountService service;

    public S3FileCountController(S3FileCountService service) {
        this.service = service;
    }

    /**
     * S3 prefix 配下の全件数と「今日（JST）」の作成・更新件数を取得する。
     *
     * <h3>リクエスト例</h3>
     * <pre>{@code
     * GET /api/s3/files/B002
     * }</pre>
     *
     * @param batchCode バッチコード（例: B002）
     * @return 全件数および今日(JST)の件数
     */
    @GetMapping("/{batchCode}")
    public S3FileCountResponse getFileCountToday(
            @PathVariable String batchCode) {

        return service.getFileCountWithToday(batchCode);
    }

    /**
     * S3 prefix 配下の全件数と、指定日（JST）の作成・更新件数を取得する。
     *
     * <h3>リクエスト例</h3>
     * <pre>{@code
     * GET /api/s3/files/B002?day=2026-01-30
     * }</pre>
     *
     * @param batchCode バッチコード（例: B002）
     * @param day 件数を算出する日付（JST, yyyy-MM-dd）
     * @return 全件数および指定日(JST)の件数
     */
    @GetMapping("/{batchCode}/by-day")
    public S3FileCountResponse getFileCountByDay(
            @PathVariable String batchCode,
            @RequestParam("day")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate day) {

        return service.getFileCountWithDay(batchCode, day);
    }
}
