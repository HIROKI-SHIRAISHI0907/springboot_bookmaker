package dev.common.readfile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

/**
 * delay_postpone json 読み込みサービス
 *
 * 対象ファイル:
 * - delay_postpone_YYYY-MM-DD.json
 * - delay_postpone_YYYY-MM-DD_2.json
 * - delay_postpone_YYYY-MM-DD_3.json
 * ...
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
     * 対象ファイルパターン
     * 例:
     * delay_postpone_2026-08-08.json
     * delay_postpone_2026-08-08_2.json
     * delay_postpone_2026-08-08_3.json
     */
    private static final Pattern DELAY_FILE_PATTERN =
            Pattern.compile("^(.*/)?delay_postpone_(\\d{4}-\\d{2}-\\d{2})(?:_(\\d+))?\\.json$");

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    /**
     * 指定日の delay_postpone json を base / _2 / _3 ... 含めて全件読み込む
     *
     * @param targetDate yyyy-MM-dd
     * @return 延期・遅延試合一覧
     */
    public List<DelayPostponeMatchDto> readAllDelayPostponeMatches(String targetDate) {
        List<S3DelayFile> targetFiles = listTargetFiles(targetDate);

        if (targetFiles.isEmpty()) {
            return Collections.emptyList();
        }

        // 重複除去: status + category + home + away
        Map<String, DelayPostponeMatchDto> dedupMap = new LinkedHashMap<String, DelayPostponeMatchDto>();

        for (S3DelayFile file : targetFiles) {
            List<DelayPostponeMatchDto> fileItems = readSingleFile(file.getKey());

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
     * 指定日の対象ファイルを S3 から列挙
     * 読み込み順は base -> _2 -> _3 ...
     *
     * @param targetDate yyyy-MM-dd
     * @return 対象ファイル一覧
     */
    private List<S3DelayFile> listTargetFiles(String targetDate) {
        String prefix = normalizePrefix(OUTPUT_PREFIX) + "delay_postpone_" + targetDate;

        List<S3DelayFile> results = new ArrayList<S3DelayFile>();
        String continuationToken = null;

        do {
            ListObjectsV2Request.Builder builder = ListObjectsV2Request.builder()
                    .bucket(OUTPUT_BUCKET)
                    .prefix(prefix);

            if (hasText(continuationToken)) {
                builder.continuationToken(continuationToken);
            }

            ListObjectsV2Response response = s3Client.listObjectsV2(builder.build());

            if (response.contents() != null) {
                for (int i = 0; i < response.contents().size(); i++) {
                    String key = response.contents().get(i).key();

                    S3DelayFile parsed = parseDelayFile(key);
                    if (parsed == null) {
                        continue;
                    }

                    if (!targetDate.equals(parsed.getTargetDate())) {
                        continue;
                    }

                    results.add(parsed);
                }
            }

            continuationToken = response.nextContinuationToken();

        } while (hasText(continuationToken));

        Collections.sort(results, Comparator.comparingInt(S3DelayFile::getSequenceNo));
        return results;
    }

    /**
     * 単一 json ファイルを読み込み
     *
     * @param key S3 key
     * @return 抽出結果
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
     *
     * @param inputStream 入力ストリーム
     * @param sourceKey 読み込み元 key
     * @return 抽出結果
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
     *
     * @param out 出力先
     * @param sectionNode JSON 配列ノード
     * @param sourceKey 読み込み元 key
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
     * 日本語ステータスを enum に変換
     *
     * @param statusTypeJa 延期 / 遅延
     * @return FutureScheduleConstant
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
     * S3 key を解析
     *
     * 無印:
     * delay_postpone_2026-08-08.json -> sequenceNo = 1
     *
     * 連番:
     * delay_postpone_2026-08-08_2.json -> sequenceNo = 2
     *
     * @param key S3 key
     * @return 解析結果
     */
    private S3DelayFile parseDelayFile(String key) {
        Matcher matcher = DELAY_FILE_PATTERN.matcher(key);
        if (!matcher.matches()) {
            return null;
        }

        String date = matcher.group(2);
        String seqStr = matcher.group(3);

        int sequenceNo = 1;
        if (hasText(seqStr)) {
            sequenceNo = Integer.parseInt(seqStr);
        }

        return new S3DelayFile(key, date, sequenceNo);
    }

    /**
     * 重複判定キー生成
     *
     * @param dto DTO
     * @return dedup key
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

    /**
     * S3 key 情報
     */
    private static class S3DelayFile {

        /** S3 key */
        private final String key;

        /** 対象日 yyyy-MM-dd */
        private final String targetDate;

        /** 無印=1, _2=2, _3=3 ... */
        private final int sequenceNo;

        public S3DelayFile(String key, String targetDate, int sequenceNo) {
            this.key = key;
            this.targetDate = targetDate;
            this.sequenceNo = sequenceNo;
        }

        public String getKey() {
            return key;
        }

        public String getTargetDate() {
            return targetDate;
        }

        public int getSequenceNo() {
            return sequenceNo;
        }
    }
}
