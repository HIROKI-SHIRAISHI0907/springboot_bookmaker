package dev.batch.bm_b013;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.bm.BookDataRepository;
import dev.batch.repository.master.CountryLeagueMasterBatchRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.DataEntity;
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

	/** CountryLeagueMasterBatchRepository */
	@Autowired
	private CountryLeagueMasterBatchRepository countryLeagueMasterBatchRepository;

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
			// data_category は「国: リーグ - ラウンドxx」形式を想定
			String dataCategoryPrefix = country + ": " + league;

			// Dataテーブル該当データ削除
			DataEntity entity = new DataEntity();
			entity.setDataCategory(dataCategoryPrefix);
			entity.setHomeTeamName(METHOD_NAME);
			entity.setAwayTeamName(METHOD_NAME);

			int delDResultSum = 0;
			try {
				int delDResult = bookDataRepository.deleteByDataCategory(entity);
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
