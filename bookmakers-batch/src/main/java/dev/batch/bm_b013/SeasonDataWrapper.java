package dev.batch.bm_b013;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.batch.repository.master.CountryLeagueSeasonMasterBatchRepository;
import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.common.util.DateUtil;

/**
 * シーズンデータ更新Wrapper
 * @author shiraishitoshio
 *
 */
@Service
public class SeasonDataWrapper {

	private static final String PROJECT_NAME = AutoSeasonHyphenTransaction.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = AutoSeasonHyphenTransaction.class.getName();

	private static final String FILE_PREFIX = "b025_fin_season_data";
	private static final String SEASON_READFIN_MARKER = "(season=READFIN)";
	private static final String TEAM_READFIN_MARKER = "(team=READFIN)";

	/** シーズンバッチレポジトリ */
	@Autowired
	private CountryLeagueSeasonMasterBatchRepository countryLeagueSeasonMasterBatchRepository;

	/** シーズン終了日ハイフン更新ロジック */
	@Autowired
	private AutoSeasonHyphenTransaction autoSeasonHyphenTransaction;

	/** テーブル関係の削除 */
	@Autowired
	private EachTableTransaction eachTableTransaction;

	/** CSV関係の更新 */
	@Autowired
	private EachCsvTransaction eachCsvTransaction;

	/** バケット管理 */
	@Autowired
	private PathConfig config;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private S3Operator s3Operator;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 実行クラス
	 * @throws Exception
	 */
	public void execute() throws Exception {
		final String METHOD_NAME = "execute";
		// ログ出力
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// システム日時取得
		String sysDate = DateUtil.getSysDate();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		LocalDateTime now = LocalDateTime.parse(sysDate, formatter);

		TransactionDTO dto = new TransactionDTO();

		dto.setFormatter(formatter);
		dto.setNow(now);

		// シーズン終了日リストを保持
		List<CountryLeagueSeasonMasterEntity> list = countryLeagueSeasonMasterBatchRepository.findDateList();

		// シーズン終了日をシステム日時が超えているものをMap化
		// country-league -> endSeasonDate のMap
		Map<String, String> countryLeagueMap = list.stream()
				.filter(Objects::nonNull)
				.filter(entity -> entity.getCountry() != null)
				.filter(entity -> entity.getLeague() != null)
				.filter(entity -> isBeforeNow(entity.getEndSeasonDate(), formatter, now))
				.collect(Collectors.toMap(
						entity -> entity.getCountry() + "-" + entity.getLeague(),
						CountryLeagueSeasonMasterEntity::getEndSeasonDate,
						(oldValue, newValue) -> newValue,
						LinkedHashMap::new));

		if (countryLeagueMap.isEmpty()) {
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00001I_BATCH_EXECUTION_GREEN_FIN,
					"システム日時が超えているシーズンデータがありません。");
			return;
		}

		dto.setCountryLeagueMap(countryLeagueMap);

		// シーズン終了データをまとめたJSONデータをupload
		String seasonFinBucket = config.getS3BucketsOutputsNextSeason();
		final String fileName = FILE_PREFIX + ".json"; // b025_fin_season_data.json
		final String jsonFolder = config.getB008JsonFolder(); // /tmp/json/
		final Path jsonFilePath = Paths.get(jsonFolder, fileName);
		final String s3Key = fileName;

		Files.createDirectories(jsonFilePath.getParent());

		// 1) 既存S3上の b025_fin_season_data.json を取得
		Map<String, String> mergedMap = new LinkedHashMap<>();
		try {
		    List<String> existingKeys = s3Operator.listKeys(seasonFinBucket, s3Key);
		    boolean exists = existingKeys.stream().anyMatch(k -> k.equals(s3Key));

		    if (exists) {
		        String existingJson = s3Operator.downloadTextUtf8(seasonFinBucket, s3Key);

		        if (existingJson != null && !existingJson.isBlank()) {
		            @SuppressWarnings("unchecked")
		            Map<String, String> existingMap = objectMapper.readValue(
		                    existingJson, LinkedHashMap.class);

		            if (existingMap != null && !existingMap.isEmpty()) {
		                mergedMap.putAll(existingMap);
		                this.manageLoggerComponent.debugInfoLog(
		                        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
		                        MessageCdConst.MCD00001I_BATCH_EXECUTION_GREEN_FIN,
		                        "既存 b025_fin_season_data.json を取得: " + existingMap.size() + "件");
		            }
		        }
		    } else {
		        this.manageLoggerComponent.debugInfoLog(
		                PROJECT_NAME, CLASS_NAME, METHOD_NAME,
		                MessageCdConst.MCD00001I_BATCH_EXECUTION_GREEN_FIN,
		                "既存 b025_fin_season_data.json は S3 に存在しません。新規作成。");
		    }
		} catch (Exception e) {
		    this.manageLoggerComponent.debugErrorLog(
		            PROJECT_NAME, CLASS_NAME, METHOD_NAME,
		            MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
		            "既存 b025_fin_season_data.json の取得に失敗。マージをスキップ。");
		    mergedMap.clear();
		}

