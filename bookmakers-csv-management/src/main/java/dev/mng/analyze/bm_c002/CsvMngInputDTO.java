package dev.mng.analyze.bm_c002;

import java.util.List;

import lombok.Data;

/**
 * CSV管理InputDTO
 * @author shiraishitoshio
 *
 */
@Data
public class CsvMngInputDTO {

	/** サブインフォオブジェクト */
	private List<SubInfo> subInfo;

}
