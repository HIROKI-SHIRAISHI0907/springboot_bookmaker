package dev.batch.constant;

/**
 * バッチステータス定数
 * @author shiraishitoshio
 *
 */
public class BatchStatusConstant {

	/** コンストラクタ生成禁止 */
	private BatchStatusConstant() {}

	/** 0: QUEUED */
	public static final int STATUS_QUEUED = 0;

	/** 1: RUNNING */
	public static final int STATUS_RUNNING = 1;

	/** 10: SUCCESS */
	public static final int STATUS_SUCCESS = 10;

	/** 99: FAILED */
	public static final int STATUS_FAILED = 99;

}
