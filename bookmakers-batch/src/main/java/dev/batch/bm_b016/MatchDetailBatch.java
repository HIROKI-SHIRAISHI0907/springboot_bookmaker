package dev.batch.bm_b016;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.common.AbstractJobBatchTemplate;
import dev.common.getinfo.GetMatchDetailInfo;
import dev.common.readfile.dto.MatchDetailDataDTO;

/**
 * 試合詳細データ登録処理実行クラス。
 * <p>
 * 試合詳細スクレイピング（Python: MatchDetailData_aws_S3.py）が aws-s3-detail-data-csv に出力した
 * CSV（1 試合 = 1 フォルダ）を読み、soccer_bm の match_detail_* テーブルに登録する。
 * </p>
 *
 * <p><b>実行方式</b></p>
 * <ul>
 *   <li>開始/終了ログを必ず出力する</li>
 *   <li>例外は内部で捕捉し、debugErrorLog に例外を付与して出力する</li>
 *   <li>戻り値で成功/失敗を返却する</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Service("B016")
public class MatchDetailBatch extends AbstractJobBatchTemplate {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = MatchDetailBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = MatchDetailBatch.class.getName();

	/** エラーコード（運用ルールに合わせて変更） */
	private static final String ERROR_CODE = "BM_B016_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B016";

	/** オーバーライド */
	@Override
	protected String batchCode() {
		return BATCH_CODE;
	}

	@Override
	protected String errorCode() {
		return ERROR_CODE;
	}

	@Override
	protected String projectName() {
		return PROJECT_NAME;
	}

	@Override
	protected String className() {
		return CLASS_NAME;
	}

	/** 試合詳細データ取得管理クラス */
	@Autowired
	private GetMatchDetailInfo getMatchDetailInfo;

	/** MatchDetailStatクラス */
	@Autowired
	private MatchDetailStat matchDetailStat;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doExecute(JobContext ctx) throws Exception {
		Map<String, MatchDetailDataDTO> map = this.getMatchDetailInfo.getData();
		if (map == null || map.isEmpty()) {
			endLog();
			return;
		}

		this.matchDetailStat.matchDetailStat(map);
		endLog();
	}

	/**
	 * 終了ログ
	 */
	private void endLog() {
		final String METHOD_NAME = "endLog";
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
	}
}
