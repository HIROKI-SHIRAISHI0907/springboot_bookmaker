package dev.web.api.bm_a027;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * UploadedRealTimeDataDownloadSearchCondition
 * @author shiraishitoshio
 *
 */
@Data
public class UploadedRealTimeDataDownloadSearchCondition {

	/**
     * アップロードされた日付で参照する場合の日付
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate uploadDate;

    /**
     * 対戦情報
     */
	private String country;

	/**
     * 対戦情報
     */
	private String league;

    /**
     * 終了済まで格納されているzipかどうかで検索する場合のフラグ
     */
    private boolean finFlg;

}
