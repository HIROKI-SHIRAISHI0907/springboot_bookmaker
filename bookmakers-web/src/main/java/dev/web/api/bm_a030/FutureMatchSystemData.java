package dev.web.api.bm_a030;

import lombok.Data;

/**
 * システムデータ（static_data）の登録状況
 * @author shiraishitoshio
 *
 */
@Data
public class FutureMatchSystemData {

	/** times が「終了済」の行数 */
	private Integer finishedCount = 0;

	/** times が「終了済」以外（試合中に記録された）の行数 */
	private Integer liveCount = 0;
}