		// 2) 新規 countryLeagueMap をマージ
		int addedCount = 0;
		int preservedCount = 0;
		int updatedCount = 0;

		for (Map.Entry<String, String> entry : countryLeagueMap.entrySet()) {
		    String key = entry.getKey();
		    String newValue = entry.getValue();

		    if (mergedMap.containsKey(key)) {
		        String existingValue = mergedMap.get(key);

		        // 既存値が null/空 → 新値で置換
		        if (existingValue == null || existingValue.isBlank()) {
		            mergedMap.put(key, newValue);
		            addedCount++;
		            continue;
		        }

		        // 既存値に READFIN マーカーが付いている → 絶対に上書きしない
		        if (existingValue.contains(SEASON_READFIN_MARKER)
		                || existingValue.contains(TEAM_READFIN_MARKER)) {
		            preservedCount++;
		            continue;
		        }

		        // マーカー無しの場合、日付部分を比較
		        String existingDatePart = extractDatePart(existingValue);
		        String newDatePart = extractDatePart(newValue);

		        // 日付をパースして新旧比較
		        Instant existingInstant = parseFinSeasonDate(existingDatePart);
		        Instant newInstant = parseFinSeasonDate(newDatePart);

		        if (newInstant != null && existingInstant != null
		                && newInstant.isAfter(existingInstant)) {
		            // 新しい日付 → 上書き（来シーズン終了日で更新）
		            mergedMap.put(key, newValue);
		            updatedCount++;
		            this.manageLoggerComponent.debugInfoLog(
		                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
		                    MessageCdConst.MCD00001I_BATCH_EXECUTION_GREEN_FIN,
		                    "日付更新(新シーズン): " + key
		                            + " [" + existingDatePart + " -> " + newDatePart + "]");
		        } else if (newInstant == null || existingInstant == null) {
		            // どちらかがパース失敗
		            //  - 既存値がパース不能で新値が有効 → 上書き（復旧）
		            //  - 新値がパース不能 → 保持
		            if (existingInstant == null && newInstant != null) {
		                mergedMap.put(key, newValue);
		                updatedCount++;
		                this.manageLoggerComponent.debugInfoLog(
		                        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
		                        MessageCdConst.MCD00001I_BATCH_EXECUTION_GREEN_FIN,
		                        "日付更新(既存パース不能): " + key
		                                + " [" + existingDatePart + " -> " + newDatePart + "]");
		            } else {
		                preservedCount++;
		            }
		        } else {
		            // 新しい日付 <= 既存日付 → 保持（初回記録日時尊重）
		            preservedCount++;
		        }
		    } else {
		        // 新規キー追加
		        mergedMap.put(key, newValue);
		        addedCount++;
		    }
		}

