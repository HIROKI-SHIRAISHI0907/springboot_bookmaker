package dev.web.api.bm_w099;

import lombok.Data;

/**
 *バッチ実行リクエストDTO
 * @author shiraishitoshio
 *
 */
@Data
public class BatchExecuteRequestDTO {

	/** 実行するバッチコード（B001〜B005） */
	private String batchCode;

	/** 実行モード（例: WEB / MANUAL / TEST 等） */
	private String exeMode;

	/** ロジックコード（ログのThreadContext用。任意運用） */
	private String logicCd;

	/** 国（ThreadContext用。必要な時だけ） */
	private String country;

	/** リーグ（ThreadContext用。必要な時だけ） */
	private String league;

	/** 補足情報（ThreadContext用。国/リーグの代わりに使う） */
	private String info;

}
