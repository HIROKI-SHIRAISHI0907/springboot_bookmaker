package dev.application.main.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.application.analyze.bm_m022.FutureStat;
import dev.common.entity.FutureEntity;
import dev.common.getstatinfo.GetFutureInfo;
import dev.common.logger.ManageLoggerComponent;

/**
 * 未来統計用サービスクラス
 * @author shiraishitoshio
 *
 */
@Service
public class FutureService implements FutureIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = FutureService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = FutureService.class.getSimpleName();

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
	public void execute() throws Exception {
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
			this.loggerComponent.createSystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					e.getMessage(),
					e.getCause());
		}

		// endLog
		this.loggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

	}

}
