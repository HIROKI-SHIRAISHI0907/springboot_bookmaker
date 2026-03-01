package dev.common.readfile.dto;

import java.util.List;

import lombok.Data;

@Data
public class MatchKeyDTO {

	/** マッチキーリスト */
	private List<MatchKeyItem> items;

}
