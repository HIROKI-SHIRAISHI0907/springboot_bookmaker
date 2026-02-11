package dev.web.api.bm_a007;

public enum S3PrefixScope {
    /** 設定ファイルのprefixを使う */
    DEFAULT,
    /** バケット直下（prefix=null） */
    ROOT,
    /** 設定prefixの1つ上（例: a/b/ -> a/、json/ -> null） */
    PARENT,
    /** prefixOverride を使う */
    CUSTOM
}
