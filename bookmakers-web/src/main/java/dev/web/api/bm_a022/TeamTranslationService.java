package dev.web.api.bm_a022;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

import reactor.core.publisher.Mono;

/**
 * チーム情報現地言語翻訳クラス
 * @author shiraishitoshio
 *
 */
@Service
public class TeamTranslationService {

    private static final String CLOUD_PLATFORM_SCOPE =
            "https://www.googleapis.com/auth/cloud-platform";

    private final WebClient webClient;
    private final CountryLanguageResolver countryLanguageResolver;

    @Value("${google.cloud.project-id:}")
    private String projectId;

    @Value("${google.cloud.translation.endpoint:https://translate.googleapis.com}")
    private String endpoint;

    @Value("${google.cloud.translation.location:global}")
    private String location;

    @Value("${google.cloud.translation.source-language:ja}")
    private String sourceLanguageCode;

    public TeamTranslationService(
            WebClient webClient,
            CountryLanguageResolver countryLanguageResolver) {
        this.webClient = webClient;
        this.countryLanguageResolver = countryLanguageResolver;
    }

    public TeamTranslationResult translateToLocalLanguage(TeamTranslationRequest request) throws IOException {
        String targetLanguageCode = countryLanguageResolver.resolveTargetLanguageCode(request.getCountry());
        return translate(request, targetLanguageCode);
    }

    public TeamTranslationResult translate(TeamTranslationRequest request, String targetLanguageCode) throws IOException {
        validateConfig();

        List<String> contents = new ArrayList<>();
        contents.add(nvl(request.getTeamName()));
        contents.add(nvl(request.getCountry()));
        contents.add(nvl(request.getHomeCity()));
        contents.add(nvl(request.getStadium()));

        Map<String, Object> body = new HashMap<>();
        body.put("contents", contents);
        body.put("mimeType", "text/plain");
        body.put("sourceLanguageCode", sourceLanguageCode);
        body.put("targetLanguageCode", targetLanguageCode);

        String url = endpoint
                + "/v3/projects/" + projectId
                + "/locations/" + location
                + ":translateText";

        String accessToken = getAccessToken();

        TranslateTextResponse response = webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("x-goog-user-project", projectId)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(errorBody -> Mono.error(
                                        new RuntimeException("Translation API call failed. status="
                                                + clientResponse.statusCode()
                                                + ", url=" + url
                                                + ", body=" + errorBody))))
                .bodyToMono(TranslateTextResponse.class)
                .block();

        if (response == null || response.getTranslations() == null || response.getTranslations().size() < 4) {
            throw new RuntimeException("Translation API response is invalid.");
        }

        TeamTranslationResult result = new TeamTranslationResult();
        result.setTeamName(getTranslatedText(response, 0, request.getTeamName()));
        result.setCountry(getTranslatedText(response, 1, request.getCountry()));
        result.setHomeCity(getTranslatedText(response, 2, request.getHomeCity()));
        result.setStadium(getTranslatedText(response, 3, request.getStadium()));
        result.setTargetLanguageCode(targetLanguageCode);

        return result;
    }

    private void validateConfig() {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException("google.cloud.project-id が未設定です。");
        }
        if ("your-gcp-project-id".equals(projectId)) {
            throw new IllegalStateException("google.cloud.project-id がダミー値のままです。");
        }
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("google.cloud.translation.endpoint が未設定です。");
        }
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("google.cloud.translation.location が未設定です。");
        }
    }

    private String getAccessToken() throws IOException {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                .createScoped(Collections.singletonList(CLOUD_PLATFORM_SCOPE));

        credentials.refreshIfExpired();
        AccessToken accessToken = credentials.getAccessToken();

        if (accessToken == null) {
            credentials.refresh();
            accessToken = credentials.getAccessToken();
        }

        if (accessToken == null) {
            throw new IOException("Failed to obtain Google access token.");
        }

        return accessToken.getTokenValue();
    }

    private String getTranslatedText(TranslateTextResponse response, int index, String fallback) {
        if (response.getTranslations().size() <= index) {
            return fallback;
        }
        TranslateTextResponse.TranslationItem item = response.getTranslations().get(index);
        if (item == null || item.getTranslatedText() == null || item.getTranslatedText().isBlank()) {
            return fallback;
        }
        return item.getTranslatedText();
    }

    private String nvl(String value) {
        return value == null ? "" : value.trim();
    }
}