		// 3) マージ結果をローカルへ出力
		objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFilePath.toFile(), mergedMap);

		// 4) S3へアップロード
		s3Operator.uploadFile(seasonFinBucket, s3Key, jsonFilePath);

		this.manageLoggerComponent.debugInfoLog(
		        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
		        MessageCdConst.MCD00001I_BATCH_EXECUTION_GREEN_FIN,
		        "b025_fin_season_data.json アップロード完了: 合計 " + mergedMap.size()
		                + " 件 (新規: " + addedCount + " / 更新: " + updatedCount
		                + " / 保持: " + preservedCount + ")");

		// country-league の一覧を DTO に保持
		List<String> countryLeagueList = list.stream()
				.filter(Objects::nonNull)
				.filter(entity -> entity.getCountry() != null)
				.filter(entity -> entity.getLeague() != null)
				.filter(entity -> entity.getEndSeasonDate() != null)
				.filter(entity -> isBeforeNow(entity.getEndSeasonDate(), formatter, now))
				.map(entity -> entity.getCountry() + "-" + entity.getLeague())
				.collect(Collectors.toList());

		dto.setCountryLeague(countryLeagueList);

		try {
			this.autoSeasonHyphenTransaction.execute(dto);
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
					"autoSeasonHyphenTransaction");
			throw e;
		}

		try {
			this.eachCsvTransaction.execute(dto);
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
					"eachCsvTransaction");
			throw e;
		}

		try {
			this.eachTableTransaction.execute(dto);
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
					"eachTableTransaction");
			throw e;
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
	}

	/**
	 * endSeasonDate を過ぎてから3日経過しているか
	 * @param endSeasonDate
	 * @param formatter
	 * @param now
	 * @return
	 */
	private boolean isBeforeNow(String endSeasonDate, DateTimeFormatter formatter, LocalDateTime now) {
		if (endSeasonDate == null || endSeasonDate.length() < 19) {
			return false;
		}
		String normalizedEndSeasonDate = endSeasonDate.substring(0, 19);
		LocalDateTime endDateTime = LocalDateTime.parse(normalizedEndSeasonDate, formatter);

		// endSeasonDate から3日後
		LocalDateTime threeDaysAfterEnd = endDateTime.plusDays(3);

		// 「3日経過しているものだけ」を取りたいので、now がその3日後を超えているかで判定
		return !now.isBefore(threeDaysAfterEnd);
	}

	/**
	 * b025値から日付部分だけを抽出する
	 * 例:
	 *   "2026-07-21 12:00:00+00" -> "2026-07-21 12:00:00+00"
	 *   "2026-07-21 12:00:00+00 (season=READFIN)" -> "2026-07-21 12:00:00+00"
	 *   "2026-07-21 12:00:00+00 (season=READFIN) (team=READFIN)" -> "2026-07-21 12:00:00+00"
	 *
	 * @param value b025 JSONの値
	 * @return 日付部分（マーカー除去後、トリム済み）
	 */
	private String extractDatePart(String value) {
	    if (value == null || value.isBlank()) {
	        return null;
	    }
	    return value
	            .replace(SEASON_READFIN_MARKER, "")
	            .replace(TEAM_READFIN_MARKER, "")
	            .trim();
	}

	/**
	 * b025値の日付部分をInstantにパースする
	 * 対応フォーマット例:
	 *   "2026-07-21 12:00:00+00"
	 *   "2026-07-21 12:00:00+0000"
	 *   "2026-07-21 12:00:00+00:00"
	 *
	 * @param dateStr 日付文字列(マーカー除去済み推奨)
	 * @return パース成功: Instant / 失敗: null
	 */
	private Instant parseFinSeasonDate(String dateStr) {
		final String METHOD_NAME = "parseFinSeasonDate";
	    if (dateStr == null || dateStr.isBlank()) {
	        return null;
	    }
	    String s = dateStr.trim();

	    // マーカーが残っていても除去
	    s = s.replace(SEASON_READFIN_MARKER, "")
	         .replace(TEAM_READFIN_MARKER, "")
	         .trim();

	    // "+00" -> "+00:00" に正規化（ISO_OFFSET_DATE_TIMEはコロン必須）
	    // "yyyy-MM-dd HH:mm:ss+00" のような表記に対応
	    // まずスペースをTに置換
	    String normalized = s.replaceFirst(" ", "T");

	    // オフセット末尾が "+00" や "+0900" の場合に "+00:00" 形式へ
	    normalized = normalized.replaceAll("([+-]\\d{2})$", "$1:00");
	    normalized = normalized.replaceAll("([+-]\\d{2})(\\d{2})$", "$1:$2");

	    try {
	        return OffsetDateTime.parse(normalized).toInstant();
	    } catch (DateTimeParseException e1) {
	        // フォールバック: よくあるパターンを個別に試す
	        List<DateTimeFormatter> formatters = List.of(
	                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX"),
	                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX"),
	                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"),
	                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")
	        );
	        for (DateTimeFormatter f : formatters) {
	            try {
	                return OffsetDateTime.parse(s, f).toInstant();
	            } catch (DateTimeParseException ignore) {
	                // continue
	            }
	        }
	        this.manageLoggerComponent.debugInfoLog(
	                PROJECT_NAME, CLASS_NAME, METHOD_NAME,
	                MessageCdConst.MCD00001I_BATCH_EXECUTION_GREEN_FIN,
	                "日付パース失敗: " + dateStr);
	        return null;
	    }
	}

}
