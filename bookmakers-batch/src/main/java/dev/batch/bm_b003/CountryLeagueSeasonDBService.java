package dev.batch.bm_b003;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import dev.batch.repository.master.CountryLeagueSeasonMasterRepository;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M029シーズンデータDB管理部品
 * @author shiraishitoshio
 *
 */
@Component
public class CountryLeagueSeasonDBService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CountryLeagueSeasonDBService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CountryLeagueSeasonDBService.class.getSimpleName();

	/** 有効フラグ */
	private static final String VALID_FLG_0 = "0";

	/** CountryLeagueSeasonMasterRepositoryレポジトリクラス */
	@Autowired
	private CountryLeagueSeasonMasterRepository countryLeagueSeasonMasterRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * チェックメソッド
	 * @param chkEntities
	 */
	public List<CountryLeagueSeasonMasterEntity> selectInBatch(List<CountryLeagueSeasonMasterEntity> chkEntities
			) {
		final String METHOD_NAME = "selectInBatch";
		List<CountryLeagueSeasonMasterEntity> entities = new ArrayList<CountryLeagueSeasonMasterEntity>();
		for (CountryLeagueSeasonMasterEntity entity : chkEntities) {
			try {
				int count = this.countryLeagueSeasonMasterRepository.findDataCount(entity);
				if (count == 0) {
					entities.add(entity);
				}
			} catch (Exception e) {
				String messageCd = "DB接続エラー";
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
				throw e;
			}
		}
		return entities;
	}

	/**
	 * 登録メソッド
	 * @param insertEntities
	 */
	public int insertInBatch(List<CountryLeagueSeasonMasterEntity> insertEntities) {
		final String METHOD_NAME = "insertInBatch";
		final int BATCH_SIZE = 100;
		for (int i = 0; i < insertEntities.size(); i += BATCH_SIZE) {
			int end = Math.min(i + BATCH_SIZE, insertEntities.size());
			List<CountryLeagueSeasonMasterEntity> batch = insertEntities.subList(i, end);
			for (CountryLeagueSeasonMasterEntity entity : batch) {
				try {
					// 有効フラグを埋める
					entity.setValidFlg(VALID_FLG_0);
					// シーズン年を元にシーズン開始日と終了日を埋める
					String[] years = convertSeasonYear(entity.getSeasonYear());
					String startDate = buildDate(years[0], entity.getStartSeasonDate());
					String endDate   = buildDate(years[1], entity.getEndSeasonDate());
					entity.setStartSeasonDate(startDate);
					entity.setEndSeasonDate(endDate);
					int result = this.countryLeagueSeasonMasterRepository.insert(entity);
					if (result != 1) {
						String messageCd = "新規登録エラー";
						this.manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null);
						return 9;
					}
				} catch (DuplicateKeyException e) {
					String messageCd = "登録済みです";
					this.manageLoggerComponent.debugWarnLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd);
					// 重複は特に例外として出さない
					continue;
				} catch (Exception e) {
					String messageCd = "システムエラー";
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e);
					return 9;
				}
			}
		}
		String messageCd = "BM_M028 登録件数: " + insertEntities.size();
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd);
		return 0;
	}

	/**
	 * 年変換
	 * @param year
	 * @return
	 */
	private String[] convertSeasonYear(String year) {
		// 戻り値[0] = 開始年, [1] = 終了年
		if (year == null || year.isEmpty()) {
			throw new IllegalArgumentException("seasonYear is null or empty");
		}

		if (year.length() == 9 && year.contains("/")) {
			// 2025/2026
			String[] years = year.split("/");
			return new String[] { years[0], years[1] };
		}

		if (year.length() == 4) {
			// 2025
			return new String[] { year, year };
		}

		throw new IllegalArgumentException("Invalid seasonYear format: " + year);
	}

	/**
	 * 日付構築
	 * @param year
	 * @param seasonDate
	 * @return
	 */
	private String buildDate(String year, String seasonDate) {
	    if (seasonDate == null || seasonDate.isEmpty()) {
	        return null;
	    }

	    // 例: "4.10." , ".24.08" , "24.08" などから「数値」を抜き出す
	    String[] raw = seasonDate.split("\\.");
	    List<String> nums = new ArrayList<>();
	    for (String s : raw) {
	        if (s != null) {
	            s = s.trim();
	            if (!s.isEmpty()) nums.add(s);
	        }
	    }

	    if (nums.size() < 2) {
	        throw new IllegalArgumentException("Invalid seasonDate format: " + seasonDate);
	    }

	    // まずは「日.月」 or 「月.日」判定
	    int a = Integer.parseInt(nums.get(0));
	    int b = Integer.parseInt(nums.get(1));

	    int day;
	    int month;

	    // ".24.08" は「日=24, 月=8」なので a(24) が 12より大きければ日と判断
	    if (a > 12) {
	        day = a;
	        month = b;
	    } else if (b > 12) {
	        // "08.24" のように b が 12超なら b が日
	        month = a;
	        day = b;
	    } else {
	        // 両方 12以下は曖昧なので、従来形式 "4.10." を優先（=月.日）
	        month = a;
	        day = b;
	    }

	    String monthStr = String.format("%02d", month);
	    String dayStr   = String.format("%02d", day);

	    return year + "-" + monthStr + "-" + dayStr;
	}


}
