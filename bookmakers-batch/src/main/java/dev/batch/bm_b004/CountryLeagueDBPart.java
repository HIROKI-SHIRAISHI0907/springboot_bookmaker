package dev.batch.bm_b004;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.master.CountryLeagueMasterBatchRepository;
import dev.common.entity.CountryLeagueMasterEntity;
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
public class CountryLeagueDBPart {

    /** 実行ログに出力するプロジェクト識別子（クラスの配置パス）。 */
    private static final String PROJECT_NAME = CountryLeagueDBPart.class.getProtectionDomain()
            .getCodeSource().getLocation().getPath();

    /** 実行ログに出力するクラス名。 */
    private static final String CLASS_NAME = CountryLeagueDBPart.class.getName();

    /** 運用向けのエラーコード。 */
    private static final String ERROR_CODE = "DBPART";

    /** 国リーグマスタの参照・更新を行うリポジトリ。 */
    @Autowired
    private CountryLeagueMasterBatchRepository countryLeagueMasterRepository;

    /** バッチ共通ログ出力を行う。 */
    @Autowired
    private ManageLoggerComponent manageLoggerComponent;

    /**
     * 操作メソッド
     * @param csvRows
     * @return
     */
    @Transactional
	public boolean dbOperation(List<CountryLeagueMasterEntity> csvRows) {
		final String METHOD_NAME = "dbOperation";
		try {
			if (csvRows == null || csvRows.isEmpty()) {
				manageLoggerComponent.debugInfoLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, null,
						"teamData_XX.csv is empty. nothing to do.");
				return true;
			}

			// 0) バッチキー確定（同一 country & league 前提）
			String country = csvRows.get(0).getCountry();
			String league = csvRows.get(0).getLeague();

			if (isBlank(country) || isBlank(league)) {
				manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, null,
						"invalid batch header: country/league is blank (country: " + country
						+ ", league: " + league + ")");
				return false;
			}

			// 混在チェック（同一国同一リーグで来る想定だが保険）
			for (CountryLeagueMasterEntity r : csvRows) {
				if (!safeEq(country, r.getCountry()) || !safeEq(league, r.getLeague())) {
					manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, null,
							"mixed group row detected (country/league mismatch). expected="
									+ country + "/" + league + " actual="
									+ r.getCountry() + "/" + r.getLeague());
					return false;
				}
			}

			// 1) DB：対象リーグ（削除差分用）
			List<CountryLeagueMasterEntity> dbLeagueRows =
					countryLeagueMasterRepository.findActiveByCountryAndLeague(country, league);

			// 2) DB：同一国（link一致 / 改名救済用）
			List<CountryLeagueMasterEntity> dbCountryRows =
					countryLeagueMasterRepository.findActiveByCountry(country);

			// 3) DB インデックス（link & teamKey）
			Map<String, CountryLeagueMasterEntity> dbByLink = new HashMap<>();
			Map<String, CountryLeagueMasterEntity> dbByTeamKey = new HashMap<>();
			Map<String, Integer> dbTeamKeyCount = new HashMap<>(); // 同名複数の曖昧検知

			for (CountryLeagueMasterEntity db : dbCountryRows) {
				if (!isBlank(db.getLink())) {
					dbByLink.put(db.getLink(), db);
				}

				String tk = teamKey(db.getTeam());
				if (!isBlank(tk)) {
					dbByTeamKey.putIfAbsent(tk, db);
					dbTeamKeyCount.put(tk, dbTeamKeyCount.getOrDefault(tk, 0) + 1);
				}
			}

			// 4) CSV：このリーグスナップショットに存在する teamKey 集合（削除判定用）
			Set<String> presentTeamKeys = new HashSet<>();
			for (CountryLeagueMasterEntity csv : csvRows) {
				String tk = teamKey(csv.getTeam());
				if (!isBlank(tk)) {
					presentTeamKeys.add(tk);
				}
			}

			// 5) CSV → Insert / Update
			for (CountryLeagueMasterEntity csv : csvRows) {
				if (isBlank(csv.getTeam())) {
					manageLoggerComponent.debugWarnLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE,
							"skip row: team is blank");
					continue;
				}

				CountryLeagueMasterEntity hit = null;

				// (A) link一致（最優先：改名でも追える）
				if (!isBlank(csv.getLink())) {
					hit = dbByLink.get(csv.getLink());
				}

				// (B) country + teamKey一致（link変更救済）
				if (hit == null) {
					String tk = teamKey(csv.getTeam());
					if (!isBlank(tk)) {
						int cnt = dbTeamKeyCount.getOrDefault(tk, 0);
						if (cnt == 1) {
							hit = dbByTeamKey.get(tk);
						} else if (cnt > 1) {
							// 曖昧 → 誤更新防止のため Insert に倒す
							manageLoggerComponent.debugWarnLog(
									PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE,
									"ambiguous team name in same country. insert as new. country="
											+ country + " team=" + csv.getTeam());
						}
					}
				}

				if (hit != null) {
					// 差分があるときだけ UPDATE
					boolean needUpdate =
							!safeEq(hit.getLeague(), csv.getLeague()) ||
							!safeEq(hit.getTeam(), csv.getTeam()) ||
							!safeEq(hit.getLink(), csv.getLink());

					if (needUpdate) {
						int upd = countryLeagueMasterRepository.updateById(
								hit.getLeague(), csv.getTeam(),
								csv.getLink(), Integer.parseInt(hit.getId()));
						if (upd != 1) {
							manageLoggerComponent.debugErrorLog(
									PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, null,
									"update failed id=" + hit.getId());
							return false;
						}
					}
				} else {
					// INSERT
					int ins = countryLeagueMasterRepository.insert(csv);
					if (ins != 1) {
						manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, null,
								"insert failed country=" + csv.getCountry()
										+ " league=" + csv.getLeague()
										+ " team=" + csv.getTeam()
										+ " link=" + csv.getLink());
						return false;
					}
				}
			}

			// 6) LOGICAL DELETE は「更新後のDB状態」で判定しないと、改名（team変更）で誤って削除される。
			//    Step1で取った dbLeagueRows は “更新前スナップショット” なので、ここで取り直す。
			dbLeagueRows = countryLeagueMasterRepository.findActiveByCountryAndLeague(country, league);

			// 6) LOGICAL DELETE（この国×このリーグの差分）
			for (CountryLeagueMasterEntity db : dbLeagueRows) {
				String tk = teamKey(db.getTeam());
				if (isBlank(tk)) {
					continue;
				}
				if (!presentTeamKeys.contains(tk)) {
					int del = countryLeagueMasterRepository.logicalDeleteById(
							Integer.parseInt(db.getId()));
					if (del != 1) {
						manageLoggerComponent.debugWarnLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE,
								"logical delete maybe already deleted. id=" + db.getId());
					}
				}
			}

			return true;

		} catch (Exception e) {
			manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, ERROR_CODE, e);
			return false;
		}
	}

	/** チーム名の正規化キー（最低限） */
	private String teamKey(String team) {
		if (team == null) {
			return "";
		}
		return team.trim()
				.replace("　", " ")
				.replaceAll("\\s+", "")
				.toLowerCase();
	}

	/** 空白 */
	private boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	/** 同一文字列か */
	private boolean safeEq(String a, String b) {
		if (a == null) return b == null;
		return a.equals(b);
	}
}
