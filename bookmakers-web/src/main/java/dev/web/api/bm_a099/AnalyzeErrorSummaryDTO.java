package dev.web.api.bm_a099;

import lombok.Data;

/**
 * BM × エラー種別 × 国・リーグ × 原因の項目ごとの件数（ビュー analyze_error_summary の1行）
 * @author shiraishitoshio
 *
 */
@Data
public class AnalyzeErrorSummaryDTO {

	private String bmNumber;

	private String errorType;

	/** エラー種別の表示名 */
	private String errorTypeLabel;

	private String country;

	private String league;

	private String errorField;

	/** 登録できなかった試合（行）数 */
	private Integer matchCount;

	/** うち未対応 */
	private Integer unresolvedCount;

	/** 発生回数の合計 */
	private Integer occurredTotal;

	private String firstOccurredAt;

	private String lastOccurredAt;

	/** 最新のエラー内容 */
	private String latestMessage;
}
