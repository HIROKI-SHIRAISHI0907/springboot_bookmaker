package dev.web.api.bm_a005;

import java.util.List;

import lombok.Data;

@Data
public class AllLeagueBatchRequest {

	/** まとめた更新 */
	private List<AllLeagueRequest> items;

}
