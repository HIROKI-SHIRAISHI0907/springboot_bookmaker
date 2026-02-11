package dev.web.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_a006.S3FileCountRequest;
import dev.web.api.bm_a006.S3FileCountResponse;
import dev.web.api.bm_a006.S3FileCountService;
import dev.web.api.bm_a006.S3FileListRequest;
import dev.web.api.bm_a006.S3FileListResponse;

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
@RequestMapping("/api/admin/s3/files")
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
     * POST /api/admin/s3/files/count
     * }</pre>
     *
     * @param S3FileCountRequest
     * @return S3FileCountResponse
     */
    @PostMapping("/count")
    public S3FileCountResponse getFileCount(
    		@RequestBody S3FileCountRequest req) {

        return service.count(req);
    }

    /**
     * S3 prefix 配下の全リストを取得する。
     *
     * <h3>リクエスト例</h3>
     * <pre>{@code
     * POST /api/admin/s3/files/list
     * }</pre>
     *
     * @param AllLeagueRequest
     * @return S3FileCountResponse
     */
    @PostMapping("/list")
    public S3FileListResponse getFileList(@RequestBody S3FileListRequest req) {

        return service.list(req);
    }
}
