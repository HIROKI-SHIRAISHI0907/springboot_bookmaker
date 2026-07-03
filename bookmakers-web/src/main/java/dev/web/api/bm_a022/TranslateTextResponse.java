package dev.web.api.bm_a022;

import java.util.List;

import lombok.Data;

@Data
public class TranslateTextResponse {

    private List<TranslationItem> translations;

    @Data
    public static class TranslationItem {
        private String translatedText;
        private String detectedLanguageCode;
    }
}
