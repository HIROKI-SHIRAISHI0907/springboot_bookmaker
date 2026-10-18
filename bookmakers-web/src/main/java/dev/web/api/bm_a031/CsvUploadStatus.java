package dev.web.api.bm_a031;

/**
 * ファイルアップロードの状態（file_upload_status_manage.status）と、エラーの段階（error_phase）
 *
 * <pre>
 * アップロード ──（受付）──▶ PENDING 判定前 ─┬─（バリデーション OK）→ S3 uploaded/ に置く（PENDING のまま）
 *                                             │                         └─ バッチサービス ─┬─ DB チェック・反映 OK → success/ … SUCCESS 正常終了
 *                                             │                                            └─ DB チェック NG       → error/   … ERROR 異常終了（phase=DB）
 *                                             └─（バリデーション NG）→ S3 error/ に _error.zip を置く … ERROR 異常終了（phase=VALIDATION）
 * </pre>
 *
 * @author shiraishitoshio
 */
public final class CsvUploadStatus {

	/** 判定前（バリデーションを通り uploaded/ に置いた。バッチサービスの処理待ち） */
	public static final String PENDING = "PENDING";

	/** 判定中（バッチサービスが処理している。バッチサービスで使う） */
	public static final String PROCESSING = "PROCESSING";

	/** 正常終了（バッチサービスで反映して success/ に置いた） */
	public static final String SUCCESS = "SUCCESS";

	/** 異常終了（error/ に _error.zip を置いた。画面からダウンロードできる） */
	public static final String ERROR = "ERROR";

	/** エラーの段階: web のバリデーション（ファイル名・形・必須項目など） */
	public static final String PHASE_VALIDATION = "VALIDATION";

	/** エラーの段階: S3 への保管に失敗 */
	public static final String PHASE_STORE = "STORE";

	/** エラーの段階: バッチサービスの DB チェック・反映 */
	public static final String PHASE_DB = "DB";

	private CsvUploadStatus() {}
}
