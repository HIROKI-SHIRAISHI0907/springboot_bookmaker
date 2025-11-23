package dev.application.main.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.common.util.TableUtil;
import dev.application.domain.repository.bm.LogicFlgRepository;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.mng.analyze.bm_c001.CsvArtifactHelper;
import dev.mng.dto.ConditionData;

/**
 * 論理削除サービスクラス
 * @author shiraishitoshio
 *
 */
@Service
@Transactional
public class LogicFlgService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = LogicFlgService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = LogicFlgService.class.getSimpleName();

	/** 有効 */
	private static final String LOGIC_FLG_0 = "0";

	/** 無効 */
	private static final String LOGIC_FLG_1 = "1";

	/**
	 * 論理削除レポジトリ
	 */
	@Autowired
	private LogicFlgRepository logicFlgRepository;

	/**
	 * CsvArtifactHelperクラス
	 */
	@Autowired
	private CsvArtifactHelper CsvArtifactHelper;

	/** ログ管理ラッパー*/
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/** 国リーグリスト */
	private List<String> countryList;

	/** カテゴリリスト */
	private List<String> categoryList;

	/**
	 * 実行メソッド
	 */
	public int execute() throws Exception {
		final String METHOD_NAME = "execute";

		// 時間計測開始
		long startTime = System.nanoTime();

		// ログ出力
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 設定統計データの取得
		List<ConditionData> stat = setUpdateStat();

		// 更新用全テーブル
		this.countryList = TableUtil.getCountryList();
		this.categoryList = TableUtil.getCategoryList();

		// 更新(データが空の場合は制限をかけていないため全てフラグ0にする)
		logicFlgAllUpdate(LOGIC_FLG_1);
		if (!stat.isEmpty()) {
			for (ConditionData dto : stat) {
				String country = dto.getCountry();
				String league = dto.getLeague();
				logicFlgUpdate(country, league, LOGIC_FLG_0);
			}
		} else {
			logicFlgAllUpdate(LOGIC_FLG_0);
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
	 * 統計データ
	 */
	private List<ConditionData> setUpdateStat() {
		// 設定した国、リーグ情報のみ適用させる
		List<ConditionData> returnList = this.CsvArtifactHelper.statCondition(null);
		return returnList;
	}

	/**
	 * 更新メソッド
	 */
	private synchronized void logicFlgUpdate(String country, String league, String flg) {
		final String METHOD_NAME = "logicFlgUpdate";
		for (String table : this.countryList) {
			String fillChar = setLoggerFillChar(
					country,
					league,
					table);
			int result = this.logicFlgRepository.updateLogicFlgByCountryLeague(
					table, country, league, flg);
			if (result != 1) {
				String messageCd = "更新エラー";
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, result,
						String.format("country=%s, league=%s, table=%s", country, league, table));
			}
			String messageCd = "更新件数";
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar, "更新件数: 1件");
		}

		for (String table : this.categoryList) {
			String fillChar = setLoggerFillChar(
					country,
					league,
					table);
			int result = this.logicFlgRepository.updateLogicFlgByCategoryLike(
					table, country, league, flg);
			if (result != 1) {
				String messageCd = "更新エラー";
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, result,
						String.format("country=%s, league=%s, table=%s", country, league, table));
			}
			String messageCd = "更新件数";
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar, "更新件数: 1件");
		}
	}

	/**
	 * 更新メソッド
	 */
	private synchronized void logicFlgAllUpdate(String flg) {
		final String METHOD_NAME = "logicFlgAllUpdate";
		for (String table : this.countryList) {
			String fillChar = setLoggerFillChar(
					"",
					"",
					table);
			int result = this.logicFlgRepository.updateAllLogicFlg(
					table, flg);
			if (result != 1) {
				String messageCd = "更新エラー";
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						messageCd,
						1, result,
						String.format("table=%s", table));
			}
			String messageCd = "更新件数";
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar, "更新件数: 1件");
		}
	}

	/**
	 * 埋め字設定
	 * @param country 国
	 * @param league リーグ
	 * @param team チーム
	 * @return
	 */
	private String setLoggerFillChar(String country, String league, String table) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("国: " + country + ", ");
		stringBuilder.append("リーグ: " + league + ", ");
		stringBuilder.append("テーブル: " + table);
		return stringBuilder.toString();
	}

}
