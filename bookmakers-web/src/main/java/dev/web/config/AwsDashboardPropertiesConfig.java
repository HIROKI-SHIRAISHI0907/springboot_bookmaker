package dev.web.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * aws.dashboard.* の設定値
 */
@Data
@ConfigurationProperties(prefix = "aws.dashboard")
public class AwsDashboardPropertiesConfig {

	/** AWS リージョン */
	private String region = "ap-northeast-1";

	/** 日付の区切りに使うタイムゾーン */
	private String zoneId = "Asia/Tokyo";

	/** RDS テーブル件数取得対象のスキーマ（空なら接続のデフォルト） */
	private String rdsSchema = "";

	/** 件数取得から除外するテーブル */
	private List<String> rdsExcludeTables = new ArrayList<String>();

	/** true: COUNT(*) / false: 統計情報からの推定値 */
	private boolean rdsExactCount = true;

	/** キャッシュ秒数 */
	private long cacheTtlSeconds = 300;
}
