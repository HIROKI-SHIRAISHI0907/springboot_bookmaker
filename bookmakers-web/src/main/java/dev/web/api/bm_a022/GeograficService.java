package dev.web.api.bm_a022;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	 * マスタデータに登録かつ住所が未登録のみJSONファイルをローカル出力 → S3へアップロードする。
	 *
	 * @return アップロードしたS3 key
	 */
	public String convertAndUpload() throws Exception {

		// 1) JSON出力対象データ生成（DB登録済みは除外）
		List<Map<String, Object>> out = toOutputMap();

		// 2) 全件既存なら何もしない
		if (out.isEmpty()) {
			return null;
		}

		// 3) JSON名設定
		final String outputBucket = pathConfig.getS3Geografic();
		final String fileName = FILE_PREFIX + ".json";

		// ローカル出力先はS3バケット名ではなくローカルフォルダを使う
		final String jsonFolder = pathConfig.getB008JsonFolder();
		final Path jsonFilePath = Paths.get(jsonFolder, fileName);

		Files.createDirectories(jsonFilePath.getParent());
		objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFilePath.toFile(), out);

		// 4) S3へアップロード
		final String s3Key = S3_PREFIX + fileName;
		s3Operator.uploadFile(outputBucket, s3Key, jsonFilePath);

		return s3Key;
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
	private List<Map<String, Object>> toOutputMap() {

		List<Map<String, Object>> out = new ArrayList<>();
		// API叩かれていないレコード対象にしてJSON出力する
		List<TeamLocationEntity> idOpt = teamLocationWebRepository.findByNaturalKey();
		if (idOpt.isEmpty()) {
			return new ArrayList<Map<String,Object>>();
		}

		for (TeamLocationEntity entity : idOpt) {
			Map<String, Object> row = new HashMap<>();
			row.put("country", entity.getCountry());
			row.put("teamName", entity.getTeamName());
			row.put("homeCity", entity.getHomeCity());
			row.put("stadium", entity.getStadiumName());

			out.add(row);
		}

		return out;
	}

}
