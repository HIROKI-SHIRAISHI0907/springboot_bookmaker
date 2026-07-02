package dev.web.api.bm_a022;

import java.util.List;

import lombok.Data;

/**
 * GeograficRequest
 * @author shiraishitoshio
 *
 */
@Data
public class GeograficRequest {

	/** リクエストマッチ */
	private List<Item> matches;

	@Data
	public static class Item {

		/** 国 */
		private String country;

		/** リーグ */
		private String league;

		/** チーム */
		private String team;

		/** ホーム都市 */
		private String homeCity;

		/** スタジアム名 */
		private String stadium;
	}

}
