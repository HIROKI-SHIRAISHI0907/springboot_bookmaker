package dev.web.api.bm_u001;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * StatSizeFinalizeCsvEntity
 * @author shiraishitoshio
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class StatSizeFinalizeDTO extends MetaEntity {

	/** ID */
	private String id;

	/** 選択肢No. */
	private String optionNum;

	/** 選択肢 */
	private String options;

	/** フラグ(0:有効,1:無効) */
	private String validFlg;

}
