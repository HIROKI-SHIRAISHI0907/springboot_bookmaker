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

		// country_league_masterのdel_flg=1に変更
		int delCLResultSum = 0;
		try {
			int delCLResult = countryLeagueMasterBatchRepository.logicalDeleteByCountryLeague(null, null);
			delCLResultSum += delCLResult;
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e);
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"country_league_master_delete_sum=" + delCLResultSum);

		// Dataテーブル該当データ削除
		DataEntity entity = new DataEntity();

		int delDResultSum = 0;
		try {
			int delDResult = bookDataRepository.deleteByDataCategory(entity);
			delDResultSum += delDResult;
		} catch (Exception e) {
			this.manageLoggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e);
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				"data_delete_sum=" + delDResultSum);

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
	}

}
