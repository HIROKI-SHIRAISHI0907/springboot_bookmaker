package dev.web.api.bm_a030;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 指定日の試合予定一覧のレスポンス
 * @author shiraishitoshio
 *
 */
@Data
public class FutureMatchesResponse {

	/** 対象日（yyyy-MM-dd） */
	private String date;

	/** 取得開始位置 */
	private Integer offset;

	/** 1ページの件数 */
	private Integer limit;

	/** 対象日の全件数（画面は使っていないが、ページ数の計算用に返す） */
	private Long total;

	/** 試合一覧 */
	private List<FutureMatchDTO> matches = new ArrayList<>();
}
