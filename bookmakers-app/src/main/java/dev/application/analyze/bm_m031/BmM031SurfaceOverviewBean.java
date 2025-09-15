package dev.application.analyze.bm_m031;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.logger.ManageLoggerComponent;
import jakarta.annotation.PostConstruct;

/**
 * surface_overviewのbeanロジック
 * @author shiraishitoshio
 *
 */
@Component
public class BmM031SurfaceOverviewBean {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = BmM031SurfaceOverviewBean.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = BmM031SurfaceOverviewBean.class.getSimpleName();

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent loggerComponent;

	/** 初期化
	 * @throws Exception */
	@PostConstruct
	public void init() throws Exception {

	}

}
