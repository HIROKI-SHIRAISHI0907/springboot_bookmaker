package dev.application.analyze.bm_m097;

import java.util.ArrayList;
import java.util.List;

import dev.common.constant.UniairConst;
import dev.common.exception.SystemException;

/**
 * CSV更新情報の操作Util
 * @author shiraishitoshio
 *
 */
public class ExistsUpdCsvInfo {

	/**
	 * 生成禁止
	 */
	private ExistsUpdCsvInfo() {
	}

	/**
	 * 存在実行メソッド
	 * @return
	 */
	public static boolean exist() {
		int selectCount = -1;
		SqlMainLogic select = new SqlMainLogic();
		try {
			selectCount = select.executeCountSelect(null, UniairConst.BM_M097, null);
		} catch (Exception e) {
			throw new SystemException("", "", "", "updcsvinfo exist err");
		}

		if (selectCount > 0) {
			return true;
		}
		return false;
	}

	/**
	 * チェックメソッド
	 * @param country 国
	 * @param league リーグ
	 * @param table_id テーブルID
	 * @param remarks 備考
	 * @return
	 */
	public static List<List<String>> chk(String country, String league, String table_id, String remarks) {
		String[] selectList = new String[5];
		selectList[0] = "id";
		selectList[1] = "country";
		selectList[2] = "league";
		selectList[3] = "table_id";
		selectList[4] = "remarks";

		String where = "";
		if (country != null) {
			where += " country = '" + country + "'";
		}
		if (where.length() > 0) {
			where += " and ";
		}
		if (league != null) {
			where += " league = '" + league + "'";
		}
		if (where.length() > 0) {
			where += " and ";
		}
		if (table_id != null) {
			where += " table_id = '" + table_id + "'";
		}
		if (where.length() > 0) {
			where += " and ";
		}
		if (remarks != null) {
			where += " remarks = '" + remarks + "'";
		}

		// 末尾の "and" を削除
		if (where.endsWith(" and ")) {
			where = where.substring(0, where.length() - 5); // "and " を削除
		}

		List<List<String>> selectResultList = null;
		SqlMainLogic select = new SqlMainLogic();
		try {
			selectResultList = select.executeSelect(null, UniairConst.BM_M097,
					selectList, where, null, null);
		} catch (Exception e) {
			throw new SystemException("", "", "", "upd_csv_info chk err");
		}

		return selectResultList;
	}

	/**
	 * チェックメソッド(BM25曖昧検索用)
	 * @param country 国
	 * @param league リーグ
	 * @param remarks 備考
	 * @param home_away ホームorアウェー
	 * @return
	 */
	public static List<List<String>> chkVague25(String country, String league, String remarks, String home_away) {
		String[] selectList = new String[5];
		selectList[0] = "id";
		selectList[1] = "country";
		selectList[2] = "league";
		selectList[3] = "table_id";
		selectList[4] = "remarks";

		String where = "";
		if (country != null) {
			where += " country = '" + country + "'";
		}
		if (where.length() > 0) {
			where += " and ";
		}
		if (league != null) {
			where += " league = '" + league + "'";
		}
		if (where.length() > 0) {
			where += " and ";
		}
		where += " table_id = '" + UniairConst.BM_M025 + "'";
		if (where.length() > 0) {
			where += " and ";
		}
		if (remarks != null) {
			if ("home".equals(home_away)) {
				where += " remarks LIKE '" + remarks + "-%'";
			} else {
				where += " remarks LIKE '%-" + remarks + "'";
			}
		}

		// 末尾の "and" を削除
		if (where.endsWith(" and ")) {
			where = where.substring(0, where.length() - 5); // "and " を削除
		}

		List<List<String>> selectResultList = null;
		SqlMainLogic select = new SqlMainLogic();
		try {
			selectResultList = select.executeSelect(null, UniairConst.BM_M097,
					selectList, where, null, null);
		} catch (Exception e) {
			throw new SystemException("", "", "", "upd_csv_info chk err");
		}

		return selectResultList;
	}

	/**
	 * 登録メソッド
	 * @param country 国
	 * @param league リーグ
	 * @param table_id テーブルID
	 * @param remarks 備考
	 * @return
	 */
	public static void insert(String country, String league,
			String table_id, String remarks) {
		List<UpdCsvInfoEntity> inserCsvInfoEntities = new ArrayList<UpdCsvInfoEntity>();
		UpdCsvInfoEntity updCsvInfoEntity = new UpdCsvInfoEntity();
		updCsvInfoEntity.setCountry(country);
		updCsvInfoEntity.setLeague(league);
		updCsvInfoEntity.setTableId(table_id);
		updCsvInfoEntity.setRemarks(remarks);
		inserCsvInfoEntities.add(updCsvInfoEntity);
		CsvRegisterImpl csvRegisterImpl = new CsvRegisterImpl();
		try {
			csvRegisterImpl.executeInsert(UniairConst.BM_M097,
					inserCsvInfoEntities, 1, inserCsvInfoEntities.size());
		} catch (Exception e) {
			System.err.println("upd_csv_info insert err execute: " + e);
		}
	}

	/**
	 * Truncate
	 * @return
	 */
	public static void truncate() {
		SqlMainLogic select = new SqlMainLogic();
		try {
			select.executeTruncate(null, UniairConst.BM_M097);
		} catch (Exception e) {
			// 特に何もしない
		}
	}

}
