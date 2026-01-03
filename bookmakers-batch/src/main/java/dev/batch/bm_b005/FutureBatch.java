package dev.batch.bm_b005;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.constant.BatchConstant;
import dev.batch.interf.BatchIF;
import dev.common.entity.FutureEntity;
import dev.common.getstatinfo.GetFutureInfo;
import dev.common.logger.ManageLoggerComponent;

/**
 * 未来統計用サービスクラス
 * @author shiraishitoshio
 *
 */
@Service
public class FutureBatch implements BatchIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = FutureBatch.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = FutureBatch.class.getSimpleName();

	/**
	 * 未来情報取得管理クラス
	 */
	@Autowired
	private GetFutureInfo getFutureInfo;

	/**
	 * BM_M022未来データ登録ロジック
	 */
	@Autowired
	private FutureStat futureStat;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent loggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int execute() throws Exception {
		final String METHOD_NAME = "execute";
		// ログ出力
		this.loggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 未来CSVデータ情報を取得
		Map<String, List<FutureEntity>> getFutureMap = this.getFutureInfo.getData();
		// BM_M022登録(Transactional)
		try {
			this.futureStat.futureStat(getFutureMap);
		} catch (Exception e) {
			// エラー
			return BatchConstant.BATCH_ERROR;
		}

		// endLog
		this.loggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		return BatchConstant.BATCH_SUCCESS;

	}

}
