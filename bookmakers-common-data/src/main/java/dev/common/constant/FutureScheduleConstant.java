package dev.common.constant;

/**
 * 未来データ関係定数
 * @author shiraishitoshio
 *
 */
public class FutureScheduleConstant {

	/** コンストラクタ生成禁止 */
	private FutureScheduleConstant() {}

	/** ライブ中 */
	public static final String LIVE = "LIVE";

	/** 試合終了済み */
	public static final String FINISHED = "FINISHED";

	/** 試合予定 */
	public static final String SCHEDULED = "SCHEDULED";

	/** 延期（本日予定の試合が別日になった） */
	public static final String POSTPONED = "POSTPONED";

	/** 遅延（本日予定の試合開始が遅れているor試合中に雨等で中断） */
	public static final String DELAYED = "DELAYED";

	/** 中断 */
	public static final String INTERRUPTED = "INTERRUPTED";

}
