// src/main/java/dev/web/api/bm_w006/LeagueStandingDTO.java
package dev.web.api.bm_w006;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * リーグ順位表APIレスポンス
 * /api/standings/{country}/{league}
 *
 * フロント側 LeagueStanding 型に対応:
 *  - season
 *  - updatedAt
 *  - rows
 *
 * 現時点では season / updatedAt は DB からの取得がないため
 * null 固定とし、将来拡張用とする。
 *
 * @author shiraishitoshio
 */
@Data
public class LeagueStandingResponse {

    /** シーズン名（例: "2024-2025"。現状は null 固定） */
    private String season;

    /** 更新日時（ISO文字列。現状は null 固定） */
    private String updatedAt;

    /** 順位表の行一覧 */
    @JsonProperty("rows")
    private List<StandingRowDTO> rows;

    public LeagueStandingResponse(List<StandingRowDTO> rows) {
        this.rows = rows;
    }
}
