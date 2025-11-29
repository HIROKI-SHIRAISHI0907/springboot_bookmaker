package dev.web.api.bm_w005;

import lombok.Data;

/**
 * GameDetailAPI（試合詳細）
 * /api/{country}/{league}/{team}/games/detail/{seq}
 *
 * フロント側 GameDetail 型に対応。
 *
 * @author shiraishitoshio
 */
@Data
public class GameDetailDTO {

    /** 大会名（data_category） */
    private String competition;

    /** ラウンド番号 */
    private Integer roundNo;

    /** 記録日時（JST, "YYYY-MM-DDTHH:mm:ss"） */
    private String recordedAt;

    /**
     * 勝者:
     *   - "LIVE"   : 進行中
     *   - "HOME"   : ホーム勝利
     *   - "AWAY"   : アウェー勝利
     *   - "DRAW"   : 引き分け
     */
    private String winner;

    /** 外部詳細へのリンク（judge 由来） */
    private String link;

    /** 生 times 文字列（例: "68:09" / "45+2'" / "終了済" など） */
    private String times;

    /** ホーム側情報 */
    private TeamSide home;

    /** アウェー側情報 */
    private TeamSide away;

    /** 会場情報 */
    private Venue venue;

    /**
     * チーム別のスタッツ情報
     */
    @Data
    public static class TeamSide {

        /** チーム名 */
        private String name;

        /** 得点 */
        private int score;

        /** 監督名 */
        private String manager;

        /** フォーメーション */
        private String formation;

        /** 期待得点（xG） */
        private Double xg;

        /** ポゼッション（%） */
        private Integer possession;

        /** シュート合計 */
        private Integer shots;

        /** 枠内シュート */
        private Integer shotsOn;

        /** 枠外シュート */
        private Integer shotsOff;

        /** ブロック数 */
        private Integer blocks;

        /** コーナーキック数 */
        private Integer corners;

        /** ビッグチャンス数 */
        private Integer bigChances;

        /** セーブ数 */
        private Integer saves;

        /** イエローカード枚数 */
        private Integer yc;

        /** レッドカード枚数 */
        private Integer rc;

        /** パス数（"xxx (yyy%)" 等の文字列） */
        private String passes;

        /** ロングパス数 */
        private String longPasses;
    }

    /**
     * 会場情報
     */
    @Data
    public static class Venue {

        /** スタジアム名 */
        private String stadium;

        /** 観客数 */
        private String audience;

        /** 収容人数 */
        private String capacity;
    }
}
