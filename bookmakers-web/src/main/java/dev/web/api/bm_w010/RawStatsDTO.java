// dev/web/api/bm_w010/RawStatsDTO.java
package dev.web.api.bm_w010;

import java.util.Map;

import lombok.Data;

/**
 * stats の HOME/AWAY 部分そのものを表す DTO。
 * HOME / AWAY どちらも
 *   scoreKey -> ( metricName -> "csv文字列 or null" )
 * という Map 構造。
 */
@Data
public class RawStatsDTO {

	/** HOME */
    private Map<String, Map<String, String>> HOME;

    /** AWAY */
    private Map<String, Map<String, String>> AWAY;
}
