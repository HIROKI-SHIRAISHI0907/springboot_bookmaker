package dev.application.analyze.bm_m037;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 試合単位の守備力・被圧力統計を表すEntityです。
 *
 * <p>被シュート、セーブ率、ブロック率、イベント後の被圧上昇などを管理します。</p>
 */
@Data
public class MatchTeamDefenseStatsEntity {

    /**
     * 主キーです。
     */
    private Integer id;

    /**
     * 試合IDです。
     */
    private String matchId;

    /**
     * シーズンです。
     */
    private String season;

    /**
     * 国です。
     */
    private String country;

    /**
     * リーグIDです。
     */
    private String leagueId;

    /**
     * リーグ名です。
     */
    private String leagueName;

    /**
     * チームIDです。
     */
    private String teamId;

    /**
     * チーム名です。
     */
    private String teamName;

    /**
     * 対戦相手チームIDです。
     */
    private Long opponentTeamId;

    /**
     * 対戦相手チーム名です。
     */
    private String opponentTeamName;

    /**
     * 被シュート数です。
     */
    private Integer shotsConcededCount;

    /**
     * 被枠内シュート数です。
     */
    private Integer shotsOnTargetConcededCount;

    /**
     * 被ボックスタッチ数です。
     */
    private Integer boxTouchesConcededCount;

    /**
     * キーパーセーブ数です。
     */
    private Integer keeperSavesCount;

    /**
     * セーブ率です。
     */
    private BigDecimal saveRate;

    /**
     * ブロックシュート数です。
     */
    private Integer blockedShotsCount;

    /**
     * ブロック率です。
     */
    private BigDecimal blockRate;

    /**
     * クリア数です。
     */
    private Integer clearancesCount;

    /**
     * クリア頻度です。
     */
    private BigDecimal clearanceFrequency;

    /**
     * タックル数です。
     */
    private Integer tacklesCount;

    /**
     * インターセプト数です。
     */
    private Integer interceptionsCount;

    /**
     * 守備アクション率です。
     */
    private BigDecimal defensiveActionRate;

    /**
     * リード時の被シュート増加率です。
     */
    private BigDecimal leadStateShotsConcededIncreaseRate;

    /**
     * 失点後10分の被攻撃量です。
     */
    private BigDecimal postConceded10mPressure;

    /**
     * 退場後10分の被圧力です。
     */
    private BigDecimal postRedCard10mPressure;

    /**
     * 算出日時です。
     */
    private LocalDateTime calculatedAt;

    /**
     * 備考です。
     */
    private String note;

}
