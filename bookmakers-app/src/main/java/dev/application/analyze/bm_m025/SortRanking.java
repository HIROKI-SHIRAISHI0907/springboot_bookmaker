package dev.application.analyze.bm_m025;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * ソートランキングデータ
 * @author shiraishitoshio
 *
 */
@Data
@AllArgsConstructor
public class SortRanking {

	/** フィールド名 */
	private String field;

	/** 相関係数 */
	private String corr;

}
