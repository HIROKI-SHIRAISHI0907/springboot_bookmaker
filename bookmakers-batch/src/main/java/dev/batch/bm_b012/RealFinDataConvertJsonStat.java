package dev.batch.bm_b012;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
 *
 * <p>前日（日本時間 0:00〜23:59）に行われた試合のうち、static_data に「終了済」（times 列）の行が無い試合を
 * { "yyyy-MM-dd": [ {matchKey, matchUrl?}, ... ] } の JSON にして S3（fin/b008_fin_getting_data_N.json）へ出力する。</p>
 *
 * <p>2026-10 修正</p>
 * <ul>
 *   <li>「終了済」は data_category ではなく times 列で判定する（Repository 側）。</li>
 *   <li>static_data を前日の範囲（日付をまたぐ試合のため終わりは +6 時間）に絞り、match_id 単位で1件にする
 *       （過去シーズンの同じカードを拾わない）。</li>
 *   <li>record_time（timestamp・タイムゾーンなし＝日本時間）を UTC とみなして +9 時間していたのを修正。</li>
 *   <li>matchUrl は mid が matchKey と一致するものを優先して取り、一致しない URL は付けない
 *       （別の試合のページを読んで matchKey の試合として保存するのを防ぐ）。</li>
 *   <li>出力する日付は前日（日本時間）のものだけ。</li>
 * </ul>
 * @author shiraishitoshio
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

	/** URL の mid= */
	private static final Pattern MID_PATTERN = Pattern.compile("[?&]mid=([^&#]+)");

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
	 * 前日分の「終了済が無い試合」を JSON にして S3 へアップロードする。
	 * 既に fin/b008_fin_getting_data_*.json に出力済みの matchKey は除外する。
	 * 同じ matchId は1件だけ（record_time が一番古い行の日付）。
	 */
	public void execute() throws Exception {
		final String METHOD_NAME = "execute";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		try {
			run(METHOD_NAME);
		} finally {
			this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();
		}
	}

	private void run(String METHOD_NAME) throws Exception {
		final String outputBucket = pathConfig.getS3BucketsOutputsFin();

		// バッチは日付変更直後(00:10想定)に実行し、「前日1日分」(日本時間)を UTC の ISO 文字列で受け取る
		String[] previousDayRange = DateOffsetDecisionUtil.previousDayRangeAsUtcIsoStrings();
		String dayStart = previousDayRange[0];
		String dayEnd = previousDayRange[1];
		String jstRange = DateOffsetDecisionUtil.toIsoJstRangeString(previousDayRange);
		info(METHOD_NAME, "システム時間検索期間: " + dayStart + "~" + dayEnd + "(日本時間換算: " + jstRange + ")");

		// 出力してよい日付（前日・日本時間）
		LocalDate targetDate = toJstMatchDate(dayStart);
		if (targetDate == null) {
			throw new IllegalStateException("前日の日付を求められません: " + dayStart);
		}

		// 0) 前日に予定されていた試合（future_master）
		List<FutureEntity> futureList = futureMasterRepository.findTodayFinData(dayStart, dayEnd);
		info(METHOD_NAME, "前日の予定試合件数: " + futureList.size());
		if (futureList.isEmpty()) {
			info(METHOD_NAME, "前日の予定試合が0件のためスキップします");
			return;
		}
		List<TeamPair> teamPairs = futureList.stream()
				.map(f -> new TeamPair(f.getHomeTeamName(), f.getAwayTeamName()))
				.collect(Collectors.toList());

		// 1) static_data のうち、前日の範囲に記録があり、その match_id に「終了済」(times) が無い試合（match_id ごとに1件）
		List<SeqKeyDTO> withoutFinList = bookDataRepository.findMatchIdsWithoutFinishedCategoryByTeams(
				teamPairs, dayStart, dayEnd);
		info(METHOD_NAME, "終了済が無い試合件数: " + withoutFinList.size());

		// 既に出力済みの matchKey
		Set<String> existingMatchKeys = loadExistingMatchKeys(outputBucket);
		info(METHOD_NAME, "既存matchKey件数: " + existingMatchKeys.size());

		Map<String, FinGettingDTO.Item> itemsByMatchId = new LinkedHashMap<>();
		int skippedExisting = 0;
		int skippedOtherDate = 0;
		int urlMismatch = 0;
		for (SeqKeyDTO dto : withoutFinList) {
			String matchId = dto.getMatchId() == null ? "" : dto.getMatchId().trim();
			if (matchId.isEmpty()) {
				continue;
			}
			if (existingMatchKeys.contains(matchId)) {
				skippedExisting++;
				continue;
			}
			LocalDate matchDate = toJstMatchDate(dto.getRecordTime());
			if (matchDate == null) {
				continue;
			}
			// 前日（日本時間）以外の日付は出さない（念のため）
			if (!targetDate.equals(matchDate)) {
				skippedOtherDate++;
				continue;
			}

			// mid が matchId と一致する URL を優先して取る。一致しなければ URL を付けない（unresolved 扱い）
			String gameLink = futureMasterRepository.findGameLinkForMatch(
					dto.getHomeTeamName(), dto.getAwayTeamName(), matchId, dto.getRecordTime());
			if (gameLink != null && !matchId.equals(extractMid(gameLink))) {
				urlMismatch++;
				info(METHOD_NAME, "URL の mid が matchKey と一致しないため URL を付けません matchKey=" + matchId
						+ " url=" + gameLink);
				gameLink = null;
			}

			FinGettingDTO.Item item = new FinGettingDTO.Item();
			item.setMatchDate(matchDate);
			item.setMatchId(matchId);
			item.setMatchUrl(gameLink);

			FinGettingDTO.Item existing = itemsByMatchId.get(matchId);
			if (existing == null || item.getMatchDate().isBefore(existing.getMatchDate())) {
				itemsByMatchId.put(matchId, item);
			}
		}
		info(METHOD_NAME, "既存matchKeyでスキップ: " + skippedExisting
				+ " / 前日以外でスキップ: " + skippedOtherDate
				+ " / URL不一致: " + urlMismatch
				+ " / 出力対象: " + itemsByMatchId.size());

		if (itemsByMatchId.isEmpty()) {
			info(METHOD_NAME, "出力対象が0件のためファイル出力・アップロードをスキップします");
			return;
		}

		// 2) 出力用 Map（日付キー昇順）
		Map<String, List<Map<String, Object>>> out = toOutputMap(itemsByMatchId.values());

		// 3) 次の連番を S3 から決定
		final int nextSeq = s3Operator.findNextSequenceNumber(outputBucket, S3_PREFIX + FILE_PREFIX, FILE_PATTERN);
		final String fileName = FILE_PREFIX + nextSeq + ".json";

		// 4) ローカルへ JSON 出力
		final Path jsonFilePath = Paths.get(pathConfig.getB008JsonFolder(), fileName);
		Files.createDirectories(jsonFilePath.getParent());
		objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFilePath.toFile(), out);

		// 5) S3 へアップロード
		final String s3Key = S3_PREFIX + fileName;
		s3Operator.uploadFile(outputBucket, s3Key, jsonFilePath);
		info(METHOD_NAME, "アップロード完了: s3://" + outputBucket + "/" + s3Key + " 件数=" + itemsByMatchId.size());
	}

	private void info(String method, String message) {
		this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, method, MessageCdConst.MCD00099I_LOG, message);
	}

	/**
	 * S3 上の fin/b008_fin_getting_data_*.json を全て読み、"matchKey" の集合を返す。
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
			info(METHOD_NAME, "既存jsonファイル一覧の取得に失敗したため既存matchKeyチェックをスキップします: " + e.getMessage());
			return existingMatchKeys;
		}
		for (String key : existingKeys) {
			try {
				String content = s3Operator.downloadTextUtf8(bucket, key);
				if (content == null || content.isBlank()) {
					continue;
				}
				Map<String, List<Map<String, Object>>> existingMap = objectMapper.readValue(content,
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
				info(METHOD_NAME, "既存jsonファイルの読み取りに失敗しました key=" + key + " error=" + e.getMessage());
			}
		}
		return existingMatchKeys;
	}

	/**
	 * Item の集まりを、日付キー（昇順）→ [{matchKey, matchUrl?}, ...] の Map にする。
	 */
	private Map<String, List<Map<String, Object>>> toOutputMap(Collection<FinGettingDTO.Item> items) {
		Map<String, List<Map<String, Object>>> out = new TreeMap<>();
		int i = 0;
		for (FinGettingDTO.Item it : items) {
			if (it.getMatchDate() == null) {
				throw new IllegalArgumentException("matchDate がありません: index=" + i);
			}
			if (it.getMatchId() == null || it.getMatchId().isBlank()) {
				throw new IllegalArgumentException("matchId がありません: index=" + i);
			}
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("matchKey", it.getMatchId().trim());
			if (it.getMatchUrl() != null && !it.getMatchUrl().isBlank()) {
				row.put("matchUrl", it.getMatchUrl().trim());
			}
			out.computeIfAbsent(it.getMatchDate().toString(), k -> new ArrayList<>()).add(row);
			i++;
		}
		return out;
	}

	/** URL の mid=（無ければ null） */
	static String extractMid(String url) {
		if (url == null) {
			return null;
		}
		Matcher m = MID_PATTERN.matcher(url);
		return m.find() ? m.group(1).trim() : null;
	}

	/**
	 * 日時文字列 → 日本時間の日付。
	 * <ul>
	 *   <li>オフセット無し（static_data.record_time の timestamp）: すでに日本時間なのでそのままの日付</li>
	 *   <li>Z / +09:00 などのオフセット付き: 日本時間に直した日付</li>
	 * </ul>
	 */
	static LocalDate toJstMatchDate(String time) {
		if (time == null || time.isBlank()) {
			return null;
		}
		Matcher m = RECORD_TIME_PATTERN.matcher(time.trim());
		if (!m.matches()) {
			return null;
		}
		LocalDateTime ldt = LocalDateTime.parse(m.group(1) + "T" + m.group(2));
		String offsetPart = m.group(3);
		if (offsetPart == null || offsetPart.isEmpty()) {
			return ldt.toLocalDate();
		}
		ZoneOffset offset = "Z".equals(offsetPart)
				? ZoneOffset.UTC
				: ZoneOffset.of(offsetPart.length() == 3 ? offsetPart + ":00" : offsetPart);
		OffsetDateTime odtJst = DateOffsetDecisionUtil.toOffsetDateTimeJst(ldt.atOffset(offset));
		return odtJst.toLocalDate();
	}
}
