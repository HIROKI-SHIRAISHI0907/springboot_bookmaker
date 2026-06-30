package dev.web.api.bm_a023;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * noEcsRun JSON取得レスポンス
 */
@Data
@NoArgsConstructor
public class NoEcsSlotsResponse {

    private String bucket;
    private String key;
    private String fileName;
    private JsonNode body;
    private String message;
}
