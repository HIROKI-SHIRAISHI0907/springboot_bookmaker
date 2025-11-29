package dev.web.api.bm_w002;

import lombok.Data;

/**
 * HistoriesDetailAPI(/api/{国}/{リーグ}/{チーム}/history/{通番})
 * @author shiraishitoshio
 *
 */
@Data
public class HistoryDetailResponseDTO {

	/** 競争 */
    private String competition;

    /** ラウンドNo. */
    private Integer roundNo;

    /** 記録時間 */
    private String recordedAt;

    /** 勝敗 */
    private String winner;  // "HOME" | "AWAY" | "DRAW"

    /** リンク */
    private String link;

    /** ホームスタッツ群 */
    private TeamSide home;

    /** アウェースタッツ群 */
    private TeamSide away;

    /** 開催データ群 */
    private Venue venue;

    /**
     * スタッツ関係データ
     * @author shiraishitoshio
     *
     */
    @Data
    public static class TeamSide {

    	/** チーム名 */
        private String name;

        /** スコア */
        private int score;

        /** 監督 */
        private String manager;

        /** フォーメーション */
        private String formation;

        /** 期待値 */
        private Double xg;

        /** ポゼッション */
        private Integer possession;   // %

        /** シュート */
        private Integer shots;

        /** 枠内シュート */
        private Integer shotsOn;

        /** 枠外シュート */
        private Integer shotsOff;

        /** ブロック */
        private Integer blocks;

        /** コーナー */
        private Integer corners;

        /** ビッグチャンス */
        private Integer bigChances;

        /** セーブ */
        private Integer saves;

        /** イエローカード */
        private Integer yc;

        /** レッドカード */
        private Integer rc;

        /** パス */
        private String passes;

        /** ロングパス */
        private String longPasses;
    }

    /**
     * 開催関係データ
     * @author shiraishitoshio
     *
     */
    @Data
    public static class Venue {

    	/** スタジアム */
        private String stadium;

        /** 観客 */
        private String audience;

        /** 収容人数 */
        private String capacity;
    }
}
