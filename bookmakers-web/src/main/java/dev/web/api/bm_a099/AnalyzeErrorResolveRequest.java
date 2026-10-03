package dev.web.api.bm_a099;

import java.util.List;

import lombok.Data;

/**
 * 対応状況・メモの更新リクエスト
 * <ul>
 *   <li>resolvedFlg: true = 対応済みにする / false = 未対応に戻す / null = 変えない（メモだけ更新）</li>
 *   <li>note: null ならメモは変えない。空文字ならメモを消す。</li>
 *   <li>seqs: まとめて更新するとき（PATCH /matches/resolve/batch）だけ使う</li>
 * </ul>
 * @author shiraishitoshio
 *
 */
@Data
public class AnalyzeErrorResolveRequest {

	private Boolean resolvedFlg;

	/** 対応者（未指定なら ADMIN） */
	private String resolvedBy;

	private String note;

	/** まとめて更新する seq */
	private List<String> seqs;
}
