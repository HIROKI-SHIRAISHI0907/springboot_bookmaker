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

	@Override
	public int execute() throws Exception {
		// 未来CSVデータ情報を取得
		Map<String, List<FutureEntity>> getFutureMap = this.getFutureInfo.getData();

		// BM_M022登録(Transactional)

		// 全ての登録ができた場合CSV削除
		return 0;
	}

}
