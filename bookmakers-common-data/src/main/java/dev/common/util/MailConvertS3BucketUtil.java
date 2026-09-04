package dev.common.util;

import java.util.List;
import java.util.Map;

import dev.common.constant.MailIdConstant;
import dev.common.constant.S3BucketConstant;
import dev.common.enums.BatchCodeToMailEnum;
import dev.common.enums.ScrapeCodeToMailEnum;

/**
 * メールID→バケット名変換Utilクラス
 */
public class MailConvertS3BucketUtil {

	/** メールIDバケットマップ */
	private static final Map<String, List<String>> MAIL_CONVERT_BUCKET_MAP = Map.of(
			MailIdConstant.BM_MAIL_001, List.of(
					S3BucketConstant.S3_PASSWORD_RESET),
			MailIdConstant.BM_MAIL_002, List.of(
					BatchCodeToMailEnum.B002.getBatchCode(),
					BatchCodeToMailEnum.B003.getBatchCode(),
					BatchCodeToMailEnum.B004.getBatchCode(),
					BatchCodeToMailEnum.B005.getBatchCode(),
					BatchCodeToMailEnum.B007.getBatchCode(),
					BatchCodeToMailEnum.B010.getBatchCode()),
			MailIdConstant.BM_MAIL_003, List.of(
					ScrapeCodeToMailEnum.S002.getScrapeCode(),
					ScrapeCodeToMailEnum.S003.getScrapeCode(),
					ScrapeCodeToMailEnum.S004.getScrapeCode(),
					ScrapeCodeToMailEnum.S005.getScrapeCode(),
					ScrapeCodeToMailEnum.S008.getScrapeCode(),
					ScrapeCodeToMailEnum.S010.getScrapeCode()),
			MailIdConstant.BM_MAIL_004, List.of(
					ScrapeCodeToMailEnum.S009.getScrapeCode()),
			MailIdConstant.BM_MAIL_005, List.of(
					ScrapeCodeToMailEnum.S009.getScrapeCode()),
			MailIdConstant.BM_MAIL_006, List.of(
					S3BucketConstant.S3_NEXT_SEASON_INFO));

	/** バッチコードマップ */
	private static final Map<String, String> BATCH_CONVERT_MAP = Map.of(
			BatchCodeToMailEnum.B002.getBatchCode(), S3BucketConstant.S3_TEAM_MEMBER,
			BatchCodeToMailEnum.B003.getBatchCode(), S3BucketConstant.S3_SEASON,
			BatchCodeToMailEnum.B004.getBatchCode(), S3BucketConstant.S3_TEAM,
			BatchCodeToMailEnum.B005.getBatchCode(), S3BucketConstant.S3_FUTURE,
			BatchCodeToMailEnum.B007.getBatchCode(), S3BucketConstant.S3_ALL_LEAGUE,
			BatchCodeToMailEnum.B010.getBatchCode(), S3BucketConstant.S3_OUTPUT_FIN,
			BatchCodeToMailEnum.B011.getBatchCode(), S3BucketConstant.S3_STAT,
			BatchCodeToMailEnum.B012.getBatchCode(), S3BucketConstant.S3_OUTPUT_FIN,
			BatchCodeToMailEnum.B013.getBatchCode(), S3BucketConstant.S3_DELETE_INFO,
			BatchCodeToMailEnum.B014.getBatchCode(), S3BucketConstant.S3_GEOGRAFIC);

	/** スクレイピングコードマップ */
	private static final Map<String, String> SCRAPE_CONVERT_MAP = Map.of(
			ScrapeCodeToMailEnum.S002.getScrapeCode(), S3BucketConstant.S3_TEAM_MEMBER,
			ScrapeCodeToMailEnum.S003.getScrapeCode(), S3BucketConstant.S3_SEASON,
			ScrapeCodeToMailEnum.S004.getScrapeCode(), S3BucketConstant.S3_TEAM,
			ScrapeCodeToMailEnum.S005.getScrapeCode(), S3BucketConstant.S3_FUTURE,
			ScrapeCodeToMailEnum.S008.getScrapeCode(), S3BucketConstant.S3_OUTPUT,
			ScrapeCodeToMailEnum.S009.getScrapeCode(), S3BucketConstant.S3_NO_ECS,
			ScrapeCodeToMailEnum.S010.getScrapeCode(), S3BucketConstant.S3_OUTPUT_FIN,
			ScrapeCodeToMailEnum.S015.getScrapeCode(), S3BucketConstant.S3_GEOGRAFIC);

	/** コンストラクタ生成禁止 */
	private MailConvertS3BucketUtil() {
	}

	/**
	 * メールIDからバッチコード、スクレイピングコードを取得
	 * @param mailId
	 * @return
	 */
	public static List<String> getMailConvertBatchScrapeList(String mailId) {
		return (MAIL_CONVERT_BUCKET_MAP.containsKey(mailId)) ? MAIL_CONVERT_BUCKET_MAP.get(mailId) : null;
	}

	/**
	 * バッチコードからS3バケットリストを取得
	 * @param batchCd
	 * @return
	 */
	public static String getBatchConvertList(String batchCd) {
		return (BATCH_CONVERT_MAP.containsKey(batchCd)) ? BATCH_CONVERT_MAP.get(batchCd) : null;
	}

	/**
	 * スクレイピングコードからS3バケットリストを取得
	 * @param scrapeCd
	 * @return
	 */
	public static String getScrapeConvertList(String scrapeCd) {
		return (SCRAPE_CONVERT_MAP.containsKey(scrapeCd)) ? SCRAPE_CONVERT_MAP.get(scrapeCd) : null;
	}

	/**
	 * メールIDからS3バケットを1つ取得
	 * @param mailId メールID
	 * @param batchScrapeCdParam どのバケットに格納するかを決めるバッチ<br>
	 * もしくはスクレイピングコード(nullの場合は対象のバケットに全て格納する)<br>
	 * MailIdConstant.BM_MAIL_001, MailIdConstant.BM_MAIL_006の場合はbatchScrapeCdParam==nullでもいい
	 * @return
	 */
	public static String getS3Bucket(String mailId, String batchScrapeCdParam) {
		if (!MailIdConstant.BM_MAIL_001.equals(mailId) &&
				!MailIdConstant.BM_MAIL_006.equals(mailId)) {
			// バッチコード・スクレイピングコードが指定されている場合は、
			// 指定されたコードからS3バケットを取得
			if (batchScrapeCdParam != null) {
				return (batchScrapeCdParam.startsWith("B"))
						? getBatchConvertList(batchScrapeCdParam)
						: getScrapeConvertList(batchScrapeCdParam);
			}
		}

		// メールIDに紐づくコード・バケットを取得
		List<String> batchScrapeCdList = getMailConvertBatchScrapeList(mailId);

		if (batchScrapeCdList == null || batchScrapeCdList.isEmpty()) {
			return null;
		}

		// 既にS3バケット名が設定されている場合
		if (batchScrapeCdList.size() == 1
				&& batchScrapeCdList.get(0).startsWith("aws-s3")) {
			return batchScrapeCdList.get(0);
		}

		// バッチコード・スクレイピングコードからS3バケットを取得
		for (String batchScrapeCd : batchScrapeCdList) {

			String batchBucket = getBatchConvertList(batchScrapeCd);
			if (batchBucket != null) {
				return batchBucket;
			}

			String scrapeBucket = getScrapeConvertList(batchScrapeCd);
			if (scrapeBucket != null) {
				return scrapeBucket;
			}
		}

		return null;
	}

}
