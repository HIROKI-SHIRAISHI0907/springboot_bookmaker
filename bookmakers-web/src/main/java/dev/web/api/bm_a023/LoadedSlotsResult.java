package dev.web.api.bm_a023;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * S3から読み込んだ noEcsRun JSON の結果
 */
@Data
@NoArgsConstructor
public class LoadedSlotsResult {

    private String bucket;
    private String key;
    private String fileName;
    private JsonNode body;

    public LoadedSlotsResult(String bucket, String key, String fileName, JsonNode body) {
        this.bucket = bucket;
        this.key = key;
        this.fileName = fileName;
        this.body = body;
    }
}
