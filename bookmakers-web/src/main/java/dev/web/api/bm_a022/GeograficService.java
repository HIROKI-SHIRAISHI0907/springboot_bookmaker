package dev.web.api.bm_a022;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.common.config.PathConfig;
import dev.common.entity.TeamLocationEntity;
import dev.common.s3.S3Operator;
import dev.web.repository.master.TeamLocationWebRepository;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class GeograficService {

	private static final String S3_PREFIX = "output/";
	private static final String FILE_PREFIX = "b015_geografic_input";

	private final ObjectMapper objectMapper;
	private final PathConfig pathConfig;
	private final S3Operator s3Operator;
	private final TeamLocationWebRepository teamLocationWebRepository;

	/**
	 * GeograficRequest(matches) を
	 * [{ country, league, homeCity, stadium }, ...] に変換し、
	 * 未登録データのみ team_location_master を upsert し、
	 * 未登録データのみJSONファイルをローカル出力 → S3へアップロードする。
	 *
	 * @return アップロードしたS3 key
	 */
	public String convertAndUpload(GeograficRequest req) throws Exception {

		validateRequest(req);

		// 1) JSON出力対象データ生成（DB登録済みは除外）
		List<Map<String, Object>> out = toOutputMap(req.getMatches());

		// 2) 全件既存なら何もしない
		if (out.isEmpty()) {
			return null;
		}

		// 3) DB登録
		upsert(out);

		// 4) JSON名設定
		final String outputBucket = pathConfig.getS3Geografic();
		final String fileName = FILE_PREFIX + ".json";

		// ローカル出力先はS3バケット名ではなくローカルフォルダを使う
		final String jsonFolder = pathConfig.getB008JsonFolder();
		final Path jsonFilePath = Paths.get(jsonFolder, fileName);

		Files.createDirectories(jsonFilePath.getParent());
		objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFilePath.toFile(), out);

		// 5) S3へアップロード
		final String s3Key = S3_PREFIX + fileName;
		s3Operator.uploadFile(outputBucket, s3Key, jsonFilePath);

		return s3Key;
	}

	private void validateRequest(GeograficRequest req) {

		if (req == null || req.getMatches() == null || req.getMatches().isEmpty()) {
			throw new IllegalArgumentException("matches がありません（または空です）");
		}
	}

	/**
	 * JSON出力用データ作成
	 *
	 * <p>
	 * 以下を除外する:
	 * </p>
	 * <ul>
	 *   <li>入力不正データ</li>
	 *   <li>同一リクエスト内の重複データ</li>
	 *   <li>すでにDB登録済みのデータ</li>
	 * </ul>
	 */
	private List<Map<String, Object>> toOutputMap(List<GeograficRequest.Item> items) {

		List<Map<String, Object>> out = new ArrayList<>();
		Set<String> requestDuplicateGuard = new HashSet<>();

		for (int i = 0; i < items.size(); i++) {
			GeograficRequest.Item it = items.get(i);

			String country = normalizeRequired(it.getCountry(), "country", i);
			String league = normalizeRequired(it.getLeague(), "league", i);
			String stadium = normalizeRequired(it.getStadium(), "stadium", i);
			String homeCity = normalizeOptional(it.getHomeCity());

			// 同一リクエスト内の重複除外
			String naturalKey = buildNaturalKey(country, homeCity, stadium);
			if (!requestDuplicateGuard.add(naturalKey)) {
				continue;
			}

			// すでにDB登録済みならJSON出力対象にしない
			Optional<Integer> idOpt = teamLocationWebRepository.findIdByNaturalKey(
					country,
					homeCity,
					stadium);

			if (idOpt.isPresent()) {
				continue;
			}

			Map<String, Object> row = new HashMap<>();
			row.put("country", country);
			row.put("league", league);
			row.put("homeCity", homeCity);
			row.put("stadium", stadium);

			out.add(row);
		}

		return out;
	}

	/**
	 * DB登録
	 * @param map JSON出力対象データ
	 */
	private void upsert(List<Map<String, Object>> map) {

		for (Map<String, Object> entry : map) {

			String country = toNullableString(entry.get("country"));
			String homeCity = toNullableString(entry.get("homeCity"));
			String stadium = toNullableString(entry.get("stadium"));

			if (country == null || country.isBlank() || stadium == null || stadium.isBlank()) {
				continue;
			}

			try {

				TeamLocationEntity entity = new TeamLocationEntity();
				entity.setCountry(country);
				entity.setHomeCity(homeCity);
				entity.setStadiumName(stadium);
				entity.setGeocodeSource("input_json");

				int rows = teamLocationWebRepository.insert(entity);
				if (rows != 1) {
					throw new RuntimeException(
							"team_location_master insert affected rows=" + rows
									+ " country=" + country
									+ " homeCity=" + homeCity
									+ " stadium=" + stadium);
				}

			} catch (Exception e) {
				throw new RuntimeException(
						"team_location_master upsert failed. "
								+ "country=" + country
								+ ", homeCity=" + homeCity
								+ ", stadium=" + stadium,
						e);
			}
		}
	}

	private String toNullableString(Object value) {
		if (value == null) {
			return null;
		}
		String s = String.valueOf(value).trim();
		return s.isEmpty() ? null : s;
	}

	private String normalizeRequired(String value, String fieldName, int index) {

		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " がありません: index=" + index);
		}
		return value.trim();
	}

	private String normalizeOptional(String value) {

		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private String buildNaturalKey(String country, String homeCity, String stadium) {
		return country + "||" + (homeCity == null ? "" : homeCity) + "||" + stadium;
	}

}
