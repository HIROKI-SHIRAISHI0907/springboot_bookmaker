package dev.web.api.upcomingDetail.upcomingDetailDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 試合予定の詳細に出すチームのアイコン 1 つ（2026-11-27 追加）
 * @author shiraishitoshio
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamBadgeDTO {

	/** 種類のコード（例: WIN_STREAK・POSSESSION_TOP） */
	private String code;

	/** 表示する文言（例: 4連勝中・ボール保持率リーグ1位） */
	private String label;

	/** GOOD（好材料）/ BAD（不安材料）/ INFO（特徴） */
	private String tone;

	/** 分類（流れ・前節・今季・リーグ順位・スタイル） */
	private String category;

	/** 根拠（ツールチップ用。例: 第8〜11節 4試合連続で勝利） */
	private String detail;
}
