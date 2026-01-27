package dev.application.analyze.bm_m001;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.master.FutureMasterRepository;
import dev.common.entity.DataEntity;
import dev.common.entity.FutureEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * 未来データ更新サービスクラス
 * @author shiraishitoshio
 *
 */
@Service
@Transactional
public class FutureStartFlgService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = FutureStartFlgService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = FutureStartFlgService.class.getSimpleName();

	/** 有効 */
	private static final String STRAT_FLG_0 = "0";

	/**
	 * 論理削除レポジトリ
	 */
	@Autowired
	private FutureMasterRepository futureMasterRepository;

	/** ログ管理ラッパー*/
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 実行メソッド
	 * @param 1アプリケーション実行時にdataテーブルに登録されたデータ
	 */
	public int execute(Map<String, List<DataEntity>> csvMap) throws Exception {
		final String METHOD_NAME = "execute";

		// 時間計測開始
		long startTime = System.nanoTime();

		// ログ出力
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// futureデータがあるか
		if (this.futureMasterRepository.findAll() == 0) {
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					String.format("データが存在しません（%s）", "future_master"));
			// endLog
			this.manageLoggerComponent.debugEndInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		}

		// app実行時に登録されたデータに関する更新
		if (csvMap != null && !csvMap.isEmpty()) {
			for (Map.Entry<String, List<DataEntity>> map : csvMap.entrySet()) {
				List<DataEntity> list = map.getValue();
				if (!list.isEmpty()) {
					String home = list.get(0).getHomeTeamName();
					String away = list.get(0).getAwayTeamName();
					if (home != null && away != null) {
						startFlgUpdate(home, away, STRAT_FLG_0);
					}
				}
			}
		} else {
			// 現在時刻前のフラグ更新
			startFlgUpdate(STRAT_FLG_0);
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 時間計測終了
		long endTime = System.nanoTime();
		long durationMs = (endTime - startTime) / 1_000_000; // ミリ秒に変換

		System.out.println("時間: " + durationMs);

		return 0;
	}

	/**
	 * 更新メソッド
	 */
	private synchronized void startFlgUpdate(String home, String away, String flg) {
		final String METHOD_NAME = "startFlgUpdate";
		String fillChar = setLoggerFillChar(
				home,
				away);
		FutureEntity entity = new FutureEntity();
		entity.setHomeTeamName(home);
		entity.setAwayTeamName(away);
		List<FutureEntity> findList = this.futureMasterRepository.findOnlyTeam(entity);
		if (!findList.isEmpty()) {
			int result = this.futureMasterRepository.updateStartFlg(
					findList.get(0).getSeq(), flg);
			if (result != 1) {
				String messageCd = "更新エラー";
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, result,
						String.format("home=%s, away=%s", home, away));
			}
			String messageCd = "更新件数";
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar, "更新件数: 1件");
		}
	}

	/**
	 * 更新メソッド
	 */
	private synchronized void startFlgUpdate(String flg) {
		final String METHOD_NAME = "startFlgUpdate";
		int result = -99;
		try {
			result = this.futureMasterRepository.updateFutureTimeFlg(
					flg);
		} catch (Exception e) {
			String messageCd = "更新エラー";
			this.manageLoggerComponent.createSystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					null,
					e);
		}

		String messageCd = "更新件数";
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, "更新件数: " + result + "件");
	}

	/**
	 * 埋め字設定
	 * @param home リーグ
	 * @param away チーム
	 * @return
	 */
	private String setLoggerFillChar(String home, String away) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("ホーム: " + home + ", ");
		stringBuilder.append("アウェー: " + away);
		return stringBuilder.toString();
	}

}
