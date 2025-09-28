package dev.mng.dto;

import java.util.List;

import lombok.Data;

/**
 * 統計計算用メインDTO
 * @author shiraishitoshio
 *
 */
@Data
public class StatConditionDTO {

	/** メイン */
	private List<ConditionData> main;

}
