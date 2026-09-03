package dev.web.api.bm_a027;

import lombok.Data;

/**
 * ファイルをダウンロードするリクエスト
 * @author shiraishitoshio
 *
 */
@Data
public class UploadedRealTimeDataDownloadRequest {

	/**
     * ファイル名
     */
    private String fileName;

}
