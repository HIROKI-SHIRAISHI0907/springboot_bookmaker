package dev.batch.bm_b012;

import lombok.Data;

/**
 * B016 試合詳細スクレイピングの対象試合（future_master の 1 行）
 * @author shiraishitoshio
 *
 */
@Data
public class GameDetailPathRow {

	/** 試合日（日本時間 yyyy-MM-dd） */
	private String matchDate;

	/** 試合開始時刻（日本時間 yyyy-MM-dd HH:mm:ss） */
	private String futureTime;

	/** 対戦チームカテゴリ */
	private String gameTeamCategory;

	/** ホームチーム */
	private String homeTeamName;

	/** アウェーチーム */
	private String awayTeamName;

	/** 試合詳細ページの URL */
	private String gameLink;
}
