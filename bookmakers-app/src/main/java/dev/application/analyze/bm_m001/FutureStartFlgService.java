package dev.application.analyze.bm_m001;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.master.FutureMasterRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.DataEntity;
import dev.common.entity.FutureEntity;
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
	private static final String CLASS_NAME = FutureStartFlgService.class.getName();

	/** 試合開始有効 */
	private static final String STRAT_FLG_1 = "1";

	/**
	 * 未来データレポジトリ
	 */
	@Autowired
	private FutureMasterRepository futureMasterRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 実行メソッド
	 * @param csvMap アプリケーション実行時にdataテーブルに登録されたデータ
	 * @return 処理結果
	 * @throws Exception
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
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					String.format("データが存在しません（%s）", "future_master"));

			// endLog
			this.manageLoggerComponent.debugEndInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME);

			return 0;
		}

		// app実行時に登録されたデータに関する更新
		if (csvMap != null && !csvMap.isEmpty()) {
			for (Map.Entry<String, List<DataEntity>> map : csvMap.entrySet()) {

				List<DataEntity> list = map.getValue();

				if (list == null || list.isEmpty()) {
					continue;
				}

				// 1つのCSVにつき、有効な試合データを1件更新
				for (DataEntity dto : list) {

					if (dto == null) {
						continue;
					}

					String home = dto.getHomeTeamName();
					String away = dto.getAwayTeamName();

					if (home != null && away != null) {
						startFlgUpdate(home, away, STRAT_FLG_1);

						// 1つのCSVにつき1試合を更新
						break;
					}
				}
			}
		}

		/*
		 * future_timeを過ぎた試合は、
		 * CSVの有無に関係なくstart_flg=1に更新する。
		 */
		startFlgUpdate(STRAT_FLG_1);

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 時間計測終了
		long endTime = System.nanoTime();
		long durationMs = (endTime - startTime) / 1_000_000;

		System.out.println("時間: " + durationMs);

		return 0;
	}

	/**
	 * チーム名を指定して試合開始フラグを更新する。
	 *
	 * @param home ホームチーム
	 * @param away アウェーチーム
	 * @param flg 試合開始フラグ
	 * @throws Exception 更新失敗時
	 */
	private synchronized void startFlgUpdate(
			String home,
			String away,
			String flg) throws Exception {

		final String METHOD_NAME = "startFlgUpdate";

		String fillChar = setLoggerFillChar(home, away);

		FutureEntity entity = new FutureEntity();
		entity.setHomeTeamName(home);
		entity.setAwayTeamName(away);

		List<FutureEntity> findList =
				this.futureMasterRepository.findOnlyTeam(entity);

		String messageCdLog = MessageCdConst.MCD00099I_LOG;

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME,
				CLASS_NAME,
				METHOD_NAME,
				messageCdLog,
				fillChar,
				"更新対象: " + findList.size() + "件");

		if (!findList.isEmpty()) {

			int result = this.futureMasterRepository.updateStartFlg(
					findList.get(0).getSeq(),
					flg);

			if (result != 1) {

				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						messageCdLog,
						fillChar + "試合予定時間: "
								+ findList.get(0).getFutureTime());

				String messageCd =
						MessageCdConst.MCD00008E_UPDATE_FAILED;

				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME,
						CLASS_NAME,
						METHOD_NAME,
						messageCd,
						null);

				throw new Exception();
			}

			String messageCd =
					MessageCdConst.MCD00006I_UPDATE_SUCCESS;

			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					fillChar,
					"更新件数: 1件");
		}
	}

	/**
	 * 現在時刻を過ぎた試合の試合開始フラグを更新する。
	 *
	 * @param flg 試合開始フラグ
	 * @throws Exception 更新失敗時
	 */
	private synchronized void startFlgUpdate(String flg) throws Exception {

		final String METHOD_NAME = "startFlgUpdate";

		int result =
				this.futureMasterRepository.updateFutureTimeFlg(flg);

		String messageCd =
				MessageCdConst.MCD00006I_UPDATE_SUCCESS;

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME,
				CLASS_NAME,
				METHOD_NAME,
				messageCd,
				"更新件数: " + result + "件");
	}

	/**
	 * 埋め字設定
	 *
	 * @param home ホーム
	 * @param away アウェー
	 * @return ログ表示文字列
	 */
	private String setLoggerFillChar(String home, String away) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("ホーム: " + home + ", ");
		stringBuilder.append("アウェー: " + away);
		return stringBuilder.toString();
	}

}
