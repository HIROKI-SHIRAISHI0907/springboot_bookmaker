package dev.common.readfile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.common.constant.FutureScheduleConstant;
import dev.common.readfile.dto.DelayPostponeMatchDto;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * delay_postpone json 読み込みサービス
 *
 * 対象:
 * - delay_postpone_YYYY-MM-DD.json
 * - delay_postpone_YYYY-MM-DD_2.json
 * - delay_postpone_YYYY-MM-DD_3.json
 * ...
 *
 * ListBucket は使わず、規則的な key を順番に読む
 *
 * @author shiraishitoshio
 */
@Component
@RequiredArgsConstructor
public class ReadDelayPostpone {

    /** 出力先 S3 bucket */
    private static final String OUTPUT_BUCKET = "aws-s3-delay-postpone-csv";

    /**
     * Python 側 OUTPUT_PREFIX と合わせること
     * 例:
     * ""      -> ルート直下
     * "json/" -> json配下
     */
    private static final String OUTPUT_PREFIX = "";

    /**
     * 念のための上限
     * 1日4回起動なら 10 でも十分
     */
    private static final int MAX_DAILY_FILES = 20;

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    /**
     * 指定日の delay_postpone json を base / _2 / _3 ... 含めて全件読み込む
     *
     * @param targetDate yyyy-MM-dd
     * @return 延期・遅延試合一覧
     */
    public List<DelayPostponeMatchDto> readAllDelayPostponeMatches(String targetDate) {
        List<String> targetKeys = buildExistingTargetKeys(targetDate);

        if (targetKeys.isEmpty()) {
            return Collections.emptyList();
        }

        // 重複除去: status + category + home + away
        Map<String, DelayPostponeMatchDto> dedupMap = new LinkedHashMap<String, DelayPostponeMatchDto>();

        for (String key : targetKeys) {
            List<DelayPostponeMatchDto> fileItems = readSingleFile(key);

            for (DelayPostponeMatchDto dto : fileItems) {
                String dedupKey = buildDedupKey(dto);
                if (!dedupMap.containsKey(dedupKey)) {
                    dedupMap.put(dedupKey, dto);
                }
            }
        }

        return new ArrayList<DelayPostponeMatchDto>(dedupMap.values());
    }

    /**
     * 存在するキーを順番に構築
     * 1件目: delay_postpone_YYYY-MM-DD.json
     * 2件目: delay_postpone_YYYY-MM-DD_2.json
     * ...
     */
    private List<String> buildExistingTargetKeys(String targetDate) {
        List<String> results = new ArrayList<String>();

        for (int seq = 1; seq <= MAX_DAILY_FILES; seq++) {
            String key = buildOutputKey(targetDate, seq);

            if (existsObjectByGetObject(key)) {
                results.add(key);
                continue;
            }

            // 無印がなければ対象なし
            if (seq == 1) {
                break;
            }

            // _2 以降は最初に無い番号で打ち切り
            break;
        }

        return results;
    }

    /**
     * seq=1 -> delay_postpone_YYYY-MM-DD.json
     * seq=2 -> delay_postpone_YYYY-MM-DD_2.json
     */
    private String buildOutputKey(String targetDate, int seq) {
        String prefix = normalizePrefix(OUTPUT_PREFIX);

        if (seq <= 1) {
            return prefix + "delay_postpone_" + targetDate + ".json";
        }

        return prefix + "delay_postpone_" + targetDate + "_" + seq + ".json";
    }

    /**
     * ListBucket を使わずに存在確認
     * getObject を試し、NoSuchKey なら false
     */
    private boolean existsObjectByGetObject(String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(OUTPUT_BUCKET)
                .key(key)
                .build();

        ResponseInputStream<GetObjectResponse> inputStream = null;
        try {
            inputStream = s3Client.getObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            String errorCode = e.awsErrorDetails() == null ? null : e.awsErrorDetails().errorCode();
            if (e.statusCode() == 404 || "NoSuchKey".equals(errorCode)) {
                return false;
            }
            throw new RuntimeException("delay_postpone json existence check failed: s3://" + OUTPUT_BUCKET + "/" + key, e);
        } catch (Exception e) {
            throw new RuntimeException("delay_postpone json existence check failed: s3://" + OUTPUT_BUCKET + "/" + key, e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    // noop
                }
            }
        }
    }

    /**
     * 単一 json ファイルを読み込み
     */
    private List<DelayPostponeMatchDto> readSingleFile(String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(OUTPUT_BUCKET)
                .key(key)
                .build();

        try (ResponseInputStream<GetObjectResponse> inputStream = s3Client.getObject(request)) {
            return extractMatches(inputStream, key);
        } catch (Exception e) {
            throw new RuntimeException("delay_postpone json 読み込み失敗: s3://" + OUTPUT_BUCKET + "/" + key, e);
        }
    }

    /**
     * JSON 本体から必要項目を抽出
     *
     * 読み込み対象:
     * - matched_delay_postpone_games
     * - carry_over_postponed_games
     */
    private List<DelayPostponeMatchDto> extractMatches(InputStream inputStream, String sourceKey) {
        try {
            JsonNode root = objectMapper.readTree(inputStream);

            List<DelayPostponeMatchDto> results = new ArrayList<DelayPostponeMatchDto>();

            appendSection(results, root.path("matched_delay_postpone_games"), sourceKey);
            appendSection(results, root.path("carry_over_postponed_games"), sourceKey);

            return results;

        } catch (Exception e) {
            throw new RuntimeException("delay_postpone json 解析失敗: " + sourceKey, e);
        }
    }

    /**
     * 指定セクションから延期・遅延試合を抽出
     */
    private void appendSection(List<DelayPostponeMatchDto> out, JsonNode sectionNode, String sourceKey) {
        if (sectionNode == null || !sectionNode.isArray()) {
            return;
        }

        for (int i = 0; i < sectionNode.size(); i++) {
            JsonNode node = sectionNode.get(i);

            String statusTypeJa = trim(node.path("status_type").asText(null));
            String category = trim(node.path("category").asText(null));
            String home = trim(node.path("home").asText(null));
            String away = trim(node.path("away").asText(null));

            if (!hasText(statusTypeJa) || !hasText(category) || !hasText(home) || !hasText(away)) {
                continue;
            }

            String statusType = toFutureSchedule(statusTypeJa);
            if (statusType == null) {
                continue;
            }

            out.add(new DelayPostponeMatchDto(
                    statusType,
                    category,
                    home,
                    away,
                    sourceKey
            ));
        }
    }

    /**
     * 日本語ステータスをコード文字列に変換
     */
    private String toFutureSchedule(String statusTypeJa) {
        if (FutureScheduleConstant.POSTPONED.getJapaneseMeaning().equals(statusTypeJa)) {
            return FutureScheduleConstant.POSTPONED.getCode();
        }
        if (FutureScheduleConstant.DELAYED.getJapaneseMeaning().equals(statusTypeJa)) {
            return FutureScheduleConstant.DELAYED.getCode();
        }
        return null;
    }

    /**
     * 重複判定キー生成
     */
    private String buildDedupKey(DelayPostponeMatchDto dto) {
        String statusCode = "";
        if (dto.getStatusType() != null) {
            statusCode = safe(dto.getStatusType());
        }

        return statusCode
                + "|"
                + safe(dto.getCategory())
                + "|"
                + safe(dto.getHome())
                + "|"
                + safe(dto.getAway());
    }

    private String normalizePrefix(String prefix) {
        if (!hasText(prefix)) {
            return "";
        }
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
