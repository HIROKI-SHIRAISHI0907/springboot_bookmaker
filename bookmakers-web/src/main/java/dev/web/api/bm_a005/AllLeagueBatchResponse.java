package dev.web.api.bm_a005;

// dev.web.api.bm_a005.AllLeagueBatchResponse
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AllLeagueBatchResponse {
  private String responseCode; // "200" or "207"(partial) or "400"
  private int total;
  private int success;
  private int failed;
  private List<ItemResult> results;

  @Data @AllArgsConstructor
  public static class ItemResult {
    private String country;
    private String league;
    private String responseCode; // "200"/"400"/"404"/"409"/"500"
    private String message;
  }
}
