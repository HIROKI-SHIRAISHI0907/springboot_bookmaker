package dev.web.api.bm_a011;

import lombok.Data;

@Data
public class IngestedRowDTO {

    public enum TableName {
        FUTURE_MASTER,
        DATA
    }

    private TableName table;

    /** 主キー（future_master.seq / data.seq） */
    private String seq;

    /** future_master 側サマリ */
    private FutureMasterIngestSummaryDTO future;

    /** data 側サマリ */
    private DataIngestSummaryDTO data;

    /** matchKey */
    private String matchKey;

    /** 同一試合群に future_master が存在するか */
    private Boolean futureExists;

    /** 同一試合群に「終了済」data が存在するか */
    private Boolean hasFinishedData;

    /**
     * 統計詳細データがそろっているか。matchKey（mid）で判定
     * 2026-11-18 変更: 必ずあるはずの 5 データ（試合情報・サマリー・フォーメーション・ラインアップ・選手スタッツ）が
     * すべてあれば true（以前は match_detail_match に 1 行あれば true）。
     * false の試合は「統計詳細データ取得」ボタンで取り直しの対象になる。
     */
    private Boolean hasDetailData;

    /** 統計詳細データ 9 データのうち、データがある数（2026-11-18 追加） */
    private Integer detailTableCount;

    /** 統計詳細データの全データ数（9） */
    private Integer detailTableTotal;

    /** 統計詳細データで、データが無いもの（日本語名。2026-11-18 追加） */
    private java.util.List<String> detailMissingTables;

    /** 統計詳細データで、このリーグでは提供されていないもの（日本語名。分母から外した。2026-11-22 追加） */
    private java.util.List<String> detailUnavailableTables;

    /**
     * 取れていないデータがあるが、取り直しの期間（試合から DetailAvailabilityService.RETRY_LAST_DAYS 日）を過ぎた
     * （＝取得終了。これ以上取らない。2026-11-22 追加）
     */
    private Boolean detailRetryExpired;

}
