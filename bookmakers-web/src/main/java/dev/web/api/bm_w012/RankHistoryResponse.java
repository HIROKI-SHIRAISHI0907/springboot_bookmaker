package dev.web.api.bm_w012;

import java.util.List;

import lombok.Data;

/**
 * RankHistoryResponse
 * @author shiraishitoshio
 *
 */
@Data
public class RankHistoryResponse {

	/**
	 * items
	 */
    private List<RankHistoryPointResponse> items;

}
