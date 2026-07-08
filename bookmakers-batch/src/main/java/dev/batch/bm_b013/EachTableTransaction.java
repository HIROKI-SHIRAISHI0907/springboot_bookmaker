package dev.batch.bm_b013;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.bm.BookDataRepository;
import dev.batch.repository.master.CountryLeagueMasterBatchRepository;
import dev.batch.repository.master.FutureMasterRepository;
import dev.batch.repository.master.InitialMasterCsvRepository;
import dev.batch.repository.master.PointSettingMasterBatchRepository;
import dev.common.constant.MasterNameConstant;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;

/**
 * テーブル関係の削除
 * @author shiraishitoshio
 *
 */
@Component
@Transactional
public class EachTableTransaction {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = EachTableTransaction.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = EachTableTransaction.class.getName();

	/** InitialMasterCsvRepository */
	@Autowired
	private InitialMasterCsvRepository initialMasterCsvRepository;

	/** CountryLeagueMasterBatchRepository */
	@Autowired
	private CountryLeagueMasterBatchRepository countryLeagueMasterBatchRepository;

	/** FutureMasterRepository */
	@Autowired
	private FutureMasterRepository futureMasterRepository;

	/** PointSettingMasterBatchRepository */
	@Autowired
	private PointSettingMasterBatchRepository pointSettingMasterBatchRepository;

	/** BookDataRepository */
	@Autowired
	private BookDataRepository bookDataRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 実行
	 */
	public void execute(TransactionDTO dto) {
		final String METHOD_NAME = "execute";
		// ログ出力
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// initial_reading_csv_masterの対象レコード削除
		for (String countryLeague : dto.getCountryLeague()) {
			String[] pair = splitCountryLeague(countryLeague);
			String country = pair[0];
			String league = pair[1];

			int delINResultSum = 0;
			List<String> masterList = new ArrayList<String>();
			masterList.add(MasterNameConstant.COUNTRY_LEAGUE_SEASON_MASTER);
			masterList.add(MasterNameConstant.COUNTRY_LEAGUE_MASTER);
			for (String master : masterList) {
				try {
					int delINResult = initialMasterCsvRepository.delete(country, league, master);
					delINResultSum += delINResult;
				} catch (Exception e) {
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e);
					throw e;
				}
			}

			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					"initial_master_csv_delete_sum=" + delINResultSum);
		}

		for (String countryLeague : dto.getCountryLeague()) {
			String[] pair = splitCountryLeague(countryLeague);
			String country = pair[0];
			String league = pair[1];

			// country_league_masterのdel_flg=1に変更
			int delCLResultSum = 0;
			try {
				int delCLResult = countryLeagueMasterBatchRepository.logicalDeleteByCountryLeague(country, league);
				delCLResultSum += delCLResult;
			} catch (Exception e) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e);
				throw e;
			}

			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					"country_league_master_delete_sum=" + delCLResultSum);
		}

		for (String countryLeague : dto.getCountryLeague()) {
			String[] pair = splitCountryLeague(countryLeague);
			String country = pair[0];
			String league = pair[1];

			// point_setting_master
			int delPTResultSum = 0;
			try {
				int delPTResult = pointSettingMasterBatchRepository.delete(country, league);
				delPTResultSum += delPTResult;
			} catch (Exception e) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e);
				throw e;
			}

			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					"point_setting_master_delete_sum=" + delPTResultSum);
		}


		for (String countryLeague : dto.getCountryLeague()) {
			String[] pair = splitCountryLeague(countryLeague);
			String country = pair[0];
			String league = pair[1];
			// data_category は「国: リーグ - ラウンドxx」形式を想定
			String dataCategoryPrefix = country + ": " + league;

			// Futureテーブル該当データ削除
			int delFResultSum = 0;
			try {
				int delFResult = futureMasterRepository.deleteByDataCategory(dataCategoryPrefix);
				delFResultSum += delFResult;
			} catch (Exception e) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e);
				throw e;
			}

			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					"future_master_delete_sum=" + delFResultSum);
		}

		for (String countryLeague : dto.getCountryLeague()) {
			String[] pair = splitCountryLeague(countryLeague);
			String country = pair[0];
			String league = pair[1];
			// data_category は「国: リーグ - ラウンドxx」形式を想定
			String dataCategoryPrefix = country + ": " + league;

			// Dataテーブル該当データ削除
			int delDResultSum = 0;
			try {
				int delDResult = bookDataRepository.deleteByDataCategory(dataCategoryPrefix);
				delDResultSum += delDResult;
			} catch (Exception e) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e);
				throw e;
			}

			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					"data_delete_sum=" + delDResultSum);
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
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
