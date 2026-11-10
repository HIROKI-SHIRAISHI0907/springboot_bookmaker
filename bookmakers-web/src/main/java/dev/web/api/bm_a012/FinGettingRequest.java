package dev.web.api.bm_a012;

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

		/**
		 * 終了済データを取るか（2026-11 追加）。false なら fin の JSON（B008 終了済データ取得の入力）に入れない。
		 * null（以前の画面からのリクエスト）は true として扱う。
		 */
		private Boolean needFinished;

		/**
		 * 統計詳細データを取るか（2026-11 追加）。false なら b016_game_detail_path_&lt;連番&gt;.json に入れない。
		 * null は true として扱う。
		 */
		private Boolean needDetail;

		/** 終了済データを取るか（null は true） */
		public boolean wantsFinished() {
			return !Boolean.FALSE.equals(needFinished);
		}

		/** 統計詳細データを取るか（null は true） */
		public boolean wantsDetail() {
			return !Boolean.FALSE.equals(needDetail);
		}
	}

}
