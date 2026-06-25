package dev.batch.bm_b003;

import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import dev.batch.constant.FlgConstant;
import dev.batch.constant.PointSettingConstant;
import dev.batch.repository.master.CountryLeagueSeasonMasterBatchRepository;
import dev.batch.repository.master.InitialMasterCsvRepository;
import dev.batch.repository.master.PointSettingMasterBatchRepository;
import dev.common.constant.MasterNameConstant;
import dev.common.constant.MessageCdConst;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.entity.InitialReadingMasterCsvEntity;
import dev.common.entity.PointSettingEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M029シーズンデータDB管理部品
 *
 * 仕様:
 * - 新規データは insert
 * - 既存データは条件を満たす場合のみ update
 * - update 時は、incoming が 空 / null / N/A / "-" / "null" / "未定" の場合、
 *   既存値を維持する
 * - 新規登録/更新の両方で initial_reading_master_csv をモーダル表示対象にする
 *
 * @author shiraishitoshio
 */
@Component
public class CountryLeagueSeasonDBService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CountryLeagueSeasonDBService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CountryLeagueSeasonDBService.class.getName();

	/** BM_BATCH_NUMBER */
	private static final String BM_NUMBER = "BM_B003";

	/** JST */
	private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

	/** InitialMasterCsvRepository */
	@Autowired
	private InitialMasterCsvRepository initialMasterCsvRepository;

	/** CountryLeagueSeasonMasterRepositoryレポジトリクラス */
	@Autowired
	private CountryLeagueSeasonMasterBatchRepository countryLeagueSeasonMasterRepository;

	/** PointSettingMasterBatchRepositoryレポジトリクラス */
	@Autowired
	private PointSettingMasterBatchRepository pointSettingMasterBatchRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * insert/update の振り分け結果
	 */
	public static class SeasonUpsertPlan {
		private final List<CountryLeagueSeasonMasterEntity> insertEntities = new ArrayList<>();
		private final List<CountryLeagueSeasonMasterEntity> updateEntities = new ArrayList<>();

		public List<CountryLeagueSeasonMasterEntity> getInsertEntities() {
			return insertEntities;
		}

		public List<CountryLeagueSeasonMasterEntity> getUpdateEntities() {
			return updateEntities;
		}
	}

	/**
	 * insert / update の振り分け
	 */
	public SeasonUpsertPlan selectInBatch(List<CountryLeagueSeasonMasterEntity> chkEntities) {
		final String METHOD_NAME = "selectInBatch";
		SeasonUpsertPlan plan = new SeasonUpsertPlan();

		if (chkEntities == null || chkEntities.isEmpty()) {
			return plan;
		}

		int currentYear = Year.now(JST).getValue();

		for (CountryLeagueSeasonMasterEntity entity : chkEntities) {
			if (entity == null) {
				continue;
			}

			try {
				// 取得データの日付を seasonYear から補正
				buildSeasonDates(entity);

				if (!hasMeaningfulValue(entity.getCountry()) || !hasMeaningfulValue(entity.getLeague())) {
					continue;
				}

				CountryLeagueSeasonMasterEntity existing =
						this.countryLeagueSeasonMasterRepository.findLatestByCountryLeague(
								entity.getCountry(), entity.getLeague());

				// 新規
				if (existing == null) {
					plan.getInsertEntities().add(entity);
					continue;
				}

				// 完全同一なら何もしない
				if (isSameRecord(existing, entity)) {
					continue;
				}

				// 更新対象なら update リストへ
				if (shouldUpdate(existing, entity, currentYear)) {
					CountryLeagueSeasonMasterEntity updateEntity = mergeForUpdate(existing, entity);
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
	public int insertInBatch(List<CountryLeagueSeasonMasterEntity> insertEntities) {
		final String METHOD_NAME = "insertInBatch";
		final int BATCH_SIZE = 100;

		if (insertEntities == null || insertEntities.isEmpty()) {
			String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					BM_NUMBER + " 登録件数: 0");
			return 0;
		}

		for (int i = 0; i < insertEntities.size(); i += BATCH_SIZE) {
			int end = Math.min(i + BATCH_SIZE, insertEntities.size());
			List<CountryLeagueSeasonMasterEntity> batch = insertEntities.subList(i, end);
			String biko = null;

			for (CountryLeagueSeasonMasterEntity entity : batch) {
				try {
					buildSeasonDates(entity);

					biko = "country,league,seasonYear,start,end: "
							+ entity.getCountry() + ","
							+ entity.getLeague() + ","
							+ entity.getSeasonYear() + ","
							+ entity.getStartSeasonDate() + ","
							+ entity.getEndSeasonDate();

					// シーズンマスタ登録
					int result = this.countryLeagueSeasonMasterRepository.insert(entity);
					if (result != 1) {
						String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
						this.manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
								null, "countryLeagueSeasonMasterRepository");
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

					// 勝ち点設定マスタ登録
					PointSettingEntity eSettingEntity = new PointSettingEntity();
					eSettingEntity.setCountry(entity.getCountry());
					eSettingEntity.setLeague(entity.getLeague());
					eSettingEntity.setWin(PointSettingConstant.WIN);
					eSettingEntity.setLose(PointSettingConstant.LOSE);
					eSettingEntity.setDraw(PointSettingConstant.DRAW);

					int result2 = this.pointSettingMasterBatchRepository.insert(eSettingEntity);
					if (result2 != 1) {
						String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
						this.manageLoggerComponent.debugErrorLog(
								PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
								null, "pointSettingMasterBatchRepository");
						return 9;
					}

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
		}

		String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 登録件数: " + insertEntities.size());
		return 0;
	}

	/**
	 * 更新メソッド
	 */
	public int updateInBatch(List<CountryLeagueSeasonMasterEntity> updateEntities) {
		final String METHOD_NAME = "updateInBatch";
		final int BATCH_SIZE = 100;

		if (updateEntities == null || updateEntities.isEmpty()) {
			String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
					BM_NUMBER + " 更新件数: 0");
			return 0;
		}

		int updated = 0;

		for (int i = 0; i < updateEntities.size(); i += BATCH_SIZE) {
			int end = Math.min(i + BATCH_SIZE, updateEntities.size());
			List<CountryLeagueSeasonMasterEntity> batch = updateEntities.subList(i, end);
			String biko = null;

			for (CountryLeagueSeasonMasterEntity entity : batch) {
				try {
					buildSeasonDates(entity);

					biko = "id,country,league,seasonYear,start,end: "
							+ entity.getId() + ","
							+ entity.getCountry() + ","
							+ entity.getLeague() + ","
							+ entity.getSeasonYear() + ","
							+ entity.getStartSeasonDate() + ","
							+ entity.getEndSeasonDate();

					int result = this.countryLeagueSeasonMasterRepository.updateById(entity);
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
		}

		String messageCd = MessageCdConst.MCD00006I_UPDATE_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 更新件数: " + updated);
		return 0;
	}

	/**
	 * 初回CSV確認テーブルをモーダル表示対象として登録/再活性化する
	 * - 既存なし: insert
	 * - 既存あり: initial_flg を 0 に戻す
	 */
	private int upsertInitialReadingTarget(CountryLeagueSeasonMasterEntity entity) {
		InitialReadingMasterCsvEntity initialEntity = new InitialReadingMasterCsvEntity();
		initialEntity.setCountry(entity.getCountry());
		initialEntity.setLeague(entity.getLeague());
		initialEntity.setMasterName(MasterNameConstant.COUNTRY_LEAGUE_SEASON_MASTER);
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
	 * 更新対象か判定
	 *
	 * ルール:
	 * - 既存seasonYearに今年が含まれず、新データに意味のある日付がある
	 * - 既存終了日が空で、新データに開始/終了日が入っている
	 * - 新seasonYearの方が新しい かつ 新データに意味のある値がある
	 * - 同seasonYearでも、空欄補完 or round/path/icon の有効値更新がある
	 */
	private boolean shouldUpdate(
			CountryLeagueSeasonMasterEntity existing,
			CountryLeagueSeasonMasterEntity incoming,
			int currentYear) {

		boolean existingHasCurrentYear = containsYear(existing.getSeasonYear(), currentYear);
		boolean incomingHasAnyDate =
				hasMeaningfulValue(incoming.getStartSeasonDate()) || hasMeaningfulValue(incoming.getEndSeasonDate());
		boolean incomingHasBothDates =
				hasMeaningfulValue(incoming.getStartSeasonDate()) && hasMeaningfulValue(incoming.getEndSeasonDate());
		boolean incomingIsNewer = compareSeasonYear(incoming.getSeasonYear(), existing.getSeasonYear()) > 0;

		// 既存seasonYearに今年が含まれない = 古いので更新対象
		if (!existingHasCurrentYear && incomingHasAnyDate) {
			return true;
		}

		// 既存終了日が空で、新しい開始/終了日が埋まっている
		if (!hasMeaningfulValue(existing.getEndSeasonDate()) && incomingHasBothDates) {
			return true;
		}

		// 新seasonYearの方が新しい かつ incoming に意味のある値がある
		if (incomingIsNewer && hasAnyMeaningfulSeasonData(incoming)) {
			return true;
		}

		// 同seasonYearでも、空欄補完 or round/path/icon更新
		if (Objects.equals(normalizeValue(existing.getSeasonYear()), normalizeValue(incoming.getSeasonYear()))) {
			if (!hasMeaningfulValue(existing.getStartSeasonDate()) && hasMeaningfulValue(incoming.getStartSeasonDate())) {
				return true;
			}
			if (!hasMeaningfulValue(existing.getEndSeasonDate()) && hasMeaningfulValue(incoming.getEndSeasonDate())) {
				return true;
			}
			if (!Objects.equals(normalizeValue(existing.getRound()), normalizeValue(incoming.getRound()))
					&& hasMeaningfulValue(incoming.getRound())) {
				return true;
			}
			if (!Objects.equals(normalizeValue(existing.getPath()), normalizeValue(incoming.getPath()))
					&& hasMeaningfulValue(incoming.getPath())) {
				return true;
			}
			if (!Objects.equals(normalizeValue(existing.getIcon()), normalizeValue(incoming.getIcon()))
					&& hasMeaningfulValue(incoming.getIcon())) {
				return true;
			}
		}

		return false;
	}

	/**
	 * 既存と新規が同一内容か
	 */
	private boolean isSameRecord(
			CountryLeagueSeasonMasterEntity existing,
			CountryLeagueSeasonMasterEntity incoming) {
		return Objects.equals(normalizeValue(existing.getCountry()), normalizeValue(incoming.getCountry()))
				&& Objects.equals(normalizeValue(existing.getLeague()), normalizeValue(incoming.getLeague()))
				&& Objects.equals(normalizeValue(existing.getSeasonYear()), normalizeValue(incoming.getSeasonYear()))
				&& Objects.equals(normalizeValue(existing.getStartSeasonDate()), normalizeValue(incoming.getStartSeasonDate()))
				&& Objects.equals(normalizeValue(existing.getEndSeasonDate()), normalizeValue(incoming.getEndSeasonDate()))
				&& Objects.equals(normalizeValue(existing.getRound()), normalizeValue(incoming.getRound()))
				&& Objects.equals(normalizeValue(existing.getPath()), normalizeValue(incoming.getPath()))
				&& Objects.equals(normalizeValue(existing.getIcon()), normalizeValue(incoming.getIcon()));
	}

	/**
	 * 更新用にマージ
	 *
	 * incoming が空/null/N/A なら existing を採用する。
	 * これにより、手動編集済み値を空値で上書きしない。
	 */
	private CountryLeagueSeasonMasterEntity mergeForUpdate(
			CountryLeagueSeasonMasterEntity existing,
			CountryLeagueSeasonMasterEntity incoming) {

		CountryLeagueSeasonMasterEntity entity = new CountryLeagueSeasonMasterEntity();
		entity.setId(existing.getId());

		entity.setCountry(firstMeaningful(incoming.getCountry(), existing.getCountry()));
		entity.setLeague(firstMeaningful(incoming.getLeague(), existing.getLeague()));
		entity.setSeasonYear(firstMeaningful(incoming.getSeasonYear(), existing.getSeasonYear()));
		entity.setStartSeasonDate(firstMeaningful(incoming.getStartSeasonDate(), existing.getStartSeasonDate()));
		entity.setEndSeasonDate(firstMeaningful(incoming.getEndSeasonDate(), existing.getEndSeasonDate()));
		entity.setRound(firstMeaningful(incoming.getRound(), existing.getRound()));
		entity.setPath(firstMeaningful(incoming.getPath(), existing.getPath()));
		entity.setIcon(firstMeaningful(incoming.getIcon(), existing.getIcon()));
		entity.setValidFlg(firstMeaningful(incoming.getValidFlg(), existing.getValidFlg()));
		entity.setDelFlg(firstMeaningful(incoming.getDelFlg(), existing.getDelFlg()));

		return entity;
	}

	/**
	 * seasonYear から開始日/終了日を補正
	 */
	private void buildSeasonDates(CountryLeagueSeasonMasterEntity entity) {
		if (entity == null || !hasMeaningfulValue(entity.getSeasonYear())) {
			return;
		}
		String[] years = SeasonDateBuilder.convertSeasonYear(entity.getSeasonYear());
		String startDate = SeasonDateBuilder.buildDate(years[0], entity.getStartSeasonDate());
		String endDate = SeasonDateBuilder.buildDate(years[1], entity.getEndSeasonDate());
		entity.setStartSeasonDate(startDate);
		entity.setEndSeasonDate(endDate);
	}

	/**
	 * seasonYear に指定年が含まれるか
	 * 例: 2026, 2025/2026, 2026/2027
	 */
	private boolean containsYear(String seasonYear, int year) {
		if (!hasMeaningfulValue(seasonYear)) {
			return false;
		}
		String yearStr = String.valueOf(year);
		Pattern p = Pattern.compile("\\d{4}");
		Matcher m = p.matcher(seasonYear);
		while (m.find()) {
			if (yearStr.equals(m.group())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * seasonYear の新旧比較
	 * 2025/2026 > 2025
	 * 2026 > 2025/2025
	 */
	private int compareSeasonYear(String a, String b) {
		return Integer.compare(extractMaxYear(a), extractMaxYear(b));
	}

	private int extractMaxYear(String seasonYear) {
		if (!hasMeaningfulValue(seasonYear)) {
			return -1;
		}
		Pattern p = Pattern.compile("\\d{4}");
		Matcher m = p.matcher(seasonYear);
		int max = -1;
		while (m.find()) {
			int y = Integer.parseInt(m.group());
			if (y > max) {
				max = y;
			}
		}
		return max;
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
	 * シーズン情報として意味のある値を1つでも持っているか
	 */
	private boolean hasAnyMeaningfulSeasonData(CountryLeagueSeasonMasterEntity entity) {
		if (entity == null) {
			return false;
		}

		return hasMeaningfulValue(entity.getSeasonYear())
				|| hasMeaningfulValue(entity.getStartSeasonDate())
				|| hasMeaningfulValue(entity.getEndSeasonDate())
				|| hasMeaningfulValue(entity.getRound())
				|| hasMeaningfulValue(entity.getPath())
				|| hasMeaningfulValue(entity.getIcon());
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
}
