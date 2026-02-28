package dev.web.api.bm_w015;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * FinGettingRequest
 * @author shiraishitoshio
 *
 */
@Data
public class FinGettingRequest {

	/** リクエストマッチ */
	private List<Item> matches;

	@Data
	public static class Item {

		/** 試合日フォーマット */
		@JsonFormat(pattern = "yyyy-MM-dd")
		private LocalDate matchDate; // 試合日 (JST想定のYYYY-MM-DD)

		/** マッチID */
		private String matchId; // Flashscoreのmid（あなたの前提：gameLinkのmidと一致）

		/** マッチURL */
		private String matchUrl; // あれば入れる（無ければ null/空でOK）
	}

}
