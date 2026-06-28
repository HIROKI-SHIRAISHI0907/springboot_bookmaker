package dev.application.analyze.bm_m034;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import dev.application.analyze.common.entity.StatMetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 試合中のチーム時点スナップショットFactを表すEntityです。
 *
 * <p>1行が「1試合・1チーム・1時点」の累積状態を表します。
 * リアルタイム予測、モメンタム分析、得点確率分析の元データになります。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MatchTeamSnapshotFactEntity extends StatMetaEntity {

    /**
     * 主キーです。
     */
    private Integer id;

    /**
     * シーズンです。
     */
    private String season;

    /**
     * ホームチームかどうかです。
     */
    private Boolean homeFlg;

    /**
     * 試合経過秒です。
     */
    private Integer asOfSeconds;

    /**
     * 表示上の試合時間です。
     */
    private String matchTimeLabel;

    /**
     * 現時点の自チーム得点です。
     */
    private Integer teamScore;

    /**
     * 現時点の相手チーム得点です。
     */
    private Integer opponentScore;

    /**
     * スコア差です。
     */
    private Integer scoreDiff;

    /**
     * ポゼッション率です。
     */
    private BigDecimal possessionRate;

    /**
     * 累積シュート数です。
     */
    private Integer shotsCount;

    /**
     * 累積枠内シュート数です。
     */
    private Integer shotsOnTargetCount;

    /**
     * 累積枠外シュート数です。
     */
    private Integer shotsOffTargetCount;

    /**
     * 累積ブロックシュート数です。
     */
    private Integer blockedShotsCount;

    /**
     * 累積ビッグチャンス数です。
     */
    private Integer bigChancesCount;

    /**
     * 累積コーナーキック数です。
     */
    private Integer cornersCount;

    /**
     * 累積ボックスタッチ数です。
     */
    private Integer boxTouchesCount;

    /**
     * 累積パス数です。
     */
    private Integer passesCount;

    /**
     * 累積ロングパス数です。
     */
    private Integer longPassesCount;

    /**
     * 累積ファイナルサードパス数です。
     */
    private Integer finalThirdPassesCount;

    /**
     * 累積クロス数です。
     */
    private Integer crossesCount;

    /**
     * 累積タックル数です。
     */
    private Integer tacklesCount;

    /**
     * 累積クリア数です。
     */
    private Integer clearancesCount;

    /**
     * 累積デュエル勝利数です。
     */
    private Integer duelsWonCount;

    /**
     * 累積インターセプト数です。
     */
    private Integer interceptionsCount;

    /**
     * 累積イエローカード数です。
     */
    private Integer yellowCardsCount;

    /**
     * 累積レッドカード数です。
     */
    private Integer redCardsCount;

    /**
     * スナップショット記録日時です。
     */
    private LocalDateTime snapshotRecordedAt;

    /**
     * 元データ件数です。
     */
    private Integer sourceCount;

    /**
     * データ品質フラグです。
     */
    private String dataQualityFlag;

    /**
     * 備考です。
     */
    private String note;

}
