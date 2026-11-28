package dev.batch.bm_b004;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.constant.FlgConstant;
import dev.batch.repository.master.CountryLeagueMasterBatchRepository;
import dev.batch.repository.master.InitialMasterCsvRepository;
import dev.common.constant.MasterNameConstant;
import dev.common.constant.MessageCdConst;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.entity.InitialReadingMasterCsvEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * CountryLeagueDBService管理部品
 *
 * 仕様:
 * - 新規データは insert
 * - 既存データは必要時のみ update
 * - incoming が空/null/N/A なら existing を保持
 * - 新規/更新どちらも initial_reading_master_csv をモーダル表示対象にする
 *
 * @author shiraishitoshio
 */
@Component
@Transactional
public class CountryLeagueDBService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CountryLeagueDBService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CountryLeagueDBService.class.getName();

	/** BM_BATCH_NUMBER */
	private static final String BM_NUMBER = "BM_B004";

	/** InitialMasterCsvRepository */
	@Autowired
	private InitialMasterCsvRepository initialMasterCsvRepository;

	/** CountryLeagueMasterRepositoryレポジトリクラス */
	@Autowired
	private CountryLeagueMasterBatchRepository countryLeagueMasterRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * insert/update の振り分け結果
	 */
	public static class MasterUpsertPlan {
		private final List<CountryLeagueMasterEntity> insertEntities = new ArrayList<>();
		private final List<CountryLeagueMasterEntity> updateEntities = new ArrayList<>();

		public List<CountryLeagueMasterEntity> getInsertEntities() {
			return insertEntities;
		}

		public List<CountryLeagueMasterEntity> getUpdateEntities() {
			return updateEntities;
		}
	}

	/**
	 * チェックメソッド
	 *
	 * - 新規 -> insert
	 * - 既存あり + 更新必要 -> update
	 */
	public MasterUpsertPlan selectInBatch(List<CountryLeagueMasterEntity> chkEntities) {
		final String METHOD_NAME = "selectInBatch";
		MasterUpsertPlan plan = new MasterUpsertPlan();

		if (chkEntities == null || chkEntities.isEmpty()) {
			return plan;
		}

		for (CountryLeagueMasterEntity incoming : chkEntities) {
			if (incoming == null) {
				continue;
			}

			try {
				if (!hasMeaningfulValue(incoming.getCountry())
						|| !hasMeaningfulValue(incoming.getLeague())
						|| !hasMeaningfulValue(incoming.getTeam())) {
					continue;
				}

				String country = incoming.getCountry();
				String league = incoming.getLeague();
				String team = incoming.getTeam();

				CountryLeagueMasterEntity existing =
						this.countryLeagueMasterRepository.findByCountryLeague(country, league, team);

				if (existing == null) {
					plan.getInsertEntities().add(incoming);
					continue;
				}

				if (isSameRecord(existing, incoming)) {
					continue;
				}

				if (shouldUpdate(existing, incoming)) {
					CountryLeagueMasterEntity updateEntity = mergeForUpdate(existing, incoming);
					plan.getUpdateEntities().add(updateEntity);
				}

			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, "DB接続エラー");
				throw e;
			}
		}

		return plan;
	}

	/**
	 * 登録メソッド
	 */
	public int insertInBatch(List<CountryLeagueMasterEntity> insertEntities) {
		final String METHOD_NAME = "insertInBatch";

		if (insertEntities == null || insertEntities.isEmpty()) {
			String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					BM_NUMBER + " 登録件数: 0件");
			return 0;
		}

		int inserted = 0;

		for (CountryLeagueMasterEntity entity : insertEntities) {
			String biko = null;
			try {
				normalizeEntity(entity);

				biko = "country,league,team,link: "
						+ entity.getCountry() + ","
						+ entity.getLeague() + ","
						+ entity.getTeam() + ","
						+ entity.getLink();

				// 新規登録時は del_flg を 0 に戻す
				if (!hasMeaningfulValue(entity.getDelFlg())) {
					entity.setDelFlg("0");
				}

				int result = this.countryLeagueMasterRepository.insert(entity);
				if (result != 1) {
					String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null,
							"countryLeagueMasterRepository");
					return 9;
				}

				// モーダル表示対象にする（新規）
				int initialResult = upsertInitialReadingTarget(entity);
				if (initialResult != 1) {
					String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
					this.manageLoggerComponent.debugErrorLog(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
							null, "initialMasterCsvRepository");
					return 9;
				}

				inserted++;

			} catch (DuplicateKeyException e) {
				String messageCd = MessageCdConst.MCD00002W_DUPLICATION_WARNING;
				this.manageLoggerComponent.debugWarnLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, biko);
				continue;
			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, biko);
				return 9;
			}
		}

		String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 登録件数: " + inserted + "件");
		return 0;
	}

	/**
	 * 更新メソッド
	 */
	public int updateInBatch(List<CountryLeagueMasterEntity> updateEntities) {
		final String METHOD_NAME = "updateInBatch";

		if (updateEntities == null || updateEntities.isEmpty()) {
			String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					BM_NUMBER + " 更新件数: 0件");
			return 0;
		}

		int updated = 0;

		for (CountryLeagueMasterEntity entity : updateEntities) {
			String biko = null;
			try {
				normalizeEntity(entity);

				biko = "id,country,league,team,link: "
						+ entity.getId() + ","
						+ entity.getCountry() + ","
						+ entity.getLeague() + ","
						+ entity.getTeam() + ","
						+ entity.getLink();

				int result = this.countryLeagueMasterRepository.updateAllId(entity);
				if (result > 0) {
					updated += result;

					// モーダル表示対象に戻す（更新）
					int initialResult = upsertInitialReadingTarget(entity);
					if (initialResult != 1) {
						String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
						this.manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
								null, "initialMasterCsvRepository");
						return 9;
					}
				}

			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, biko);
				return 9;
			}
		}

		String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 更新件数: " + updated + "件");
		return 0;
	}

	/**
	 * 初回CSV確認テーブルをモーダル表示対象として登録/再活性化する
	 * - 既存なし: insert
	 * - 既存あり: initial_flg を 0 に戻す
	 */
	private int upsertInitialReadingTarget(CountryLeagueMasterEntity entity) {
		InitialReadingMasterCsvEntity initialEntity = new InitialReadingMasterCsvEntity();
		initialEntity.setCountry(entity.getCountry());
		initialEntity.setLeague(entity.getLeague());
		initialEntity.setMasterName(MasterNameConstant.COUNTRY_LEAGUE_MASTER);
		initialEntity.setInitialFlg(FlgConstant.INITIAL_FLG);

		int count = this.initialMasterCsvRepository.findCount(
				initialEntity.getMasterName(),
				initialEntity.getCountry(),
				initialEntity.getLeague());

		if (count == 0) {
			return this.initialMasterCsvRepository.insert(initialEntity);
		}

		return this.initialMasterCsvRepository.updateInitialFlg(
				initialEntity.getMasterName(),
				initialEntity.getCountry(),
				initialEntity.getLeague(),
				FlgConstant.INITIAL_FLG);
	}

	/**
	 * CSVに存在した country + league をモーダル表示対象に戻す
	 *
	 * 仕様:
	 * - country + league 単位で重複排除
	 * - 既存なし: insert
	 * - 既存あり: initial_flg を 0 に戻す
	 */
	public int resetInitialFlgByIncomingTargets(List<CountryLeagueMasterEntity> entities) {
		final String METHOD_NAME = "resetInitialFlgByIncomingTargets";

		if (entities == null || entities.isEmpty()) {
			return 0;
		}

		Set<String> processedKeys = new HashSet<>();

		for (CountryLeagueMasterEntity entity : entities) {
			try {
				if (entity == null) {
					continue;
				}

				String country = trim(entity.getCountry());
				String league = trim(entity.getLeague());

				if (!hasMeaningfulValue(country) || !hasMeaningfulValue(league)) {
					continue;
				}

				String key = country + "___" + league;
				if (!processedKeys.add(key)) {
					continue;
				}

				InitialReadingMasterCsvEntity initialEntity = new InitialReadingMasterCsvEntity();
				initialEntity.setMasterName(MasterNameConstant.COUNTRY_LEAGUE_MASTER);
				initialEntity.setCountry(country);
				initialEntity.setLeague(league);

				// モーダル表示対象に戻す: 0
				initialEntity.setInitialFlg("0");

				int count = this.initialMasterCsvRepository.findCount(
						initialEntity.getMasterName(),
						initialEntity.getCountry(),
						initialEntity.getLeague());

				int result;
				if (count == 0) {
					result = this.initialMasterCsvRepository.insert(initialEntity);
					if (result != 1) {
						String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
						this.manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
								null, "initialMasterCsvRepository insert failed");
						return 9;
					}
				} else {
					result = this.initialMasterCsvRepository.updateInitialFlg(
							initialEntity.getMasterName(),
							initialEntity.getCountry(),
							initialEntity.getLeague(),
							initialEntity.getInitialFlg());

					// update は DB によって 0 件扱いになる場合もあるので、
					// ここでは例外扱いしない設計でもよい
					if (result < 0) {
						String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
						this.manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
								null, "initialMasterCsvRepository update failed");
						return 9;
					}
				}

			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e);
				return 9;
			}
		}

		return 0;
	}

	/**
	 * 更新対象か判定
	 *
	 * ルール:
	 * - incoming の link が意味のある値で、既存と異なる
	 * - 既存 delFlg が 0 以外で、incoming が来たので再活性化したい
	 */
	private boolean shouldUpdate(
			CountryLeagueMasterEntity existing,
			CountryLeagueMasterEntity incoming) {

		if (hasMeaningfulValue(incoming.getLink())
				&& !Objects.equals(normalizeValue(existing.getLink()), normalizeValue(incoming.getLink()))) {
			return true;
		}

		// 既存が削除扱いなら復帰対象
		if (!"0".equals(normalizeFlag(existing.getDelFlg()))) {
			return true;
		}

		return false;
	}

	/**
	 * 既存と新規が同一内容か
	 */
	private boolean isSameRecord(
			CountryLeagueMasterEntity existing,
			CountryLeagueMasterEntity incoming) {

		return Objects.equals(normalizeValue(existing.getCountry()), normalizeValue(incoming.getCountry()))
				&& Objects.equals(normalizeValue(existing.getLeague()), normalizeValue(incoming.getLeague()))
				&& Objects.equals(normalizeValue(existing.getTeam()), normalizeValue(incoming.getTeam()))
				&& Objects.equals(normalizeValue(existing.getLink()), normalizeValue(incoming.getLink()))
				&& Objects.equals(normalizeFlag(existing.getDelFlg()), normalizeFlag(incoming.getDelFlg()));
	}

	/**
	 * 更新用にマージ
	 *
	 * incoming が空/null/N/A なら existing を採用する。
	 * これにより、手動編集済みの link を空値で上書きしない。
	 */
	private CountryLeagueMasterEntity mergeForUpdate(
			CountryLeagueMasterEntity existing,
			CountryLeagueMasterEntity incoming) {

		CountryLeagueMasterEntity entity = new CountryLeagueMasterEntity();
		entity.setId(existing.getId());

		entity.setCountry(firstMeaningful(incoming.getCountry(), existing.getCountry()));
		entity.setLeague(firstMeaningful(incoming.getLeague(), existing.getLeague()));
		entity.setTeam(firstMeaningful(incoming.getTeam(), existing.getTeam()));
		entity.setLink(firstMeaningful(incoming.getLink(), existing.getLink()));

		// データが再取得できたものは有効化
		entity.setDelFlg("0");

		return entity;
	}

	/**
	 * 値の正規化
	 */
	private void normalizeEntity(CountryLeagueMasterEntity entity) {
		if (entity == null) {
			return;
		}
		entity.setCountry(trim(entity.getCountry()));
		entity.setLeague(trim(entity.getLeague()));
		entity.setTeam(trim(entity.getTeam()));
		entity.setLink(trim(entity.getLink()));
		entity.setDelFlg(trim(entity.getDelFlg()));
	}

	/**
	 * 意味のある値か
	 * 空文字・null・N/A・-・"null"・"未定" は空扱い
	 */
	private boolean hasMeaningfulValue(String value) {
		if (value == null) {
			return false;
		}

		String v = value.trim();
		if (v.isEmpty()) {
			return false;
		}

		String lower = v.toLowerCase();
		if ("n/a".equals(lower) || "null".equals(lower) || "-".equals(v) || "未定".equals(v)) {
			return false;
		}

		return true;
	}

	/**
	 * incoming に意味のある値があれば incoming、無ければ existing
	 */
	private String firstMeaningful(String incoming, String existing) {
		if (hasMeaningfulValue(incoming)) {
			return incoming.trim();
		}
		if (existing == null) {
			return null;
		}
		return existing.trim();
	}

	/**
	 * 比較用正規化
	 */
	private String normalizeValue(String value) {
		if (!hasMeaningfulValue(value)) {
			return null;
		}
		return value.trim();
	}

	/**
	 * flag 比較用
	 */
	private String normalizeFlag(String value) {
		if (value == null || value.isBlank()) {
			return "0";
		}
		return value.trim();
	}

	private String trim(String value) {
		return value == null ? null : value.trim();
	}

	/** 差分の論理削除をするのに必要な、CSV のチーム数の下限（これより少ないスナップショットは信用しない） */
	private static final int MIN_SNAPSHOT_TEAMS = 8;

	/**
	 * CSV（1 ファイル = 1 リーグの順位表のスナップショット）に無いチームを論理削除する（2026-11-28 追加）。
	 *
	 * <p>B004 は登録・更新しかしていなかったため、降格したチームの行が元のリーグに残り、
	 * 「ブンデスリーガ 21 チーム」のようにチーム数が増えていた。
	 * 降格先がスクレイピング対象外のリーグだと、昇格・降格の検知（トリガー）でも拾えない。</p>
	 *
	 * <p>誤削除を防ぐため、次のときは削除しない。</p>
	 * <ul>
	 *   <li>国・リーグが空、またはサブリーグのある行が混じる（順位表がグループ分けされていて、1 ファイルが全チームとは限らない）</li>
	 *   <li>CSV のチーム数が {@link #MIN_SNAPSHOT_TEAMS} 未満</li>
	 *   <li>削除する数が、有効なチームの半分を超える（別のリーグ・壊れたスナップショットの可能性）</li>
	 * </ul>
	 * 同じチームかどうかは、リンク一致またはチーム名（前後・途中の空白を除く）一致で見る。
	 *
	 * @param entities CSV の行
	 * @return 論理削除した件数
	 */
	public int deactivateMissingTeams(List<CountryLeagueMasterEntity> entities) {
		final String METHOD_NAME = "deactivateMissingTeams";
		if (entities == null || entities.isEmpty()) {
			return 0;
		}
		java.util.Map<String, List<CountryLeagueMasterEntity>> groups = new java.util.LinkedHashMap<>();
		for (CountryLeagueMasterEntity e : entities) {
			if (e == null || !hasMeaningfulValue(e.getCountry()) || !hasMeaningfulValue(e.getLeague())
					|| !hasMeaningfulValue(e.getTeam())) {
				continue;
			}
			groups.computeIfAbsent(e.getCountry() + "\u0000" + e.getLeague(), k -> new ArrayList<>()).add(e);
		}

		int total = 0;
		for (List<CountryLeagueMasterEntity> rows : groups.values()) {
			String country = rows.get(0).getCountry();
			String league = rows.get(0).getLeague();
			String label = country + ": " + league;

			Set<String> links = new HashSet<>();
			Set<String> teamKeys = new HashSet<>();
			boolean hasSub = false;
			for (CountryLeagueMasterEntity r : rows) {
				if (hasMeaningfulValue(r.getSubLeague())) {
					hasSub = true;
				}
				if (hasMeaningfulValue(r.getLink())) {
					links.add(r.getLink().trim());
				}
				teamKeys.add(teamKey(r.getTeam()));
			}
			if (hasSub) {
				continue;
			}
			if (teamKeys.size() < MIN_SNAPSHOT_TEAMS) {
				this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, null,
						"[差分削除] " + label + " CSV のチーム数が少ないので削除しません（" + teamKeys.size() + "）");
				continue;
			}

			List<CountryLeagueMasterEntity> active =
					this.countryLeagueMasterRepository.findActiveByCountryAndLeague(country, league);
			List<CountryLeagueMasterEntity> missing = new ArrayList<>();
			boolean activeHasSub = false;
			for (CountryLeagueMasterEntity db : active) {
				if (hasMeaningfulValue(db.getSubLeague())) {
					activeHasSub = true;
				}
				boolean present = (hasMeaningfulValue(db.getLink()) && links.contains(db.getLink().trim()))
						|| teamKeys.contains(teamKey(db.getTeam()));
				if (!present) {
					missing.add(db);
				}
			}
			if (activeHasSub || missing.isEmpty()) {
				continue;
			}
			if (missing.size() * 2 > active.size()) {
				this.manageLoggerComponent.debugWarnLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, null,
						"[差分削除] " + label + " 削除する数が多すぎるので止めます（有効 " + active.size()
								+ " / CSV に無い " + missing.size() + "）");
				continue;
			}
			for (CountryLeagueMasterEntity db : missing) {
				int n = this.countryLeagueMasterRepository.logicalDeleteById(db.getId());
				total += n;
				this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, null,
						"[差分削除] " + label + " 順位表に無いので論理削除 id=" + db.getId() + " team=" + db.getTeam()
								+ (n == 1 ? "" : "（すでに削除済み）"));
			}
		}
		return total;
	}

	/** チーム名の比較キー（空白を全部除く） */
	private static String teamKey(String team) {
		return team == null ? "" : team.replace('\u3000', ' ').replaceAll("\\s+", "");
	}
}
