package dev.web.api.bm_w022;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
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

	private static final String FALLBACK_MESSAGE = "件数を算出できませんでした。bucket/prefix や権限をご確認ください。";

	private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

	private final S3Client s3;
	private final S3JobPropertiesConfig props;

	public S3FileCountService(S3Client s3, S3JobPropertiesConfig props) {
		this.s3 = s3;
		this.props = props;
	}

	/** ① 件数取得（POST /count） */
    public S3FileCountResponse count(S3FileCountRequest req) {
        S3JobPropertiesConfig.JobConfig cfg = props.require(req.getBatchCode());

        LocalDate dayJst = (req.getDay() != null) ? req.getDay() : LocalDate.now(JST);

        String effectivePrefix = resolvePrefix(cfg.getPrefix(), req.getScope(), req.getPrefixOverride());
        boolean recursive = cfg.isRecursive();

        S3FileCountResponse res = new S3FileCountResponse();
        res.setBatchCode(req.getBatchCode());
        res.setBucket(cfg.getBucket());
        res.setPrefix(effectivePrefix);
        res.setRecursive(recursive);
        res.setDayJst(dayJst.toString());

        if (cfg.getBucket() == null || cfg.getBucket().isBlank()) {
            res.setMessage(FALLBACK_MESSAGE + " (バケットが指定されていません。batchCode: " + req.getBatchCode() + ")");
            return res;
        }

        // JSTの当日範囲 [start, end)
        Instant start;
        Instant end;
        ZonedDateTime zStart = dayJst.atStartOfDay(JST);
        ZonedDateTime zEnd = zStart.plusDays(1);
        start = zStart.toInstant();
        end = zEnd.toInstant();

        try {
            CountResult cr = countObjects(cfg.getBucket(), effectivePrefix, recursive, start, end);
            res.setTotalCount(cr.total);
            res.setCountOnDay(cr.onDay);
            res.setMessage("OK");
            return res;
        } catch (Exception e) {
            res.setMessage(FALLBACK_MESSAGE + " (" + e.getClass().getSimpleName() + ")");
            return res;
        }
    }

    /** ② 一覧取得（POST /list） */
    public S3FileListResponse list(S3FileListRequest req) {
        S3JobPropertiesConfig.JobConfig cfg = props.require(req.getBatchCode());

        String effectivePrefix = resolvePrefix(cfg.getPrefix(), req.getScope(), req.getPrefixOverride());
        boolean recursive = (req.getRecursiveOverride() != null) ? req.getRecursiveOverride() : cfg.isRecursive();
        int limit = clamp(req.getLimit(), 1, 1000); // 上限はお好みで

        S3FileListResponse res = new S3FileListResponse();
        res.setBatchCode(req.getBatchCode());
        res.setBucket(cfg.getBucket());
        res.setPrefix(effectivePrefix);
        res.setRecursive(recursive);

        if (cfg.getBucket() == null || cfg.getBucket().isBlank()) {
            res.setMessage("bucketが未設定です");
            res.setItems(List.of());
            res.setReturnedCount(0);
            return res;
        }

        try {
            List<S3FileListResponse.Item> items = listObjects(cfg.getBucket(), effectivePrefix, recursive, limit);
            res.setItems(items);
            res.setReturnedCount(items.size());
            res.setMessage("OK");
            return res;
        } catch (Exception e) {
            res.setItems(List.of());
            res.setReturnedCount(0);
            res.setMessage(FALLBACK_MESSAGE + " (" + e.getClass().getSimpleName() + ")");
            return res;
        }
    }

    // ===== prefix 解決 =====

    private String resolvePrefix(String configuredPrefix, S3PrefixScope scope, String prefixOverride) {
        S3PrefixScope s = (scope != null) ? scope : S3PrefixScope.DEFAULT;
        switch (s) {
            case ROOT:
                return null;
            case PARENT:
                return parentPrefix(configuredPrefix);
            case CUSTOM:
                return normalizePrefix(prefixOverride);
            case DEFAULT:
            default:
                return normalizePrefix(configuredPrefix);
        }
    }

    private String parentPrefix(String prefix) {
        String p = normalizePrefix(prefix);
        if (p == null) return null;

        // "json/" -> null, "a/b/" -> "a/"
        String trimmed = p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
        int idx = trimmed.lastIndexOf('/');
        if (idx < 0) return null;
        return trimmed.substring(0, idx + 1);
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) return null;
        return prefix.endsWith("/") ? prefix : (prefix + "/");
    }

    private static int clamp(Integer v, int min, int max) {
        if (v == null) return min;
        return Math.max(min, Math.min(max, v));
    }

    // ===== count（既存のまま） =====

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
                    // フォルダプレースホルダ除外したい場合（例: prefix自体のキー）
                    if (prefix != null && Objects.equals(obj.key(), prefix)) continue;

                    total++;

                    if (obj.lastModified() != null) {
                        Instant lm = obj.lastModified();
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

    // ===== list（追加） =====

    private List<S3FileListResponse.Item> listObjects(String bucket, String prefix, boolean recursive, int limit) {
        String token = null;
        List<S3FileListResponse.Item> out = new ArrayList<>();

        do {
            int remaining = limit - out.size();
            if (remaining <= 0) break;

            ListObjectsV2Request.Builder req = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .continuationToken(token)
                    .maxKeys(Math.min(1000, remaining));

            if (!recursive) req.delimiter("/");

            ListObjectsV2Response resp = s3.listObjectsV2(req.build());

            if (resp.contents() != null) {
                for (S3Object obj : resp.contents()) {
                    if (prefix != null && Objects.equals(obj.key(), prefix)) continue;

                    S3FileListResponse.Item item = new S3FileListResponse.Item();
                    item.setKey(obj.key());
                    item.setSize(obj.size() == null ? 0L : obj.size());
                    item.setLastModifiedIso(obj.lastModified() == null ? null : obj.lastModified().toString());
                    out.add(item);

                    if (out.size() >= limit) break;
                }
            }

            token = resp.isTruncated() ? resp.nextContinuationToken() : null;
        } while (token != null);

        return out;
    }

    private static class CountResult {
        final long total;
        final long onDay;
        CountResult(long total, long onDay) { this.total = total; this.onDay = onDay; }
    }
}
