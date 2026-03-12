package dev.web.api.bm_w006;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TeamsStandingsResponse {

    /** シーズン */
    private String seasonYear;

    /** 最新節（trendからmaxを取る） */
    private Integer latestMatch;

    /** 最新節の順位表（表示用） */
    private List<TeamStandingsRowViewDTO> standings;

    /** 全節×全チームの推移（グラフ用） */
    private List<TeamStandingsRowDTO> trend;

}
