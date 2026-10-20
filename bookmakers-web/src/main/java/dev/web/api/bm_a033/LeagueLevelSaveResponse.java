package dev.web.api.bm_a033;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * リーグレベル情報反映設定: 保存の結果
 * @author shiraishitoshio
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeagueLevelSaveResponse {

	/** 更新した 国・リーグ の数 */
	private int updatedLeagues;

	/** 更新した行数（チームの行） */
	private int updatedRows;

	/** 保存後の一覧 */
	private List<LeagueLevelDTO> items = new ArrayList<>();
}
