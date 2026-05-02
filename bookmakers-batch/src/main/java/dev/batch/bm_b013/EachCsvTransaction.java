package dev.batch.bm_b013;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.bm.CsvDetailManageBatchRepository;
import dev.batch.repository.bm.CsvInfoBatchRepository;
import dev.common.logger.ManageLoggerComponent;

/**
 * CSV関係の削除
 *
 * Transaction範囲:
 * - csv_detail_manage（season_yearがあるもの）
 * - csv_detail_manage と紐付く csv情報
 *
 * @author shiraishitoshio
 */
@Component
@Transactional(rollbackFor = Exception.class)
public class EachCsvTransaction {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = EachCsvTransaction.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = EachCsvTransaction.class.getName();

	/** csv_detail_manage */
	@Autowired
	private CsvDetailManageBatchRepository csvDetailManageBatchRepository;

	/**
	 * csv_detail_manage と紐付く csv情報テーブル削除用
	 */
	@Autowired
	private CsvInfoBatchRepository csvInfoBatchRepository;

	/** ログ管理 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 実行メソッド
	 *
	 * @throws Exception
	 */
	public void execute(TransactionDTO dto) throws Exception {
		final String METHOD_NAME = "execute";

		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		try {
			if (dto == null || dto.getCountryLeague() == null || dto.getCountryLeague().isEmpty()) {
				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						"対象countryLeagueが無いため、CSV削除処理をスキップします。");
				return;
			}

			int totalCountryLeague = 0;
			int totalCsvInfoDelete = 0;
			int totalCsvDetailDelete = 0;

			for (String countryLeague : dto.getCountryLeague()) {
				if (countryLeague == null || countryLeague.isBlank()) {
					continue;
				}

				totalCountryLeague++;

				String[] pair = splitCountryLeague(countryLeague);
				String country = pair[0];
				String league = pair[1];

				if (country.isBlank() || league.isBlank()) {
					this.manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							"country/league の分解に失敗したためスキップ: " + countryLeague);
					continue;
				}

				// data_category は「国: リーグ - ラウンドxx」形式を想定
				String dataCategoryPrefix = country + ": " + league;

				// season_year がある csv_detail_manage から csv_id を取得
				List<String> csvIds = csvDetailManageBatchRepository
						.findCsvIdsByDataCategoryPrefixAndSeasonYearExists(dataCategoryPrefix);

				if (csvIds == null || csvIds.isEmpty()) {
					this.manageLoggerComponent.debugInfoLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							"削除対象なし: " + dataCategoryPrefix);
					continue;
				}

				// 重複除去
				Set<String> distinctSet = new LinkedHashSet<>(csvIds);
				List<String> distinctCsvIds = new ArrayList<>(distinctSet);

				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						"削除対象csv_id取得: dataCategoryPrefix=" + dataCategoryPrefix
								+ ", csvIds.size=" + distinctCsvIds.size());

				// 先に紐付きCSV情報を削除
				int csvInfoDeleteCount = csvInfoBatchRepository.deleteByCsvIds(distinctCsvIds);

				// 次に csv_detail_manage を削除
				int csvDetailDeleteCount = csvDetailManageBatchRepository.deleteByCsvIds(distinctCsvIds);

				totalCsvInfoDelete += csvInfoDeleteCount;
				totalCsvDetailDelete += csvDetailDeleteCount;

				this.manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						"削除完了: dataCategoryPrefix=" + dataCategoryPrefix
								+ ", csvInfoDeleteCount=" + csvInfoDeleteCount
								+ ", csvDetailDeleteCount=" + csvDetailDeleteCount);
			}

			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					"EachCsvTransaction 完了"
							+ ", 対象countryLeague数=" + totalCountryLeague
							+ ", csvInfo削除件数=" + totalCsvInfoDelete
							+ ", csvDetailManage削除件数=" + totalCsvDetailDelete);

		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					"CSV削除処理で例外発生", e);
			throw e;
		} finally {
			this.manageLoggerComponent.debugEndInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		}
	}

	/**
	 * "country-league" を country / league に分解
	 *
	 * league 側に "-" が含まれる可能性があるので、最初の "-" だけで分割する
	 */
	private String[] splitCountryLeague(String countryLeague) {
		if (countryLeague == null || countryLeague.isBlank()) {
			return new String[] { "", "" };
		}

		int idx = countryLeague.indexOf('-');
		if (idx < 0) {
			return new String[] { "", "" };
		}

		String country = countryLeague.substring(0, idx).trim();
		String league = countryLeague.substring(idx + 1).trim();

		return new String[] { country, league };
	}
}
