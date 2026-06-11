package dev.batch.constant;

/**
 * フラグ定数
 * @author shiraishitoshio
 *
 */
public class FlgConstant {

	/** コンストラクタ生成禁止 */
	private FlgConstant() {}

	/** 初回CSVマスタ登録 */
	public static final String INITIAL_FLG = "0";

	/** 初回以降CSVマスタ登録 */
	public static final String NOT_INITIAL_FLG = "1";

}
