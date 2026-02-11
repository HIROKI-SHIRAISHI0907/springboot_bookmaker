package dev.web.api.bm_a004;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import lombok.Data;

@Data
public class S3FileCountRequest {

	/** バッチコード */
    private String batchCode;

    /** dayを省略したら today(JST) を使う */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate day;

    /**
     * 起点prefixを上書きしたい時に指定。
     * null を指定したら「バケット直下」を意味する（prefixなし）。
     * 未指定(null)と区別したいなら Optional/フラグで管理する（後述）。
     */
    private String prefixOverride;

    /**
     * DEFAULT: 設定ファイルのprefixを使う
     * ROOT: prefix=null (バケット直下)
     * PARENT: 設定prefixの1つ上（例: json/ -> null）
     * CUSTOM: prefixOverride を使う
     */
    private S3PrefixScope scope = S3PrefixScope.DEFAULT;
}
