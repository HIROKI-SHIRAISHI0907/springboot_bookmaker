package dev.batch.bm_b095;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.common.util.DateOffsetDecisionUtil;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * 不要ファイル削除サービスクラス
 * @author shiraishitoshio
 *
 */
@Component
public class DeleteFileService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = DeleteFileService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = DeleteFileService.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "DELETE_DATA";

	/** メール制御JSON：「通知済」フラグ（PutMailNoticeJson と同じ値） */
	private static final String NOTIFIED_FLAG = "通知済";

	/** メール制御JSON：「送信失敗」フラグ（PutMailNoticeJson と同じ値。3回失敗し以降送らないもの） */
	private static final String FAILED_FLAG = "送信失敗";

	/** メール制御JSON：処理キーがこの件数以上溜まっていたら削除候補にする */
	private static final int MAIL_JSON_MIN_KEYS = 50;

	/** メール制御JSON：件数が少なくても、この日数以上更新が無ければ溜まったとみなす */
	private static final long MAIL_JSON_STALE_DAYS = 30;

	/** メール制御JSON：直近この時間内に更新されたものは書き込み中の可能性があるので触らない */
	private static final long MAIL_JSON_QUIET_HOURS = 1;

	/** メール制御JSON：バケット直下の XXX.json（フォルダ配下は対象外） */
	private static final Pattern MAIL_JSON_PATTERN = Pattern.compile("^[^/]+\\.json$");

	/** 延期ファイル：バケット直下の delay_postpone_YYYY-MM-DD(_N).json（PostponedRescheduleService と同じ） */
	private static final Pattern DELAY_KEY_PATTERN = Pattern
			.compile("^delay_postpone_(\\d{4}-\\d{2}-\\d{2})(?:_(\\d+))?\\.json$");

	/** 延期ファイル：この月数以上経過していたら削除 */
	private static final long DELAY_KEEP_MONTHS = 3;

	/** S3 DeleteObjects の1回あたり上限 */
	private static final int DELETE_CHUNK = 1000;

	@Autowired
	private S3Operator s3Operator;

	@Autowired
	private PathConfig config;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/** JSON変換用 */
	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * 不要ファイル削除処理を行う。以下のデータを削除する
	 * <p>
	 * 1. メール送信バッチECS制御用に格納したJSONファイル(aws-s3-mail) ある程度溜まっており通知済が全て入っていたら削除
	 * 2. aws-s3-delay-postpone-csvにある延期ファイルが3ヶ月以上経過していたら削除
	 * </p>
	 */
	public void execute() {
		final String METHOD_NAME = "execute";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 1. メール送信バッチECS制御用に格納したJSONファイル(aws-s3-mail) 削除
		deleteNotifiedMailJson();

		// 2. aws-s3-delay-postpone-csv の3ヶ月以上経過した延期ファイル 削除
		deleteOldDelayPostponeFiles();

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * aws-s3-mail 直下のメール制御JSON(XXX.json)のうち、以下を全て満たすものを削除する。
	 * <ul>
	 *   <li>処理キーが1件以上あり、値が全て「通知済」または「送信失敗」（未通知 "" が1件も無い）</li>
	 *   <li>処理キーが {@value #MAIL_JSON_MIN_KEYS} 件以上溜まっている、
	 *       または最終更新から {@value #MAIL_JSON_STALE_DAYS} 日以上経過している</li>
	 *   <li>最終更新から {@value #MAIL_JSON_QUIET_HOURS} 時間以上経過している（書き込み中のものは触らない）</li>
	 * </ul>
	 * 読めない・JSONとして壊れているファイルは消さずにスキップする。
	 * 削除後に同じIDで送信があっても、PutMailNoticeJson が空Mapから作り直すので問題ない。
	 */
	private void deleteNotifiedMailJson() {
		final String METHOD_NAME = "deleteNotifiedMailJson";
		String bucket = config.getS3BucketsMail();
		Instant now = Instant.now();
		List<String> targets = new ArrayList<>();
		int scanned = 0;
		int skipped = 0;
		try {
			for (S3Object obj : s3Operator.listObjectsBySuffix(bucket, ".json")) {
				String key = obj.key();
				if (!MAIL_JSON_PATTERN.matcher(key).matches()) {
					continue;
				}
				scanned++;
				Instant lastModified = obj.lastModified();
				if (lastModified == null
						|| Duration.between(lastModified, now).toHours() < MAIL_JSON_QUIET_HOURS) {
					skipped++;
					continue;
				}
				Map<String, String> noticeMap = readNoticeMap(bucket, key);
				if (noticeMap == null || noticeMap.isEmpty() || !isAllFinished(noticeMap)) {
					skipped++;
					continue;
				}
				boolean enoughKeys = noticeMap.size() >= MAIL_JSON_MIN_KEYS;
				boolean stale = Duration.between(lastModified, now).toDays() >= MAIL_JSON_STALE_DAYS;
				if (enoughKeys || stale) {
					targets.add(key);
				} else {
					skipped++;
				}
			}
			int deleted = deleteInChunks(bucket, targets);
			this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					"メール制御JSON削除 bucket=" + bucket + ", 対象JSON=" + scanned
							+ ", 削除件数=" + deleted + ", 残した件数=" + skipped);
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG, e,
					"メール制御JSON削除でエラー bucket=" + bucket + ": " + e.getMessage());
		}
	}

	/**
	 * aws-s3-delay-postpone-csv 直下の delay_postpone_YYYY-MM-DD(_N).json のうち、
	 * ファイル名の日付が今日（日本時間）から {@value #DELAY_KEEP_MONTHS} ヶ月以上前のものを削除する。
	 * json/ 配下（B009 が出す b001 など）やパターンに合わないキーは対象外。
	 */
	private void deleteOldDelayPostponeFiles() {
		final String METHOD_NAME = "deleteOldDelayPostponeFiles";
		String bucket = config.getS3BucketsOutputsDelayPostpone();
		LocalDate cutoff = LocalDate.now(DateOffsetDecisionUtil.getZoneId()).minusMonths(DELAY_KEEP_MONTHS);
		List<String> targets = new ArrayList<>();
		int scanned = 0;
		try {
			for (String key : s3Operator.listKeys(bucket, "delay_postpone_")) {
				Matcher m = DELAY_KEY_PATTERN.matcher(key);
				if (!m.matches()) {
					continue;
				}
				scanned++;
				LocalDate fileDate;
				try {
					fileDate = LocalDate.parse(m.group(1));
				} catch (DateTimeParseException e) {
					continue;
				}
				// 3ヶ月以上経過 = ファイル日付が cutoff 以前
				if (!fileDate.isAfter(cutoff)) {
					targets.add(key);
				}
			}
			int deleted = deleteInChunks(bucket, targets);
			this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					"延期ファイル削除 bucket=" + bucket + ", 基準日=" + cutoff + " 以前, 対象ファイル=" + scanned
							+ ", 削除件数=" + deleted);
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG, e,
					"延期ファイル削除でエラー bucket=" + bucket + ": " + e.getMessage());
		}
	}

	/**
	 * メール制御JSONを読み込む。読めない・壊れている場合は null（削除しない）。
	 */
	private Map<String, String> readNoticeMap(String bucket, String key) {
		final String METHOD_NAME = "readNoticeMap";
		try {
			String json = s3Operator.downloadTextUtf8(bucket, key);
			if (json == null || json.isBlank()) {
				return null;
			}
			return objectMapper.readValue(json,
					objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
		} catch (Exception e) {
			this.manageLoggerComponent.debugWarnLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099I_LOG,
					"メール制御JSONが読めないためスキップ key=" + key + ": " + e.getMessage());
			return null;
		}
	}

	/**
	 * 全ての処理キーが「通知済」または「送信失敗」か（未通知が残っていないか）
	 */
	private boolean isAllFinished(Map<String, String> noticeMap) {
		for (String flag : noticeMap.values()) {
			if (!NOTIFIED_FLAG.equals(flag) && !FAILED_FLAG.equals(flag)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 1000件ずつ一括削除する。
	 * @return 削除件数
	 */
	private int deleteInChunks(String bucket, List<String> keys) {
		for (int i = 0; i < keys.size(); i += DELETE_CHUNK) {
			s3Operator.deleteObjects(bucket, keys.subList(i, Math.min(i + DELETE_CHUNK, keys.size())));
		}
		return keys.size();
	}

}
