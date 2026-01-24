package dev.application.analyze.bm_m001;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.interf.OriginIF;
import dev.common.entity.DataEntity;
import dev.common.getinfo.GetOriginInfo;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M001統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
public class OriginService implements OriginIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = OriginService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = OriginService.class.getSimpleName();

	/**
	 * 起源データ取得管理クラス
	 */
	@Autowired
	private GetOriginInfo getOriginInfo;

	/**
	 * BM_M001起源データ登録ロジック
	 */
	@Autowired
	private OriginStat originStat;

	/**
	 * 未来データフラグサービス
	 */
	@Autowired
	private FutureStartFlgService futureStartFlgService;

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

		// 直近のCSVデータ情報を取得
		Map<String, List<DataEntity>> getStatMap = this.getOriginInfo.getData();
		// BM_M001登録(Transactional)
		try {
			this.originStat.originStat(getStatMap);
		} catch (Exception e) {
			this.loggerComponent.createSystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					e.getMessage(),
					e.getCause());
		}

		// 未来データフラグ更新
		try {
			this.futureStartFlgService.execute(getStatMap);
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

		return 0;
	}

}
