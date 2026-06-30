package dev.web.api.bm_a023;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.web.config.S3JobPropertiesConfig;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * noEcsRun JSON を S3 から取得するサービス
 */
@Service
public class NoEcsSlotsS3Service {

    /**
     * 例: ecs_slots_2026-06-30.json
     */
    private static final Pattern SLOTS_FILE_PATTERN =
            Pattern.compile("^ecs_slots_(\\d{4}-\\d{2}-\\d{2})\\.json$");

    private final S3Client s3;
    private final S3JobPropertiesConfig props;
    private final ObjectMapper objectMapper;

    public NoEcsSlotsS3Service(
            S3Client s3,
            S3JobPropertiesConfig props,
            ObjectMapper objectMapper) {
        this.s3 = s3;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /**
     * JSON本文だけ取得
     */
    public JsonNode loadSlotsJson(NoEcsSlotsRequest req) {
        LoadedSlotsResult result = loadSlotsJsonWithMeta(req);
        return result.getBody();
    }

    /**
     * bucket / key / fileName / body をまとめて取得
     */
    public LoadedSlotsResult loadSlotsJsonWithMeta(NoEcsSlotsRequest req) {
        validate(req);

        S3JobPropertiesConfig.JobConfig cfg = props.require(req.getBatchCode());

        String bucket = cfg.getBucket();
        String fileName = resolveFileName(req, cfg);
        String key = buildKey(cfg.getPrefix(), fileName);

        try (ResponseInputStream<GetObjectResponse> in = s3.getObject(
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build())) {

            JsonNode json = objectMapper.readTree((InputStream) in);
            return new LoadedSlotsResult(bucket, key, fileName, json);

        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new IllegalArgumentException(
                        "S3に対象ファイルがありません。 bucket=" + bucket + ", key=" + key);
            }
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "JSON取得に失敗しました。 bucket=" + bucket + ", key=" + key, e);
        }
    }

    private void validate(NoEcsSlotsRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("request が null です");
        }
        if (!StringUtils.hasText(req.getBatchCode())) {
            throw new IllegalArgumentException("batchCode は必須です");
        }
    }

    /**
     * 優先順:
     * 1. fileName
     * 2. day
     * 3. S3上の最新 ecs_slots_*.json
     */
    private String resolveFileName(NoEcsSlotsRequest req, S3JobPropertiesConfig.JobConfig cfg) {
        if (StringUtils.hasText(req.getFileName())) {
            return req.getFileName().trim();
        }

        if (req.getDay() != null) {
            return "ecs_slots_" + req.getDay() + ".json";
        }

        return findLatestSlotsFileName(cfg.getBucket(), cfg.getPrefix());
    }

    /**
     * S3 上の最新 ecs_slots_yyyy-MM-dd.json を探す
     */
    private String findLatestSlotsFileName(String bucket, String prefix) {
        String normalizedPrefix = normalizePrefix(prefix);

        String token = null;
        S3Object latestObj = null;
        LocalDate latestDate = null;

        do {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(normalizedPrefix == null ? "" : normalizedPrefix)
                    .continuationToken(token)
                    .maxKeys(1000)
                    .build();

            ListObjectsV2Response response = s3.listObjectsV2(request);

            if (response.contents() != null) {
                for (S3Object obj : response.contents()) {
                    if (obj == null || obj.key() == null) {
                        continue;
                    }

                    String fileName = extractFileName(obj.key());
                    Matcher matcher = SLOTS_FILE_PATTERN.matcher(fileName);
                    if (!matcher.matches()) {
                        continue;
                    }

                    LocalDate fileDate = LocalDate.parse(matcher.group(1));

                    if (latestDate == null || fileDate.isAfter(latestDate)) {
                        latestDate = fileDate;
                        latestObj = obj;
                    } else if (fileDate.equals(latestDate) && latestObj != null) {
                        if (obj.lastModified() != null
                                && latestObj.lastModified() != null
                                && obj.lastModified().isAfter(latestObj.lastModified())) {
                            latestObj = obj;
                        }
                    }
                }
            }

            token = response.isTruncated() ? response.nextContinuationToken() : null;
        } while (token != null);

        if (latestObj == null) {
            throw new IllegalArgumentException(
                    "S3上に ecs_slots_*.json が見つかりません。 bucket=" + bucket + ", prefix=" + normalizedPrefix);
        }

        return extractFileName(latestObj.key());
    }

    private String extractFileName(String key) {
        int idx = key.lastIndexOf('/');
        if (idx >= 0) {
            return key.substring(idx + 1);
        }
        return key;
    }

    private String buildKey(String prefix, String fileName) {
        String normalizedPrefix = normalizePrefix(prefix);
        return (normalizedPrefix == null ? "" : normalizedPrefix) + fileName;
    }

    private String normalizePrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return null;
        }
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }
}
