package dev.batch.bm_b014;

import lombok.Data;

/**
 * 直近のホームゲームのスタジアム（static_data の カテゴリ・ホームチーム・スタジアムごとの試合数と最終日時。2026-11 追加）
 * @author shiraishitoshio
 */
@Data
public class TeamRecentStadiumDTO {

	/** カテゴリ（国: リーグ - ラウンド N） */
	private String dataCategory;

	/** ホームチーム */
	private String homeTeamName;

	/** スタジアム（static_data の表記のまま。都市名が括弧で付くことがある） */
	private String studium;

	/** 試合数（match_id の数） */
	private int matchCount;

	/** 一番新しい記録日時 "yyyy-MM-dd HH:mm:ss" */
	private String lastTime;
}
