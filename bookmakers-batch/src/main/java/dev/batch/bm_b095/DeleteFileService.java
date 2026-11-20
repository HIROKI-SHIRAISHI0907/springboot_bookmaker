package dev.batch.bm_b095;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.common.util.DateOffsetDecisionUtil;

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

	/**
	 * 不要ファイル削除処理を行う。以下のデータを削除する
	 * <p>
	 * 1. aws-s3-delay-postpone-csvにある延期ファイルが3ヶ月以上経過していたら削除
	 * </p>
	 */
	public void execute() {
		final String METHOD_NAME = "execute";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 1. aws-s3-delay-postpone-csv の3ヶ月以上経過した延期ファイル 削除
		deleteOldDelayPostponeFiles();

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
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
