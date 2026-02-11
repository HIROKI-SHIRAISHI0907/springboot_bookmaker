package dev.web.api.bm_a007;

import lombok.Data;

@Data
public class S3FileListRequest {

	/** バッチコード */
    private String batchCode;

    private String prefixOverride;

    /** 返す最大件数（デフォルト200、上限もサービス側で縛る） */
    private Integer limit = 200;

    /** recursive=trueなら配下全部。falseならdelimiter="/"で「直下」相当 */
    private Boolean recursiveOverride;

    /**
     * DEFAULT: 設定ファイルのprefixを使う
     * ROOT: prefix=null (バケット直下)
     * PARENT: 設定prefixの1つ上（例: json/ -> null）
     * CUSTOM: prefixOverride を使う
     */
    private S3PrefixScope scope = S3PrefixScope.DEFAULT;
}
