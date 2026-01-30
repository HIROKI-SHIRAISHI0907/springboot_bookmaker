package dev.web.api.bm_w022;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

import org.springframework.stereotype.Service;

import dev.web.config.S3JobPropertiesConfig;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * S3 の prefix（疑似フォルダ）配下にあるファイル件数取得サービス。
 * <p>
 * batchCode ごとに bucket / prefix / 再帰有無を切り替え、
 * 以下の件数を 1 回の ListObjectsV2 走査で同時に算出する。
 * </p>
 *
 * <ul>
 *   <li>prefix 配下の全ファイル件数</li>
 *   <li>指定日（JST）に作成・更新（LastModified）されたファイル件数</li>
 * </ul>
 *
 * <p>
 * S3 には厳密な「作成日時」の概念がないため、
 * ファイルの作成・更新日時は {@code lastModified} を基準とする。
 * </p>
 *
 * <h3>使用例</h3>
 *
 * <pre>{@code
 * // 全件 + 今日(JST)の件数を取得
 * S3FileCountResponse res1 =
 *     service.getFileCountWithToday("B002");
 *
 * // 全件 + 指定日(JST: 2026-01-30)の件数を取得
 * S3FileCountResponse res2 =
 *     service.getFileCountWithDay("B002", LocalDate.of(2026, 1, 30));
 * }</pre>
 * @author shiraishitoshio
 */
@Service
public class S3FileCountService {

    private static final String FALLBACK_MESSAGE =
            "件数を算出できませんでした。bucket/prefix や権限をご確認ください。";

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    private final S3Client s3;
    private final S3JobPropertiesConfig props;

    public S3FileCountService(S3Client s3, S3JobPropertiesConfig props) {
        this.s3 = s3;
        this.props = props;
    }

    /** 今日(JST)の件数も一緒に返す */
    public S3FileCountResponse getFileCountWithToday(String batchCode) {
        LocalDate todayJst = LocalDate.now(JST);
        return getFileCountWithDay(batchCode, todayJst);
    }

    /** 指定日(JST)の件数 + 全件数 */
    public S3FileCountResponse getFileCountWithDay(String batchCode, LocalDate dayJst) {
        S3JobPropertiesConfig.JobConfig cfg = props.require(batchCode);

        S3FileCountResponse res = new S3FileCountResponse();
        res.setBatchCode(batchCode);
        res.setBucket(cfg.getBucket());
        res.setPrefix(cfg.getPrefix());
        res.setRecursive(cfg.isRecursive());
        res.setDayJst(dayJst == null ? null : dayJst.toString());

        if (cfg.getBucket() == null || cfg.getBucket().isBlank()) {
            res.setMessage(FALLBACK_MESSAGE + " (バケットが指定されていません。batchCode: " + batchCode + ")");
            return res;
        }

        String prefix = normalizePrefix(cfg.getPrefix());

        // JSTの当日範囲 [start, end)
        Instant start = null;
        Instant end = null;
        if (dayJst != null) {
            ZonedDateTime zStart = dayJst.atStartOfDay(JST);
            ZonedDateTime zEnd = zStart.plusDays(1);
            start = zStart.toInstant();
            end = zEnd.toInstant();
        }

        try {
            CountResult cr = countObjects(bucket(cfg), prefix, cfg.isRecursive(), start, end);

            res.setTotalCount(cr.total);
            res.setCountOnDay(cr.onDay);
            res.setMessage("OK");
            return res;
        } catch (Exception e) {
            res.setMessage(FALLBACK_MESSAGE + " (" + e.getClass().getSimpleName() + ")");
            return res;
        }
    }

    private String bucket(S3JobPropertiesConfig.JobConfig cfg) {
        return cfg.getBucket();
    }

    /**
     * 1回の走査で
     * - total: 全件
     * - onDay: lastModified が [start, end) の件数
     */
    private CountResult countObjects(String bucket, String prefix, boolean recursive, Instant start, Instant end) {
        String token = null;
        long total = 0;
        long onDay = 0;

        do {
            ListObjectsV2Request.Builder req = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .continuationToken(token)
                    .maxKeys(1000);

            if (!recursive) req.delimiter("/");

            ListObjectsV2Response resp = s3.listObjectsV2(req.build());

            if (resp.contents() != null) {
                for (S3Object obj : resp.contents()) {
                    // フォルダプレースホルダ除外したい場合
                    if (prefix != null && Objects.equals(obj.key(), prefix)) continue;

                    total++;

                    if (start != null && end != null && obj.lastModified() != null) {
                        Instant lm = obj.lastModified(); // SDK v2 は Instant
                        if (!lm.isBefore(start) && lm.isBefore(end)) {
                            onDay++;
                        }
                    }
                }
            }

            token = resp.isTruncated() ? resp.nextContinuationToken() : null;
        } while (token != null);

        return new CountResult(total, onDay);
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) return null;
        return prefix.endsWith("/") ? prefix : (prefix + "/");
    }

    private static class CountResult {
        final long total;
        final long onDay;
        CountResult(long total, long onDay) {
            this.total = total;
            this.onDay = onDay;
        }
    }
}
