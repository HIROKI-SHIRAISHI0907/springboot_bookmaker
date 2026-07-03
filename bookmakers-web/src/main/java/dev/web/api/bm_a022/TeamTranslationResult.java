package dev.web.api.bm_a022;

import lombok.Data;

@Data
public class TeamTranslationResult {

    private String teamName;
    private String country;
    private String homeCity;
    private String stadium;
    private String targetLanguageCode;

}
