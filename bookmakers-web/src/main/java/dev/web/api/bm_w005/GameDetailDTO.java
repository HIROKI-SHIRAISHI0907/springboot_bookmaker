package dev.web.api.bm_w005;

import lombok.Data;

/**
 * GameDetailAPI（試合詳細）
 * POST /api/games/detail
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

    /** 生 times 文字列 */
    private String times;

    /** ホーム側情報 */
    private TeamSide home;

    /** アウェー側情報 */
    private TeamSide away;

    /** 会場情報 */
    private Venue venue;

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

        /** xG */
        private Double xg;

        /** 枠内xG */
        private Double inGoalXg;

        /** ポゼッション（%） */
        private Integer possession;

        /** 総シュート */
        private Integer shots;

        /** 枠内シュート */
        private Integer shotsOn;

        /** 枠外シュート */
        private Integer shotsOff;

        /** ブロックシュート */
        private Integer blocks;

        /** ビッグチャンス */
        private Integer bigChances;

        /** CK */
        private Integer corners;

        /** PA内シュート */
        private Integer boxShotsIn;

        /** PA外シュート */
        private Integer boxShotsOut;

        /** ポスト直撃 */
        private Integer goalPost;

        /** ヘディングゴール */
        private Integer headGoals;

        /** GKセーブ */
        private Integer saves;

        /** FK */
        private Integer freeKicks;

        /** オフサイド */
        private Integer offsides;

        /** ファウル */
        private Integer fouls;

        /** イエローカード */
        private Integer yc;

        /** レッドカード */
        private Integer rc;

        /** スローイン */
        private Integer throwIns;

        /** ボックスタッチ */
        private Integer boxTouches;

        /** パス数 */
        private String passes;

        /** ロングパス数 */
        private String longPasses;

        /** ファイナルサードパス数 */
        private String finalThirdPasses;

        /** クロス数 */
        private Integer crosses;

        /** タックル数 */
        private Integer tackles;

        /** クリア数 */
        private Integer clearances;

        /** デュエル数 */
        private Integer duels;

        /** インターセプト数 */
        private Integer interceptions;
    }

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
