package dev.web.api.bm_w003;

import lombok.Data;

/**
 * 年次サマリ（ALL / HOME / AWAY）
 * 小数は SQL の ROUND(...,2) を前提に Double で扱う
 */
@Data
public class OverviewSummaryDTO {

    /** 対象年度（例: 2025 / 2026） */
    private int year;

    // --- 対象試合数 ---
    private int gamesAll;
    private int gamesHome;
    private int gamesAway;

    // --- 勝点/試合 ---
    private Double pointsPerGameAll;
    private Double pointsPerGameHome;
    private Double pointsPerGameAway;

    // --- 得失点差（合計）---
    private Integer goalDiffAll;
    private Integer goalDiffHome;
    private Integer goalDiffAway;

    // --- 平均得点/平均失点（1試合あたり）---
    private Double avgGoalsForAll;
    private Double avgGoalsAgainstAll;

    private Double avgGoalsForHome;
    private Double avgGoalsAgainstHome;

    private Double avgGoalsForAway;
    private Double avgGoalsAgainstAway;

    // 任意：根拠となる合計（デバッグ・将来拡張用）
    private Integer pointsAll;
    private Integer goalsForAll;
    private Integer goalsAgainstAll;

    private Integer goalsForHome;
    private Integer goalsAgainstHome;

    private Integer goalsForAway;
    private Integer goalsAgainstAway;
}
