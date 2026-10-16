package dev.batch.bm_b003;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.common.AbstractJobBatchTemplate;
import dev.common.constant.MessageCdConst;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.getinfo.GetSeasonInfo;

/**
 * シーズン情報登録バッチ実行クラス。
 * <p>
 * シーズンデータを取得し、登録ロジック（Transactional想定）を実行する。
 * </p>
 *
 * <p><b>実行方式</b></p>
 * <ul>
 *   <li>開始/終了ログを必ず出力する</li>
 *   <li>例外は内部で捕捉し、debugErrorLog に例外を付与して出力する</li>
 *   <li>戻り値で成功/失敗を返却する</li>
 * </ul>
 *
 * <p>2026-10 修正（ヘッダしかない CSV が S3 に残り続ける問題）</p>
 * <ul>
 *   <li>GetSeasonInfo の戻り値: null = ファイルなし・読み込みエラー（何もしない） / 空のリスト = ヘッダのみ
 *       （登録はせずファイルの削除だけ行う） / 1件以上 = 登録して削除。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Service("B003")
public class CountryLeagueSeasonMasterBatch extends AbstractJobBatchTemplate {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CountryLeagueSeasonMasterBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CountryLeagueSeasonMasterBatch.class.getName();

	/** エラーコード（運用ルールに合わせて変更） */
	private static final String ERROR_CODE = "BM_B003_ERROR";

	/** バッチコード */
	private static final String BATCH_CODE = "B003";

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

	/** チーム情報取得管理クラス */
	@Autowired
	private GetSeasonInfo getSeasonInfo;

	/** BM_M029統計分析ロジック */
	@Autowired
	private CountryLeagueSeasonMasterStat countryLeagueSeasonMasterStat;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doExecute(JobContext ctx) throws Exception {
		final String METHOD_NAME = "doExecute";
		// チームデータ情報を取得
		List<CountryLeagueSeasonMasterEntity> list = this.getSeasonInfo.getData();
		if (list == null) {
			// ファイルなし・読み込みエラー（削除しない）
			endLog();
			return;
		}
		if (list.isEmpty()) {
			// ヘッダのみ: 登録はせず、ファイルの削除だけ行う（残すと毎回同じファイルを読み続ける）
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					"登録対象が0件（ヘッダのみ）のため、ファイルの削除のみ行います: " + GetSeasonInfo.SEASON_KEY);
			this.countryLeagueSeasonMasterStat.deleteProcessedFile();
			endLog();
			return;
		}
		// BM_M029登録(Transactional)
		this.countryLeagueSeasonMasterStat.seasonStat(list);
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