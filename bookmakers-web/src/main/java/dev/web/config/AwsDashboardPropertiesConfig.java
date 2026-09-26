package dev.web.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * aws.dashboard.* の設定値（application.yml）
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "aws.dashboard")
public class AwsDashboardPropertiesConfig {

	/** AWS リージョン */
	private String region = "ap-northeast-1";

	/** 日付の区切りに使うタイムゾーン */
	private String zoneId = "Asia/Tokyo";

	/** 件数取得から除外するテーブル（"table" または "schema.table"） */
	private List<String> rdsExcludeTables = new ArrayList<String>();

	/** true: 小さいテーブルは COUNT(*) で正確に数える / false: 常に統計情報の推定値 */
	private boolean rdsExactCount = true;

	/** 推定件数がこの値以下のテーブルだけ COUNT(*) する（大きいテーブルは推定値） */
	private long rdsExactCountMaxRows = 1000000L;

	// ===================== 日次スナップショット（月次レポート用） =====================

	/** 日次集計を有効にするか */
	private boolean statsEnabled = true;

	/** 日次集計を保存する DB 名（アプリの DataSource の接続先 DB 名） */
	private String statsDatabase = "soccer_bm";

	/** 毎日の集計でさかのぼって欠けている日を埋める日数（ECS / Lambda） */
	private int statsBackfillDays = 7;

	/** キャッシュ秒数 */
	private long cacheTtlSeconds = 300;
}
