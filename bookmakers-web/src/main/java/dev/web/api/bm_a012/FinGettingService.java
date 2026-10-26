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
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.common.config.PathConfig;
import dev.common.constant.B008OutputLockKeysConst;
import dev.common.lock.PgAdvisoryLock;
import dev.common.s3.S3Operator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 終了済データ不足用の JSON 作成
 *
 * <ol>
 *   <li>fin/b008_fin_getting_data_N.json（B010 の入力）を作る（既存の処理）。</li>
 *   <li>b016_game_detail_path.json（B016 試合詳細スクレイピングの入力）を aws-s3-detail-data-csv の直下に作る（2026-10 追加）。
 *       {@link #uploadGameDetailPath} を参照。</li>
 * </ol>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class FinGettingService {
	private static final String S3_PREFIX = "fin/";
	private static final String FILE_PREFIX = "b008_fin_getting_data_";
	private static final Pattern FILE_PATTERN = Pattern.compile(
			"^" + Pattern.quote(S3_PREFIX + FILE_PREFIX) + "(\\d+)\\.json$"
	);
	/** B016 の入力 JSON（aws-s3-detail-data-csv の直下） */
	public static final String GAME_DETAIL_PATH_KEY = "b016_game_detail_path.json";

	/** matchUrl が相対パスのときに付けるサイト */
	private static final String SITE = "https://www.flashscore.co.jp";

	/** URL の mid= */
	private static final Pattern MID_PATTERN = Pattern.compile("[?&]mid=([A-Za-z0-9]+)");

	/** 古い形式の URL: /match/&lt;試合ID 8 文字&gt;/ */
	private static final Pattern LEGACY_MID_PATTERN = Pattern.compile("/match/([A-Za-z0-9]{8})/?(?:[?#].*)?$");

	private final ObjectMapper objectMapper;
	private final PathConfig pathConfig;
	private final S3Operator s3Operator;
	private final PgAdvisoryLock advisoryLock;
	/**
	 * FinGettingRequest(matches) を
	 * { "yyyy-MM-dd": [ {matchKey, matchUrl?}, ... ] } に変換し、
	 * 連番付きファイル名で JSON 出力 → S3へアップロードする。
	 *
	 * 既存matchKeyの読み取り〜アップロードまでは、RealFinDataConvertJsonStat(バッチ側)と
	 * 同じロックキー(B008OutputLockKeys.B008_FIN_GETTING_JSON)で排他制御する。
	 *
	 * 続けて b016_game_detail_path.json（B016 の入力）も作る（{@link #uploadGameDetailPath}）。
	 *
	 * @return アップロードしたS3 key（fin の JSON）
	 */
	public String convertAndUpload(FinGettingRequest req) throws Exception {
		// 1) 入力チェック(S3状態に依存しないためロック外)
		if (req == null || req.getMatches() == null || req.getMatches().isEmpty()) {
			throw new IllegalArgumentException("matches がありません（または空です）");
		}
		final String outputBucket = pathConfig.getS3BucketsOutputsFin();

		// 2)〜5) 既存matchKeyの読み取りからアップロードまでを排他制御する
		final String finKey = advisoryLock.runExclusive(B008OutputLockKeysConst.B008_FIN_GETTING_JSON, () -> {
			Set<String> existingMatchKeys = loadExistingMatchKeys(outputBucket);

			Map<String, List<Map<String, Object>>> out = toOutputMap(req.getMatches(), existingMatchKeys);

			final int nextSeq = s3Operator.findNextSequenceNumber(
					outputBucket,
					S3_PREFIX + FILE_PREFIX,
					FILE_PATTERN
			);
			final String fileName = FILE_PREFIX + nextSeq + ".json";

			final String jsonFolder = pathConfig.getB008JsonFolder(); // 例: /tmp/json/
			final Path jsonFilePath = Paths.get(jsonFolder, fileName);
			Files.createDirectories(jsonFilePath.getParent());
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFilePath.toFile(), out);

			final String s3Key = S3_PREFIX + fileName;
			s3Operator.uploadFile(outputBucket, s3Key, jsonFilePath);
			return s3Key;
		});

		// 6) B016 試合詳細スクレイピングの入力 JSON も作る（fin の JSON とは独立。失敗したら例外で知らせる）
		uploadGameDetailPath(req.getMatches());

		return finKey;
	}

	/**
	 * b016_game_detail_path.json（B016 試合詳細スクレイピングの入力）を作り、aws-s3-detail-data-csv の直下に置く。
	 *
	 * <ul>
	 *   <li>対象: リクエストの matches のうち、matchUrl があり試合ID（?mid= または古い形式 /match/&lt;試合ID&gt;/）が分かるもの。
	 *       matchUrl が無い・試合ID が分からないものは入れない（ログに出す）。</li>
	 *   <li>matchUrl の mid と matchId が違うものは入れない（別の試合のページを取らないように）。</li>
	 *   <li>まだスクレイパーが読んでいない JSON が残っていれば、その中身と合わせる（同じ試合は 1 件。新しいリクエストを優先）。
	 *       スクレイパーは読み終わると JSON を消すので、残っている = 未処理。</li>
	 *   <li>形: { "yyyy-MM-dd": [ {"url": ..., "matchKey": ...}, ... ] }（日付キーの昇順）</li>
	 * </ul>
	 *
	 * @param items リクエストの matches
	 * @return 書いた試合数（0 なら何もしない）
	 */
	public int uploadGameDetailPath(List<FinGettingRequest.Item> items) throws Exception {
		final String bucket = pathConfig.getS3BucketsDetailData();

		// matchKey → {date, row}（同じ試合は 1 件）
		Map<String, String> dateByMid = new LinkedHashMap<>();
		Map<String, Map<String, Object>> rowByMid = new LinkedHashMap<>();

		// まだ読まれていない既存の JSON があれば先に入れる（後から入れるリクエストの方で上書き）
		if (s3Operator.existsOnS3(bucket, GAME_DETAIL_PATH_KEY)) {
			try {
				String content = s3Operator.downloadTextUtf8(bucket, GAME_DETAIL_PATH_KEY);
				if (content != null && !content.isBlank()) {
					Map<String, List<Map<String, Object>>> existing = objectMapper.readValue(content,
							new TypeReference<LinkedHashMap<String, List<Map<String, Object>>>>() {});
					for (Map.Entry<String, List<Map<String, Object>>> e : existing.entrySet()) {
						if (e.getValue() == null) {
							continue;
						}
						for (Map<String, Object> row : e.getValue()) {
							Object url = row.get("url");
							String mid = extractMid(url == null ? null : String.valueOf(url));
							if (mid == null) {
								continue;
							}
							dateByMid.put(mid, e.getKey());
							rowByMid.put(mid, row);
						}
					}
				}
			} catch (Exception e) {
				// 読めない（形が違う）既存 JSON は捨てて作り直す
				log.warn("[A012] 既存の {} を読めないため作り直します: {}", GAME_DETAIL_PATH_KEY, e.getMessage());
			}
		}

		int added = 0;
		int noUrl = 0;
		int mismatch = 0;
		for (FinGettingRequest.Item it : items) {
			if (it == null || it.getMatchDate() == null) {
				continue;
			}
			String url = normalizeUrl(it.getMatchUrl());
			String mid = extractMid(url);
			if (mid == null) {
				noUrl++;
				log.info("[A012] matchUrl が無い・試合ID が分からないため B016 の JSON に入れません: matchId={} url={}",
						it.getMatchId(), it.getMatchUrl());
				continue;
			}
			String matchId = it.getMatchId() == null ? "" : it.getMatchId().trim();
			if (!matchId.isEmpty() && !matchId.equals(mid)) {
				mismatch++;
				log.info("[A012] matchUrl の mid が matchId と違うため B016 の JSON に入れません: matchId={} url={}",
						matchId, url);
				continue;
			}
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("url", url);
			row.put("matchKey", mid);
			dateByMid.remove(mid);
			rowByMid.remove(mid);
			dateByMid.put(mid, it.getMatchDate().toString());
			rowByMid.put(mid, row);
			added++;
		}

		if (rowByMid.isEmpty()) {
			log.info("[A012] B016 の JSON に入れる試合がありません（matchUrl なし={}, mid 不一致={}）", noUrl, mismatch);
			return 0;
		}

		// 日付キー（昇順）→ [{url, matchKey}, ...]
		Map<String, List<Map<String, Object>>> out = new TreeMap<>();
		for (Map.Entry<String, Map<String, Object>> e : rowByMid.entrySet()) {
			out.computeIfAbsent(dateByMid.get(e.getKey()), k -> new ArrayList<>()).add(e.getValue());
		}

		String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(out);
		s3Operator.putJson(bucket, GAME_DETAIL_PATH_KEY, json);
		log.info("[A012] アップロード完了: s3://{}/{} 試合数={}（今回 {}・matchUrl なし {}・mid 不一致 {}）",
				bucket, GAME_DETAIL_PATH_KEY, rowByMid.size(), added, noUrl, mismatch);
		return rowByMid.size();
	}

	/** 相対パス（/match/...）ならサイトを付ける。前後の空白は除く。空なら null */
	static String normalizeUrl(String link) {
		if (link == null || link.isBlank()) {
			return null;
		}
		String u = link.trim();
		if (u.startsWith("//")) {
			return "https:" + u;
		}
		if (u.startsWith("/")) {
			return SITE + u;
		}
		return u;
	}

	/** 試合ID: ?mid= を優先、無ければ古い形式 /match/&lt;試合ID&gt;/。どちらも無ければ null */
	static String extractMid(String url) {
		if (url == null || url.isEmpty()) {
			return null;
		}
		Matcher m = MID_PATTERN.matcher(url);
		if (m.find()) {
			return m.group(1);
		}
		Matcher legacy = LEGACY_MID_PATTERN.matcher(url);
		return legacy.find() ? legacy.group(1) : null;
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

	/**
	 * JSONファイルを作成する上で必要なデータ構築を行う
	 * @param items
	 * @param existingMatchKeys
	 * @return
	 */
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
			// 既にキュー(未処理の他ファイル)に同じmatchIdが存在する場合はスキップ（重複投入防止）
			if (existingMatchKeys.contains(trimmedMatchId)) {
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