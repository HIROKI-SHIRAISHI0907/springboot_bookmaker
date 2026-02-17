package dev.batch.bm_b007;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.master.AllLeagueMasterBatchRepository;
import dev.common.entity.AllLeagueMasterEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * country_league_master の登録・更新・論理削除
 *
 * 前提:
 * - csvRows は「同一 country & 同一 league」のスナップショット（複数チーム）として渡される
 * - delFlg = '0' が現役、'1' が論理削除
 *
 * 最小パターン:
 * 1) Update: link一致 > (country+teamKey一致) で同一判定できたら差分更新
 * 2) Insert: 同一判定できなければ新規登録
 * 3) Logical Delete: 同一 country+league のスナップショット差分で論理削除
 */
@Component
public class AllLeagueDBPart {

	/** 実行ログに出力するプロジェクト識別子（クラスの配置パス）。 */
	private static final String PROJECT_NAME = AllLeagueDBPart.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** 実行ログに出力するクラス名。 */
	private static final String CLASS_NAME = AllLeagueDBPart.class.getName();

	/** 運用向けのエラーコード。 */
	private static final String ERROR_CODE = "DBPART";

	/** 国リーグマスタの参照・更新を行うリポジトリ。 */
	@Autowired
	private AllLeagueMasterBatchRepository allLeagueMasterBatchRepository;

	/** バッチ共通ログ出力を行う。 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 操作メソッド
	 * @param csvRows
	 * @return
	 */
	@Transactional
	public boolean dbOperation(List<AllLeagueMasterEntity> csvRows) {
		final String METHOD_NAME = "dbOperation";
		try {
			if (csvRows == null || csvRows.isEmpty()) {
				manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, null,
						"all_league_master.csv is empty. nothing to do.");
				return true;
			}

			// 1) DB：対象リーグ検索し、mapping
			List<AllLeagueMasterEntity> dbCountryLeagueRows = allLeagueMasterBatchRepository.findData();
			Map<String, List<String>> countryLeagueMap = dbCountryLeagueRows.stream()
					.collect(Collectors.groupingBy(
							AllLeagueMasterEntity::getCountry,
							Collectors.mapping(AllLeagueMasterEntity::getLeague, Collectors.toList())));

			// 2) DB：論理削除対象リーグ精査
			// 2) DB：論理削除対象リーグ精査
			for (AllLeagueMasterEntity getCsvRow : csvRows) {
			    // 3) バッチキー確定（同一 country & league 前提）
			    String country = getCsvRow.getCountry();
			    String league = getCsvRow.getLeague();

			    if (isBlank(country) || isBlank(league)) {
			        manageLoggerComponent.debugErrorLog(
			                PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, null,
			                "invalid batch header: country/league is blank (country: " + country
			                        + ", league: " + league + ")");
			        continue;
			    }

			    // 同一国,リーグが存在する時,論理削除を0（最新の国リーグ）
			    AllLeagueMasterEntity existsLeague =
			            allLeagueMasterBatchRepository.findByCountryLeague(country, league);

			    if (existsLeague != null) {
			        int update = allLeagueMasterBatchRepository
			                .reviveById(Integer.parseInt(existsLeague.getId()));
			        if (update == 0) {
			            manageLoggerComponent.debugErrorLog(
			                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, null,
			                    "update error: (country: " + country + ", league: " + league + ")");
			        }
			        removeMap(countryLeagueMap, country, league);
			    } else {
			        AllLeagueMasterEntity entity = new AllLeagueMasterEntity();
			        entity.setCountry(country);
			        entity.setLeague(league);
			        entity.setLogicFlg("0");
			        entity.setDispFlg("0");

			        int insert = allLeagueMasterBatchRepository.insert(entity);
			        if (insert == 0) {
			            manageLoggerComponent.debugErrorLog(
			                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, null,
			                    "insert error: (country: " + country + ", league: " + league + ")");
			        }
			        removeMap(countryLeagueMap, country, league);
			    }
			}

			// ★ここからが「旧名のリーグ」を論理削除（logicFlg=1）にする処理（CSVループの外！）
			for (Map.Entry<String, List<String>> entry : countryLeagueMap.entrySet()) {
			    String countries = entry.getKey();
			    List<String> leagues = entry.getValue();

			    for (String lg : leagues) {
			        if (isBlank(lg)) continue;

			        // country+league でID取得してから logicalDeleteById(id)
			        AllLeagueMasterEntity oldLeague =
			                allLeagueMasterBatchRepository.findByCountryLeague(countries, lg);

			        if (oldLeague == null || isBlank(oldLeague.getId())) {
			            manageLoggerComponent.debugErrorLog(
			                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, null,
			                    "old league not found for logical delete: (country: " + countries + ", league: " + lg + ")");
			            continue;
			        }

			        int update = allLeagueMasterBatchRepository
			                .logicalDeleteById(Integer.parseInt(oldLeague.getId()));

			        if (update == 0) {
			            manageLoggerComponent.debugErrorLog(
			                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, null,
			                    "logicFlg update error (old league): (country: " + countries + ", league: " + lg + ")");
			        }
			    }
			}
			return true;

		} catch (Exception e) {
			manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
			return false;
		}
	}

	/** 空白 */
	private boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	/** CSVに存在した country+league は「旧名候補から除外」する */
	private void removeMap(
			Map<String, List<String>> countryLeagueMap,
			String country, String league) {
		// CSVに存在した country+league は「旧名候補から除外」する
		List<String> leaguesInDb = countryLeagueMap.get(country);
		if (leaguesInDb != null) {
			leaguesInDb.remove(league);
			if (leaguesInDb.isEmpty()) {
				countryLeagueMap.remove(country);
			}
		}
	}

}
