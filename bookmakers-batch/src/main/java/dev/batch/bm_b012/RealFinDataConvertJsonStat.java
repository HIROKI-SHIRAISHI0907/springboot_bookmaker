package dev.batch.bm_b012;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.batch.bm_b010.SeqKeyDTO;
import dev.batch.bm_b012.FinGettingDTO.Item;
import dev.batch.repository.bm.BookDataRepository;
import dev.batch.repository.master.FutureMasterRepository;
import dev.common.config.PathConfig;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.common.util.DateOffsetDecisionUtil;

/**
 * RealFinDataConvertJsonStatロジック
 * @author shiraishitoshio
 *
 */
@Component
public class RealFinDataConvertJsonStat {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = RealFinDataConvertJsonStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = RealFinDataConvertJsonStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "REAL_FIN_DATA_CONVERT_JSON";

	private static final String S3_PREFIX = "fin/";
	private static final String FILE_PREFIX = "b008_fin_getting_data_";

	private static final Pattern FILE_PATTERN = Pattern.compile(
			"^" + Pattern.quote(S3_PREFIX + FILE_PREFIX) + "(\\d+)\\.json$"
	);

	// "2026-08-16 03:15:00.123456+00" / "2026-08-16T03:15:00Z" / "2026-08-16 03:15:00"(オフセット無し=UTC前提) を許容
	private static final Pattern RECORD_TIME_PATTERN = Pattern.compile(
	        "^(\\d{4}-\\d{2}-\\d{2})[ T](\\d{2}:\\d{2}:\\d{2})(?:\\.\\d+)?\\s*([+-]\\d{2}(?::?\\d{2})?|Z)?$");

	/** BookDataRepository */
	@Autowired
	private BookDataRepository bookDataRepository;

	/** FutureMasterRepository */
	@Autowired
	private FutureMasterRepository futureMasterRepository;

	/** JSON 生成に利用する ObjectMapper。 */
	@Autowired
	private ObjectMapper objectMapper;

	/** パスや外部実行設定（Python/S3等）を保持する設定クラス。 */
	@Autowired
	private PathConfig pathConfig;

	/** S3Operator。 */
	@Autowired
	private S3Operator s3Operator;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * FinGettingRequest(matches) を
	 * { "yyyy-MM-dd": [ {matchKey, matchUrl?}, ... ] } に変換し、
	 * 連番付きファイル名で JSON 出力 → S3へアップロードする。
	 *
	 * @return アップロードしたS3 key
	 */
	public void execute() throws Exception {
		final String METHOD_NAME = "execute";

		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 1) bookdatarepositoryから「終了済」がないデータのみ取得しjsonにmappingするためのdtoに入れ替え
		List<SeqKeyDTO> withoutFinList = bookDataRepository.findMatchIdsWithoutFinishedCategoryByTeams();
		List<Item> list = new ArrayList<FinGettingDTO.Item>();
		for (SeqKeyDTO dto : withoutFinList) {
			String gameLink = futureMasterRepository.findGameLinkWithoutFinishedCategoryByTeamsWithTeam(
					dto.getHomeTeamName(), dto.getAwayTeamName());
			FinGettingDTO.Item item = new FinGettingDTO.Item();
			LocalDate time = toJstMatchDate(dto.getRecordTime());
			if (time == null) continue;
            item.setMatchDate(time);
            item.setMatchId(dto.getMatchId());
            item.setMatchUrl(gameLink);
            list.add(item);
		}

		// 2) Map化
		Map<String, List<Map<String, Object>>> out = toOutputMap(list);

		// 3) 次の連番をS3から決定
		final String outputBucket = pathConfig.getS3BucketsOutputsFin();
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

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}


	/**
	 * FinGettingRequest(matches) を
	 * { "yyyy-MM-dd": [ {matchKey, matchUrl?}, ... ] } に変換し、
	 * 連番付きファイル名で JSON 出力 → S3へアップロードする。
	 *
	 * @return アップロードしたS3 key
	 */
	private Map<String, List<Map<String, Object>>> toOutputMap(List<FinGettingDTO.Item> items) {

		Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();

		for (int i = 0; i < items.size(); i++) {
			FinGettingDTO.Item it = items.get(i);

			LocalDate matchDate = it.getMatchDate();
			String matchId = it.getMatchId();
			String matchUrl = it.getMatchUrl();

			// 有無チェック(matchUrlはなくてもいい)
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
     * recordTime(UTC想定の文字列)をJSTに変換し、日付部分だけを取り出す
     */
	private static LocalDate toJstMatchDate(String recordTime) {
	    if (recordTime == null || recordTime.isBlank()) {
	        return null;
	    }
	    Matcher m = RECORD_TIME_PATTERN.matcher(recordTime.trim());
	    if (!m.matches()) {
	        return null; // 想定外フォーマットは変換しない
	    }

	    LocalDateTime ldt = LocalDateTime.parse(m.group(1) + "T" + m.group(2));
	    String offsetPart = m.group(3);

	    ZoneOffset offset;
	    if (offsetPart == null || offsetPart.isEmpty() || "Z".equals(offsetPart)) {
	        offset = ZoneOffset.UTC; // オフセット表記が無ければUTC前提
	    } else {
	        String normalized = offsetPart.length() == 3 ? offsetPart + ":00" : offsetPart; // "+00" -> "+00:00"
	        offset = ZoneOffset.of(normalized);
	    }

	    OffsetDateTime odtUtc = ldt.atOffset(offset);
	    OffsetDateTime odtJst = DateOffsetDecisionUtil.toOffsetDateTimeJst(odtUtc);
	    return odtJst.toLocalDate();
	}

}
