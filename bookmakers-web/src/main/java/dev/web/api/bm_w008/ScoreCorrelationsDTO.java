package dev.web.api.bm_w008;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * 前半,後半のDTO
 * /api/{country}/{league}/{team}/correlations
 *
 * @author shiraishitoshio
 */
@Data
public class ScoreCorrelationsDTO {

    @JsonProperty("1st")
    private List<CorrelationsItemDTO> first;

    @JsonProperty("2nd")
    private List<CorrelationsItemDTO> second;

    @JsonProperty("ALL")
    private List<CorrelationsItemDTO> all;
}
