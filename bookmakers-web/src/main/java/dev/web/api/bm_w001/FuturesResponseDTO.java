package dev.web.api.bm_w001;

import lombok.Data;

/**
 * FuturesAPI(/api/{国}/{リーグ}/{チーム}/future)
 * @author shiraishitoshio
 *
 */
@Data
public class FuturesResponseDTO {

    /** ID */
    private String id;

    /** 通番 */
    private long seq;

    /** 国およびカテゴリ */
    private String gameTeamCategory;

    /** 試合予定時間 */
    private String futureTime;   // ISO string

    /** ホームチーム */
    private String homeTeam;

    /** アウェーチーム */
    private String awayTeam;

    /** リンク */
    private String link;

    /** ラウンドNo. */
    private Integer roundNo;

    /** 試合ステータス（画面表示用の最終判定結果。SCHEDULED/LIVE/FINISHED/DELAYED/POSTPONED/INTERRUPTED） */
    private String status;

    /** システムデータ(static_data)の件数に基づく内訳。鮮度(直近更新か)は問わない */
    private SystemDataStatus systemData;

    /** 現在のリアルタイムデータ(record_timeの鮮度)に基づく内訳 */
    private RealtimeDataStatus realtimeData;

    /**
     * システムデータ側の判定結果
     * ・鮮度(直近更新されているか)は問わず、「テーブルにその区分のデータが何件あるか」を表す
     */
    @Data
    public static class SystemDataStatus {

        /** 終了済データの件数 */
        private int finishedCount;

        /** ライブ扱い(終了済/ペナルティ以外)のデータ件数(鮮度不問) */
        private int liveCount;
    }

    /**
     * リアルタイムデータ側の判定結果
     * ・record_time が直近一定時間以内かどうかで「今まさに進行中か」を表す
     */
    @Data
    public static class RealtimeDataStatus {

        /** 直近一定時間以内(record_time基準)に更新された、今まさに進行中と言えるデータの件数 */
        private int currentLiveCount;

        /** 最終更新時刻（record_time由来。ISO文字列。データが無ければ null） */
        private String lastUpdatedAt;
    }
}