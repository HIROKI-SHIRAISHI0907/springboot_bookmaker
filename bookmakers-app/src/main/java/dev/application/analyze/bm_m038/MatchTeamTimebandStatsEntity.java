package dev.application.analyze.bm_m038;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import dev.application.analyze.common.entity.StatMetaEntity;
import dev.application.analyze.common.util.MatchTimeBandType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 試合時間帯別のチーム統計を表すEntityです。
 *
 * <p>1行が「1試合・1チーム・1時間帯」を表し、
 * 時間帯ごとの強さ・弱さ分析に利用します。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MatchTeamTimebandStatsEntity extends StatMetaEntity {

    /**
     * 主キーです。
     */
    private Integer id;

    /**
     * シーズンです。
     */
    private String season;

    /**
     * 時間帯区分です。
     */
    private MatchTimeBandType timeBand;

    /**
     * その時間帯の得点数です。
     */
    private Integer goalsCount;

    /**
     * その時間帯の失点数です。
     */
    private Integer goalsConcededCount;

    /**
     * その時間帯のシュート数です。
     */
    private Integer shotsCount;

    /**
     * その時間帯の枠内シュート数です。
     */
    private Integer shotsOnTargetCount;

    /**
     * その時間帯のボックスタッチ数です。
     */
    private Integer boxTouchesCount;

    /**
     * その時間帯のコーナー数です。
     */
    private Integer cornersCount;

    /**
     * その時間帯のカード数です。
     */
    private Integer cardsCount;

    /**
     * その時間帯の得点率です。
     */
    private BigDecimal scoringRate;

    /**
     * その時間帯の失点率です。
     */
    private BigDecimal concedingRate;

    /**
     * 試合全体における初失点時刻です。
     */
    private Integer firstConcededTimeSeconds;

    /**
     * 同点時に得点した時刻です。
     */
    private Integer equalStateScoringTimeSeconds;

    /**
     * リード時に失点した時刻です。
     */
    private Integer leadStateConcededTimeSeconds;

    /**
     * 80分以降に失点した時刻です。
     */
    private Integer lateConcededTimeSeconds;

    /**
     * 算出日時です。
     */
    private LocalDateTime calculatedAt;

}
