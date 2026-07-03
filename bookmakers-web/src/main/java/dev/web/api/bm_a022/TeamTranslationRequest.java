package dev.web.api.bm_a022;

import lombok.Data;

@Data
public class TeamTranslationRequest {

    private String teamName;
    private String country;
    private String homeCity;
    private String stadium;

}
