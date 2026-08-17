package dev.web.api.bm_a012;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.common.config.PathConfig;
import dev.common.s3.S3Operator;
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

		// 2) 既存のJSONをロード
		final String outputBucket = pathConfig.getS3BucketsOutputsFin();
		Set<String> existingMatchKeys = loadExistingMatchKeys(outputBucket);

		// 2) Map化
		Map<String, List<Map<String, Object>>> out = toOutputMap(req.getMatches(), existingMatchKeys);

		// 3) 次の連番をS3から決定
		final int nextSeq = s3Operator.findNextSequenceNumber(
				outputBucket,
				S3_PREFIX + FILE_PREFIX,
				FILE_PATTERN
		);

		final String fileName = FILE_PREFIX + nextSeq + ".json";

		// 4) ローカルへJSON出力
		final String jsonFolder = pathConfig.getB008JsonFolder(); // 例: /tmp/json/
		final Path jsonFilePath = Paths.get(jsonFolder, fileName);

		Files.createDirectories(jsonFilePath.getParent());
		objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFilePath.toFile(), out);

		// 5) S3へアップロード
		final String s3Key = S3_PREFIX + fileName;
		s3Operator.uploadFile(outputBucket, s3Key, jsonFilePath);

		return s3Key;
	}

	/**
	 * S3上の fin/b008_fin_getting_*.json を全て読み取り、
	 * 各ファイル内の "matchKey" 値を集めた集合を返す。
	 * ファイルが1件も無い場合は空集合を返す。
	 */
	private Set<String> loadExistingMatchKeys(String bucket) {
		Set<String> existingMatchKeys = new HashSet<>();
		List<String> existingKeys;
		try {
			// prefix配下を一覧取得し、FILE_PATTERNに一致するキー(=b008_fin_getting_data_*.json)のみに絞り込む
			existingKeys = s3Operator.listKeys(bucket, S3_PREFIX + FILE_PREFIX).stream()
					.filter(key -> FILE_PATTERN.matcher(key).matches())
					.collect(Collectors.toList());
		} catch (Exception e) {
			return existingMatchKeys;
		}
		for (String key : existingKeys) {
			try {
				String content = s3Operator.downloadTextUtf8(bucket, key);
				if (content == null || content.isBlank()) {
					continue;
				}
				Map<String, List<Map<String, Object>>> existingMap = objectMapper.readValue(
						content,
						new TypeReference<LinkedHashMap<String, List<Map<String, Object>>>>() {}
				);
				for (List<Map<String, Object>> rows : existingMap.values()) {
					if (rows == null) {
						continue;
					}
					for (Map<String, Object> row : rows) {
						Object matchKey = row.get("matchKey");
						if (matchKey != null) {
							existingMatchKeys.add(String.valueOf(matchKey).trim());
						}
					}
				}
			} catch (Exception e) {
				// 1ファイルの読み取り・パースに失敗しても全体は止めず、警告のみ出して継続する
			}
		}
		return existingMatchKeys;
	}

	private Map<String, List<Map<String, Object>>> toOutputMap(List<FinGettingRequest.Item> items,
			Set<String> existingMatchKeys) {
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

			String trimmedMatchId = matchId.trim();
	        // existingMatchKeysに存在しない場合にスキップ
	        if (!existingMatchKeys.contains(trimmedMatchId)) {
	            continue;
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

}
