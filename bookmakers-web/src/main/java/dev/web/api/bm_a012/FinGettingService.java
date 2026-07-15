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
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.common.config.PathConfig;
import dev.common.entity.MatchKeySaveEntity;
import dev.common.s3.S3Operator;
import dev.web.repository.bm.MatchKeyRepository;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class FinGettingService {

	private static final String S3_PREFIX = "fin/";
	private static final String FILE_PREFIX = "b008_fin_getting_data_";

	private static final Pattern FILE_PATTERN = Pattern.compile(
			"^" + Pattern.quote(S3_PREFIX + FILE_PREFIX) + "(\\d+)\\.json$"
	);

	private final ObjectMapper objectMapper;
	private final PathConfig pathConfig;
	private final S3Operator s3Operator;

	private final MatchKeyRepository matchKeyRepository;

	/**
	 * FinGettingRequest(matches) を
	 * { "yyyy-MM-dd": [ {matchKey, matchUrl?}, ... ] } に変換し、
	 * 連番付きファイル名で JSON 出力 → S3へアップロードする。
	 *
	 * @return アップロードしたS3 key
	 */
	public String convertAndUpload(FinGettingRequest req) throws Exception {

		// 1) 入力チェック
		if (req == null || req.getMatches() == null || req.getMatches().isEmpty()) {
			throw new IllegalArgumentException("matches がありません（または空です）");
		}

		// 2) Map化
		Map<String, List<Map<String, Object>>> out = toOutputMap(req.getMatches());

		// 3) DB登録
		upsert(out);

		// 4) 次の連番をS3から決定
		final String outputBucket = pathConfig.getS3BucketsOutputsFin();
		final int nextSeq = s3Operator.findNextSequenceNumber(
				outputBucket,
				S3_PREFIX + FILE_PREFIX,
				FILE_PATTERN
		);

		final String fileName = FILE_PREFIX + nextSeq + ".json";

		// 5) ローカルへJSON出力
		final String jsonFolder = pathConfig.getB008JsonFolder(); // 例: /tmp/json/
		final Path jsonFilePath = Paths.get(jsonFolder, fileName);

		Files.createDirectories(jsonFilePath.getParent());
		objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFilePath.toFile(), out);

		// 6) S3へアップロード
		final String s3Key = S3_PREFIX + fileName;
		s3Operator.uploadFile(outputBucket, s3Key, jsonFilePath);

		return s3Key;
	}

	private Map<String, List<Map<String, Object>>> toOutputMap(List<FinGettingRequest.Item> items) {

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

			String dateKey = matchDate.toString();

			Map<String, Object> row = new HashMap<>();
			row.put("matchKey", matchId.trim());

			if (matchUrl != null && !matchUrl.isBlank()) {
				row.put("matchUrl", matchUrl.trim());
			}

			out.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(row);
		}

		return out;
	}

	/**
	 * DB登録
	 * @param map
	 */
	private void upsert(Map<String, List<Map<String, Object>>> map) {

		for (Map.Entry<String, List<Map<String, Object>>> entry : map.entrySet()) {
			for (Map<String, Object> obj : entry.getValue()) {

				String mk = (String) obj.get("matchKey");
				if (mk == null || mk.isBlank()) {
					continue;
				}

				Optional<String> idOpt = matchKeyRepository.findMatchKeyId(mk);
				if (idOpt.isPresent()) {
					continue;
				}

				MatchKeySaveEntity entity = new MatchKeySaveEntity();
				entity.setMatchKey(mk);

				try {
					int rows = matchKeyRepository.insert(entity);
					if (rows != 1) {
						throw new RuntimeException("match_key_save insert affected rows=" + rows + " matchKey=" + mk);
					}
				} catch (Exception e) {
					throw new RuntimeException("match_key_save insert failed. matchKey=" + mk, e);
				}
			}
		}
	}
}
