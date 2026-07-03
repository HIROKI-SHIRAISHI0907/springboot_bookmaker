package dev.web.config;

import java.io.IOException;
import java.util.Collections;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

@Configuration
public class GoogleAuthConfig {

    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    @Bean
    public GoogleCredentials googleCredentials() throws IOException {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                .createScoped(Collections.singletonList(CLOUD_PLATFORM_SCOPE));
        credentials.refreshIfExpired();
        return credentials;
    }

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }

    public static String getAccessToken(GoogleCredentials credentials) throws IOException {
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
}
