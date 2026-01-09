package dev.batch.bm_b003;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.master.CountryLeagueSeasonMasterRepository;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * country_league_season_masterの登録・更新・論理削除（すでに対象マスタにデータが存在する前提）
 * @author shiraishitoshio
 *
 */
@Component
public class CountryLeagueSeasonDBPart {

	/** 実行ログに出力するプロジェクト識別子（クラスの配置パス）。 */
	private static final String PROJECT_NAME = CountryLeagueSeasonDBPart.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** 実行ログに出力するクラス名。 */
	private static final String CLASS_NAME = CountryLeagueSeasonDBPart.class.getSimpleName();

	/** 運用向けのエラーコード。 */
	private static final String ERROR_CODE = "DBPART";

	/** 国リーグシーズンマスタの参照・更新を行うリポジトリ。 */
	@Autowired
	private CountryLeagueSeasonMasterRepository countryLeagueSeasonMasterRepository;

	/** バッチ共通ログ出力を行う。 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 国リーグシーズンマスタ（終了日・関連CSV）を登録・更新・論理削除する。
	 * 取得したseason_data.csvデータが
	 * <p>
	 * 同一国異なるリーグ異なるパスor全て異なるなら登録
	 * </p>
	 * <p>
	 * 同一国同一パスかつリーグ名が異なるor同一国同一リーグかつ異なるパスなら登録
	 * また古い方のリーグ名は論理削除
	 * </p>
	 * <p>
	 * 同一国同一パス同一リーグ名なら更新
	 * </p>
	 * @param csvRows CSVデータ列
	 */
	@Transactional
	public boolean dbOperation(List<CountryLeagueSeasonMasterEntity> csvRows) {
		final String METHOD_NAME = "dbOperation";
		try {
			if (csvRows == null || csvRows.isEmpty()) {
				manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, null,
						"season_data.csv is empty. nothing to do.");
				return true;
			}

			// 2) 同一country+pathの「現在DB側のリーグ名」をキャッシュ（DBアクセス削減）
			//    key = country + "\t" + path
			Map<String, String> dbLeagueByCountryPath = new HashMap<>();

			for (CountryLeagueSeasonMasterEntity row : csvRows) {
				String country = row.getCountry();
				String league = row.getLeague();
				String path = row.getPath(); // ← entityにpathがある想定（なければ該当getter名に変更）

				if (isBlank(country) || isBlank(league) || isBlank(path)) {
					manageLoggerComponent.debugWarnLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE,
							"skip invalid row (country/league/path is blank)");
					continue;
				}

				// A) 同一国×同一リーグ が既にあるか？
				List<CountryLeagueSeasonMasterEntity> dbSameCountryLeague = countryLeagueSeasonMasterRepository
						.findByCountryAndLeague(country, league);

				if (dbSameCountryLeague != null && !dbSameCountryLeague.isEmpty()) {
					// A-1) 同一国×同一リーグ×同一パス → 更新
					if (path.equals(dbSameCountryLeague.get(0).getPath())) {
						int upd = countryLeagueSeasonMasterRepository.updateByCountryLeague(row);
						if (upd != 1) {
							manageLoggerComponent.debugErrorLog(
									PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, null,
									"update failed (country=" + country + ", league=" + league + ")");
							return false;
						}
						continue;
					}

					// A-2) 同一国×同一リーグ×パス違い → 登録 + 古い方を論理削除
					int ins = countryLeagueSeasonMasterRepository.insert(row);
					if (ins != 1) {
						manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, null,
								"insert failed (country=" + country + ", league=" + league + ", path=" + path + ")");
						return false;
					}

					int del = countryLeagueSeasonMasterRepository.logicalDeleteByCountryLeaguePath(
							country, league, dbSameCountryLeague.get(0).getPath());
					if (del != 1) {
						manageLoggerComponent.debugWarnLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE,
								"logicalDelete old path maybe not found (country=" + country + ", league=" + league +
										", oldPath=" + dbSameCountryLeague.get(0).getPath() + ")");
						// ここは「消えなくても継続」。
					}
					continue;
				}

				// B) 同一国×同一パス のDB側リーグがあるか？（リーグ名変更検知）
				String key = country + "\t" + path;
				String dbLeague = dbLeagueByCountryPath.get(key);
				if (dbLeague == null) {
					List<CountryLeagueSeasonMasterEntity>  dbSameCountryPath = countryLeagueSeasonMasterRepository
							.findByCountryAndPath(country, path);
					dbLeague = (dbSameCountryPath == null || dbSameCountryPath.isEmpty()) ? "" : dbSameCountryPath.get(0).getLeague();
					dbLeagueByCountryPath.put(key, dbLeague);
				}

				if (!isBlank(dbLeague) && !dbLeague.equals(league)) {
					// B-1) 同一国×同一パス だがリーグ名が違う → 登録 + 古いリーグ名を論理削除
					int ins = countryLeagueSeasonMasterRepository.insert(row);
					if (ins != 1) {
						manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, null,
								"insert failed (country=" + country + ", league=" + league + ", path=" + path + ")");
						return false;
					}

					int del = countryLeagueSeasonMasterRepository.logicalDeleteByCountryLeaguePath(
							country, dbLeague, path);
					if (del != 1) {
						manageLoggerComponent.debugWarnLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE,
								"logicalDelete old league maybe not found (country=" + country +
										", oldLeague=" + dbLeague + ", path=" + path + ")");
					}

					// キャッシュ更新（同一country+pathは今後は新league扱い）
					dbLeagueByCountryPath.put(key, league);
					continue;
				}

				// C) 同一国で league/path が全部違う（またはDBにcountry+pathが無い）→ 登録
				int ins = countryLeagueSeasonMasterRepository.insert(row);
				if (ins != 1) {
					manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, null,
							"insert failed (country=" + country + ", league=" + league + ", path=" + path + ")");
					return false;
				}

				// キャッシュ更新（今入れたものが “現リーグ”）
				dbLeagueByCountryPath.put(key, league);
			}

			return true;

		} catch (Exception e) {
			manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
			return false;
		}
	}

	/**
	 * 空白考慮メソッド
	 * @param s
	 * @return
	 */
	private boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

}
