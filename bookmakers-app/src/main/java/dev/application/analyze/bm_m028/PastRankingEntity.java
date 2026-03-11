package dev.application.analyze.bm_m028;

import java.io.Serializable;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class PastRankingEntity extends MetaEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** ID (PK) */
    private Long id;

    /** 国 */
    private String country;

    /** リーグ */
    private String league;

    /** シーズン(country_league_season_masterのseasonYear) */
    private String seasonYear;

    /** 節 */
    private Integer match;

    /** チーム */
    private String team;

    /** 勝ち */
    private Integer win;

    /** 負け */
    private Integer lose;

    /** 引き分け */
    private Integer draw;

    /** 勝ち点 */
    private Integer winningPoints;

}
