package dev.application.analyze.bm_m027;

import java.util.HashMap;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * timesが45:XX,46:XXなどハーフタイム付近の並び替えに関するサブ結果DTO
 * @author shiraishitoshio
 *
 */
@Setter
@Getter
public class MakeStatisticsGameCountCsvOutputDTO {

	/**
	 * 詳細リスト
	 */
	private List<List<String>> detaiList;

	/**
	 * 親詳細マップ
	 */
	private HashMap<String, HashMap<String, HashMap<String,String>>> oyaDetailMap;

}
