package dev.web.api.bm_a012;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.common.config.PathConfig;
import dev.common.s3.S3Operator;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FinGettingService {

    private final ObjectMapper objectMapper;
    private final PathConfig pathConfig;
    private final S3Operator s3Operator;

    /**
     * FinGettingRequest(matches) を
     * { "yyyy-MM-dd": [ {matchKey, matchUrl?}, ... ] } に変換し、
     * /tmp/json/b008_fin_getting.json へ出力→S3へアップロードする。
     *
     * @return アップロードしたS3 key（呼び出し元でログやレスポンスに載せたい場合用）
     */
    public String convertAndUpload(FinGettingRequest req) throws Exception {

        // 1) 入力チェック
        if (req == null || req.getMatches() == null || req.getMatches().isEmpty()) {
            throw new IllegalArgumentException("matches がありません（または空です）");
        }

        // 2) Map化（目的のJSON構造）
        Map<String, List<Map<String, Object>>> out = toOutputMap(req.getMatches());

        // 3) ローカルへJSON出力（指定のここ）
        final String outputBucket = pathConfig.getS3BucketsOutputs();

        final String jsonFolder = pathConfig.getB008JsonFolder(); // 例: /tmp/json/
        final String fileName = "b008_fin_getting.json";
        final Path jsonFilePath = Paths.get(jsonFolder, fileName);

        Files.createDirectories(jsonFilePath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFilePath.toFile(), out);

        // 4) S3へアップロード
        final String s3Key = "fin/" + jsonFilePath.getFileName().toString(); // 例: fin/b008_fin_getting.json
        s3Operator.uploadFile(outputBucket, s3Key, jsonFilePath);

        return s3Key;
    }

    /**
     * matches(List<Item>) → {date: [{matchKey, matchUrl?}]} のMapへ変換
     */
    private Map<String, List<Map<String, Object>>> toOutputMap(List<FinGettingRequest.Item> items) {

        // 日付の出現順を維持したい場合に備えて LinkedHashMap
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();

        for (int i = 0; i < items.size(); i++) {
            FinGettingRequest.Item it = items.get(i);

            LocalDate matchDate = it.getMatchDate();
            String matchId = it.getMatchId();
            String matchUrl = it.getMatchUrl();

            if (matchDate == null) {
                throw new IllegalArgumentException("matchDate がありません: index=" + i);
            }
            if (matchId == null || matchId.isBlank()) {
                throw new IllegalArgumentException("matchId がありません: index=" + i);
            }

            String dateKey = matchDate.toString(); // yyyy-MM-dd

            Map<String, Object> row = new HashMap<>();
            row.put("matchKey", matchId.trim()); // ★例に合わせて matchKey = matchId

            if (matchUrl != null && !matchUrl.isBlank()) {
                row.put("matchUrl", matchUrl.trim());
            }

            out.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(row);
        }

        return out;
    }
}
