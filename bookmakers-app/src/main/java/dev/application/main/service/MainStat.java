package dev.application.main.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.application.analyze.bm_m027.RankingService;
import dev.application.analyze.interf.ServiceIF;
import dev.common.constant.BatchResultConst;
import dev.common.entity.BookDataEntity;
import dev.common.getinfo.GetStatInfo;
import dev.common.logger.ManageLoggerComponent;

/**
 * 統計バッチ実行クラス
 * @author shiraishitoshio
 *
 */
@Service
public class MainStat implements ServiceIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = MainStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = MainStat.class.getSimpleName();

	/**
	 * 統計情報取得管理クラス
	 */
	@Autowired
	private GetStatInfo getStatInfo;

	/** StatService */
	@Autowired
	private CoreStat statService;

	/** RankingService */
	@Autowired
	private RankingService rankingService;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int execute() throws Exception {
		final String METHOD_NAME = "execute";
		// ログ出力
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// シーケンスデータから取得(最大値情報取得)
		String csvNumber = "0";
		String csvBackNumber = null;

		// 直近のCSVデータ情報を取得
		Map<String, Map<String, List<BookDataEntity>>> getStatMap =
		        this.getStatInfo.getStatMap(csvNumber, csvBackNumber);

		// BM_M027以外登録(Transactional)
		try {
			this.statService.execute(getStatMap);
		} catch (Exception e) {
			// エラー
			return BatchResultConst.BATCH_ERR;
		}

		// BM_M027登録(Transactional)
		try {
			this.rankingService.execute(getStatMap);
		} catch (Exception e) {
			// エラー
			return BatchResultConst.BATCH_ERR;
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		return BatchResultConst.BATCH_OK;
	}

}
