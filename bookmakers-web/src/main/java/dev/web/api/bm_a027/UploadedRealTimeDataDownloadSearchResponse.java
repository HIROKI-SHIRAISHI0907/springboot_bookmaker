package dev.web.api.bm_a027;

import java.time.LocalDate;

import lombok.Data;

@Data
public class UploadedRealTimeDataDownloadSearchResponse {

	/** ファイル名(matchId名) */
	private String fileName;

	/** 対戦情報 */
	private String gameTeamName;

	/** 対戦中か？(アイコンで設定) */
	private String gameProcess;

	/** サイズ */
	private String size;

	/** 最終更新日時 */
	private LocalDate lastUpdateDate;

}
