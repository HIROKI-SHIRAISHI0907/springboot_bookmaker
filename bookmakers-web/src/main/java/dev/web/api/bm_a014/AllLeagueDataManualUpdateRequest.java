package dev.web.api.bm_a014;

import java.util.List;

import lombok.Data;

/**
 * AllLeagueDataManualUpdateRequest
 * @author shiraishitoshio
 *
 */
@Data
public class AllLeagueDataManualUpdateRequest {

	/** リクエストマッチ */
	private List<Item> leagues;

	@Data
	public static class Item {

		/** 国 */
		private String country; // 変更想定の国

		/** リーグ */
		private String league; // 変更想定のリーグ

		/** 変更想定のサブリーグ */
		private String subLeague; // 変更想定のサブリーグ

		/** チーム */
		private String team; // 変更想定のチーム
	}

}
