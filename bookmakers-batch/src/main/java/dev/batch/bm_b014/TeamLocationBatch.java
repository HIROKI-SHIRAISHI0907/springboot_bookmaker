package dev.batch.bm_b014;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.common.AbstractJobBatchTemplate;
import dev.common.entity.TeamLocationEntity;
import dev.common.getinfo.GetGeograficInfo;

/**
 * スタジアムマスタ設定処理実行クラス。
 * <p>
 * dataテーブルに保存されているstadium情報を元にマスタに保存する<br>
 * team_location_masterのscrape用に格納したJSONに利用される
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
@Service("B014")
public class TeamLocationBatch extends AbstractJobBatchTemplate {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamLocationBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamLocationBatch.class.getName();

	/** エラーコード（運用ルールに合わせて変更） */
	private static final String ERROR_CODE = "BM_B014_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B014";

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

	/** 地理情報取得管理クラス */
	@Autowired
	private GetGeograficInfo geograficInfo;

	/** TeamLocationStatクラス */
	@Autowired
	private TeamLocationStat teamLocationStat;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doExecute(JobContext ctx) throws Exception {
		// 地理データ情報を取得
		List<TeamLocationEntity> listMap = this.geograficInfo.getData();
		this.teamLocationStat.teamLocationStat(listMap, ctx.readyFlg());
	}

}
