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
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.common.config.PathConfig;
import dev.common.entity.MatchKeySaveEntity;
import dev.common.s3.S3Operator;
import dev.web.api.bm_a009.EcsScrapeTaskProgressResponse;
import dev.web.api.bm_a009.EcsScrapeTaskProgressService;
import dev.web.repository.bm.MatchKeyRepository;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class FinGettingService {

	private final EcsScrapeTaskProgressService ecsService;

    private final ObjectMapper objectMapper;
    private final PathConfig pathConfig;
    private final S3Operator s3Operator;

    private final MatchKeyRepository matchKeyRepository;

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

        // 3) DB登録
        upsert(out);

        // 4) ローカルへJSON出力（指定のここ）
        final String outputBucket = pathConfig.getS3BucketsOutputs();

        final String jsonFolder = pathConfig.getB008JsonFolder(); // 例: /tmp/json/
        final String fileName = "b008_fin_getting_data.json";
        final Path jsonFilePath = Paths.get(jsonFolder, fileName);

        Files.createDirectories(jsonFilePath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFilePath.toFile(), out);

        // 5) S3へアップロード
        final String s3Key = "fin/" + jsonFilePath.getFileName().toString(); // 例: fin/b008_fin_getting.json
        s3Operator.uploadFile(outputBucket, s3Key, jsonFilePath);

        return s3Key;
    }

    /**
     * 進捗管理
     * @return
     * @throws InterruptedException
     */
    public void getProgress() throws InterruptedException {
        while (true) {
        	// 6) ECS Taskが完了するまで処理を進ませない
            EcsScrapeTaskProgressResponse res = ecsService.getLatestProgress("B010");
            if ("STOPPED".equals(res.getStatus())) {
            	Integer exitCd = res.getExitCd();
            	if (exitCd != null && exitCd != 0) {
            		throw new RuntimeException("ECS task failed. exitCd=" + exitCd);
            	}
            	break;
            }

            Thread.sleep(10000);
        }
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

    /**
     * 登録
     * @param map
     */
    private void upsert(Map<String, List<Map<String, Object>>> map) {
    	for (Map.Entry<String, List<Map<String, Object>>> entry : map.entrySet()) {
    		List<Map<String, Object>> listMap = entry.getValue();
    		for (Map<String, Object> obj : listMap) {
    			Optional<String> matchKey = matchKeyRepository.findMatchKeyId(
    					(String) obj.get("matchKey"));
    			if (matchKey.isEmpty()) {
    				try {
    					MatchKeySaveEntity entity = new MatchKeySaveEntity();
    					entity.setMatchKey(matchKey.toString());
    					matchKeyRepository.insert(entity);
					} catch (Exception ignore) {}
    			}
    		}
    	}
    }
}
