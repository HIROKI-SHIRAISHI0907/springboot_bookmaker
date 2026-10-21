package dev.web.api.bm_a031;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * データカテゴリ統一: 画面に表示中のデータを入れたフォーマットのダウンロード
 * （POST /v1/api/admin/csv/upload/dataCategory/format の本文）
 *
 * @author shiraishitoshio
 */
@Data
public class DataCategoryFormatRequest {

	/** 表示中の行（画面の並び順のまま CSV にする） */
	private List<Row> rows = new ArrayList<>();

	/** 1 行（ホーム・アウェー・現在のカテゴリの組み合わせ） */
	@Data
	public static class Row {
		/** 処理区分（画面で新しい値を入れた行は 2。それ以外は空欄） */
		private String operation;
		private String homeTeamName;
		private String awayTeamName;
		/** 現在のデータカテゴリ（無ければ空） */
		private String currentDataCategory;
		/** 新しいデータカテゴリ（画面で入力中の値。無ければ空） */
		private String newDataCategory;
		/** 参考: static_data の件数 */
		private Long count;
		/** 参考: 国リーグ判定（一致 / 不一致 / 混在 / 不明） */
		private String leagueCheck;
		/** 参考: 国リーグ候補 */
		private List<String> leagueCandidates;
		/** 参考: 形式（形式一致 / 形式不一致） */
		private String format;
	}
}
