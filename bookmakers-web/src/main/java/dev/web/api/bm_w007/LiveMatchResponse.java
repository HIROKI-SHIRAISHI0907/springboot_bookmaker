package dev.web.api.bm_w007;

import lombok.Data;

/**
 * LiveMatchesAPI（現在開催中の試合）
 * フロント側の LiveMatch 型と対応
 *
 *   GET /api/live-matches
 *     ?country=国名&league=リーグ名 （任意）
 *
 * @author shiraishitoshio
 */
@Data
public class LiveMatchResponse {

    /** seq（public.data.seq） */
    private long seq;

    /** データカテゴリ（例: "日本: J1 リーグ - ラウンド 12"） */
    private String dataCategory;

    /** 試合時間（"68:09" / "45+2'" / "ハーフタイム" 等） */
    private String times;

    /** ホームチーム名 */
    private String homeTeamName;

    /** アウェーチーム名 */
    private String awayTeamName;

    /** ホームスコア（整数に正規化済み） */
    private Integer homeScore;

    /** アウェースコア（整数に正規化済み） */
    private Integer awayScore;

    /** ホーム xG（小数） */
    private Double homeExp;

    /** アウェー xG（小数） */
    private Double awayExp;

    /** ホーム枠内シュート数 */
    private Integer homeShootIn;

    /** アウェー枠内シュート数 */
    private Integer awayShootIn;

    /**
     * レコード時刻（record_time または update_time のどちらか）
     * ISO 文字列想定
     */
    private String recordTime;

    /**
     * 外部リンク相当（goal_time に URL が入るケースを想定）
     * ない場合は null
     */
    private String link;

    /** ホーム側チームの英語スラッグ（/team/<slug>/ の <slug> 部分） */
    private String homeSlug;

    /** アウェー側チームの英語スラッグ（/team/<slug>/ の <slug> 部分） */
    private String awaySlug;
}
