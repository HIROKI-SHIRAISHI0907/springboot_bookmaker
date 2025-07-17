package dev.common.makecsv;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * R言語データoutputDTO
 * @author shiraishitoshio
 *
 */
@Setter
@Getter
public class RDataOutputDTO {

	/**
	 * ヘッダーフラグ
	 */
	private boolean headerFlg;

	/**
	 * 重複リスト
	 */
	private List<String> dupList;

}
