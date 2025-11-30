package dev.web.api.bm_w008;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * HOME,AWAYのDTO
 * /api/{country}/{league}/{team}/correlations
 *
 * @author shiraishitoshio
 */
@Data
public class CorrelationsBySideDTO {

    @JsonProperty("HOME")
    private ScoreCorrelationsDTO home;

    @JsonProperty("AWAY")
    private ScoreCorrelationsDTO away;
}
