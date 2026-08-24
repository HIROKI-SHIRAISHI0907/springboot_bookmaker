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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.batch.bm_b010.SeqKeyDTO;
import dev.batch.repository.bm.BookDataRepository;
import dev.batch.repository.master.FutureMasterRepository;
import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.entity.FutureEntity;
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
			"^" + Pattern.quote(S3_PREFIX + FILE_PREFIX) + "(\\d+)\\.json$");
	private static final Pattern RECORD_TIME_PATTERN = Pattern.compile(
			"^(\\d{4}-\\d{2}-\\d{2})[ T](\\d{2}:\\d{2}:\\d{2})(?:\\.\\d+)?\\s*([+-]\\d{2}(?::?\\d{2})?|Z)?$");
	@Autowired
	private BookDataRepository bookDataRepository;
	@Autowired
	private FutureMasterRepository futureMasterRepository;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private PathConfig pathConfig;
	@Autowired
	private S3Operator s3Operator;
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * FinGettingRequest(matches) を
	 * { "yyyy-MM-dd": [ {matchKey, matchUrl?}, ... ] } に変換し、
	 * 連番付きファイル名で JSON 出力 → S3へアップロードする。
	 * 既に fin/b008_fin_getting_*.json に出力済みの matchKey は対象から除外する。
	 *
	 * 既存matchKeyの読み取り〜アップロードまでは、FinGettingService(API側)と
	 * 同じロックキー(B008OutputLockKeys.B008_FIN_GETTING_JSON)で排他制御する。
	 */
	public void execute() throws Exception {
		final String METHOD_NAME = "execute";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		final String outputBucket = pathConfig.getS3BucketsOutputsFin();

		// バッチは日付変更直後(00:10想定)に実行し、「前日1日分」をUTC時間の検索範囲として使う
		String[] previousDayRange = DateOffsetDecisionUtil.previousDayRangeAsUtcIsoStrings();
		String todayStart = previousDayRange[0];
		String todayEnd = previousDayRange[1];
		this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"システム時間検索期間: " + todayStart + "~" + todayEnd +
						"(日本時間換算: " + DateOffsetDecisionUtil.toIsoJstRangeString(previousDayRange) + ")");
		List<FutureEntity> todayFutureList = futureMasterRepository.findTodayFinData(todayStart, todayEnd);
		// 1) 「その日の」bookdatarepositoryから「終了済」がないデータのみ取得しjsonにmappingするためのdtoに入れ替え
		List<TeamPair> teamPairs = todayFutureList.stream()
				.map(f -> new TeamPair(f.getHomeTeamName(), f.getAwayTeamName()))
				.collect(Collectors.toList());
		List<SeqKeyDTO> withoutFinList = bookDataRepository.findMatchIdsWithoutFinishedCategoryByTeams(
				teamPairs);

		// 既存matchKeyの読み取り〜アップロードまでを排他制御する。
		// existingMatchKeysの読み取りをロック内に含めることで、API側とのタイミングずれによる
		// 重複出力・連番衝突による上書き消失の両方を防ぐ。
		Set<String> existingMatchKeys = loadExistingMatchKeys(outputBucket);
		this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"既存matchKey件数: " + existingMatchKeys.size());

		Set<FinGettingDTO.Item> list = new HashSet<FinGettingDTO.Item>();
		int skippedCount = 0;
		for (SeqKeyDTO dto : withoutFinList) {
			// 既存jsonに同じmatchKey(=matchId)が既にあればスキップ
			if (dto.getMatchId() != null && !existingMatchKeys.contains(dto.getMatchId().trim())) {
				skippedCount++;
				continue;
			}
			String gameLink = futureMasterRepository.findGameLinkWithoutFinishedCategoryByTeamsWithTeam(
					dto.getHomeTeamName(), dto.getAwayTeamName());
			FinGettingDTO.Item item = new FinGettingDTO.Item();
			LocalDate time = toJstMatchDate(dto.getRecordTime());
			if (time == null)
				continue;
			item.setMatchDate(time);
			item.setMatchId(dto.getMatchId());
			item.setMatchUrl(gameLink);
			list.add(item);
		}
		this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"既存matchKeyによりスキップした件数: " + skippedCount);
		if (list.isEmpty()) {
			this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					"出力対象が0件のためファイル出力・アップロードをスキップします");
			return;
		}
		// 2) Map化
		Map<String, List<Map<String, Object>>> out = toOutputMap(list);
		// 3) 次の連番をS3から決定
		final int nextSeq = s3Operator.findNextSequenceNumber(
				outputBucket,
				S3_PREFIX + FILE_PREFIX,
				FILE_PATTERN);
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
	 * S3上の fin/b008_fin_getting_*.json を全て読み取り、
	 * 各ファイル内の "matchKey" 値を集めた集合を返す。
	 * ファイルが1件も無い場合は空集合を返す。
	 */
	private Set<String> loadExistingMatchKeys(String bucket) {
		final String METHOD_NAME = "loadExistingMatchKeys";
		Set<String> existingMatchKeys = new HashSet<>();
		List<String> existingKeys;
		try {
			existingKeys = s3Operator.listKeys(bucket, S3_PREFIX + FILE_PREFIX).stream()
					.filter(key -> FILE_PATTERN.matcher(key).matches())
					.collect(Collectors.toList());
		} catch (Exception e) {
			this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					"既存jsonファイル一覧の取得に失敗したため既存matchKeyチェックをスキップします: " + e.getMessage());
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
						new TypeReference<LinkedHashMap<String, List<Map<String, Object>>>>() {
						});
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
				this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00099I_LOG, "既存jsonファイルの読み取りに失敗しました key=" + key + " error=" + e.getMessage());
			}
		}
		return existingMatchKeys;
	}

	private Map<String, List<Map<String, Object>>> toOutputMap(Set<FinGettingDTO.Item> items) {
		Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
		int i = 0;
		for (FinGettingDTO.Item it : items) {
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
			i++;
		}
		return out;
	}

	private static LocalDate toJstMatchDate(String recordTime) {
		if (recordTime == null || recordTime.isBlank()) {
			return null;
		}
		Matcher m = RECORD_TIME_PATTERN.matcher(recordTime.trim());
		if (!m.matches()) {
			return null;
		}
		LocalDateTime ldt = LocalDateTime.parse(m.group(1) + "T" + m.group(2));
		String offsetPart = m.group(3);
		ZoneOffset offset;
		if (offsetPart == null || offsetPart.isEmpty() || "Z".equals(offsetPart)) {
			offset = ZoneOffset.UTC;
		} else {
			String normalized = offsetPart.length() == 3 ? offsetPart + ":00" : offsetPart;
			offset = ZoneOffset.of(normalized);
		}
		OffsetDateTime odtUtc = ldt.atOffset(offset);
		OffsetDateTime odtJst = DateOffsetDecisionUtil.toOffsetDateTimeJst(odtUtc);
		return odtJst.toLocalDate();
	}
}