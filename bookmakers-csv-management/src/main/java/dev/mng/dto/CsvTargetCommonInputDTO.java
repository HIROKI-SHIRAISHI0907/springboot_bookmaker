package dev.mng.dto;

import java.util.List;

import lombok.Data;

/**
 * サービス共通用DTO
 * @author shiraishitoshio
 *
 */
@Data
public class CsvCommonInputDTO {

	/** リスト */
	private List<SubInput> subList;

}
