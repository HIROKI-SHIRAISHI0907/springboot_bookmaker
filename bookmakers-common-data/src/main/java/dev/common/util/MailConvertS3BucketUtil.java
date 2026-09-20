package dev.common.util;

/**
 * メールID／通知ID(UUID) → 格納用JSONファイル名変換Utilクラス
 */
public class MailConvertS3BucketUtil {

    /** JSONファイルの拡張子 */
    private static final String JSON_EXTENSION = ".json";

    /** コンストラクタ生成禁止 */
    private MailConvertS3BucketUtil() {
    }

    /**
     * mailId もしくは noticeId(承認フロー等で発行されるUUID) から、
     * S3格納用のJSONファイル名を取得する
     * @param id mailId または noticeId
     * @return "&lt;id&gt;.json"
     */
    public static String getJsonFileName(String id) {
        return id + JSON_EXTENSION;
    }

}