package dev.batch.bm_b003;

import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.master.CountryLeagueSeasonMasterBatchRepository;
import dev.batch.repository.master.InitialMasterCsvRepository;
import dev.common.constant.MasterNameConstant;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.entity.InitialReadingMasterCsvEntity;

@Component
public class CountryLeagueSeasonDBService {

	private static final Logger log = LoggerFactory.getLogger(CountryLeagueSeasonDBService.class);

	/** バッチサイズ */
	private static final int BATCH_SIZE = 100;

	/** JST */
	private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

	/** バッチ番号 */
	private static final String BM_NUMBER = "BM_B003";

	@Autowired
	private CountryLeagueSeasonMasterBatchRepository countryLeagueSeasonMasterRepository;

	@Autowired
	private InitialMasterCsvRepository initialMasterCsvRepository;

	/**
	 * insert対象 / update対象を振り分ける
	 */
	@Transactional(readOnly = true)
	public SeasonUpsertPlan selectInBatch(List<CountryLeagueSeasonMasterEntity> entities) {
		SeasonUpsertPlan plan = new SeasonUpsertPlan();

		if (entities == null || entities.isEmpty()) {
			log.info("[{}][CountryLeagueSeasonDBService#selectInBatch] input is empty.", BM_NUMBER);
			return plan;
		}

		for (CountryLeagueSeasonMasterEntity entity : entities) {
			if (entity == null) {
				continue;
			}

			try {
				normalizeEntity(entity);

				if (!hasMeaningfulValue(entity.getCountry()) || !hasMeaningfulValue(entity.getLeague())) {
					continue;
				}

				// 日付正規化はここで一度だけ行う
				buildSeasonDates(entity);

				CountryLeagueSeasonMasterEntity existing =
						countryLeagueSeasonMasterRepository.findLatestByCountryLeague(
								trim(entity.getCountry()),
								trim(entity.getLeague()));

				if (existing == null) {
					if (!hasMeaningfulValue(entity.getValidFlg())) {
						entity.setValidFlg("1");
					}
					if (!hasMeaningfulValue(entity.getDelFlg())) {
						entity.setDelFlg("0");
					}
					plan.getInsertEntities().add(entity);
					continue;
				}

				normalizeEntity(existing);

				if (shouldUpdate(existing, entity)) {
					CountryLeagueSeasonMasterEntity merged = mergeForUpdate(existing, entity);

					if (!isSameRecord(existing, merged)) {
						plan.getUpdateEntities().add(merged);
					}
				}

			} catch (Exception e) {
				log.error("[{}][CountryLeagueSeasonDBService#selectInBatch] entity analyze error. country={}, league={}, seasonYear={}",
						BM_NUMBER,
						safe(entity.getCountry()),
						safe(entity.getLeague()),
						safe(entity.getSeasonYear()),
						e);
			}
		}

		log.info("[{}][CountryLeagueSeasonDBService#selectInBatch] insert={}, update={}",
				BM_NUMBER,
				plan.getInsertEntities().size(),
				plan.getUpdateEntities().size());

		return plan;
	}

	/**
	 * INSERT
	 */
	@Transactional
	public int insertInBatch(List<CountryLeagueSeasonMasterEntity> entities) {
		if (entities == null || entities.isEmpty()) {
			log.info("[{}][CountryLeagueSeasonDBService#insertInBatch] target is empty.", BM_NUMBER);
			return 0;
		}

		try {
			for (int i = 0; i < entities.size(); i += BATCH_SIZE) {
				int end = Math.min(i + BATCH_SIZE, entities.size());
				List<CountryLeagueSeasonMasterEntity> batch = entities.subList(i, end);

				for (CountryLeagueSeasonMasterEntity entity : batch) {
					try {
						// ここでは再度 buildSeasonDates() しない
						normalizeEntity(entity);

						int result = countryLeagueSeasonMasterRepository.insert(entity);

						if (result > 0) {
							upsertInitialReadingTarget(entity);
							log.info("[{}][CountryLeagueSeasonDBService#insertInBatch] insert success. country={}, league={}, seasonYear={}, start={}, end={}",
									BM_NUMBER,
									safe(entity.getCountry()),
									safe(entity.getLeague()),
									safe(entity.getSeasonYear()),
									safe(entity.getStartSeasonDate()),
									safe(entity.getEndSeasonDate()));
						} else {
							log.warn("[{}][CountryLeagueSeasonDBService#insertInBatch] insert skipped. country={}, league={}, seasonYear={}",
									BM_NUMBER,
									safe(entity.getCountry()),
									safe(entity.getLeague()),
									safe(entity.getSeasonYear()));
						}

					} catch (Exception e) {
						log.error("[{}][CountryLeagueSeasonDBService#insertInBatch] insert error. country={}, league={}, seasonYear={}",
								BM_NUMBER,
								safe(entity.getCountry()),
								safe(entity.getLeague()),
								safe(entity.getSeasonYear()),
								e);
						return 9;
					}
				}
			}

			return 0;

		} catch (Exception e) {
			log.error("[{}][CountryLeagueSeasonDBService#insertInBatch] unexpected error.", BM_NUMBER, e);
			return 9;
		}
	}

	/**
	 * UPDATE
	 */
	@Transactional
	public int updateInBatch(List<CountryLeagueSeasonMasterEntity> entities) {
		if (entities == null || entities.isEmpty()) {
			log.info("[{}][CountryLeagueSeasonDBService#updateInBatch] target is empty.", BM_NUMBER);
			return 0;
		}

		try {
			for (int i = 0; i < entities.size(); i += BATCH_SIZE) {
				int end = Math.min(i + BATCH_SIZE, entities.size());
				List<CountryLeagueSeasonMasterEntity> batch = entities.subList(i, end);

				for (CountryLeagueSeasonMasterEntity entity : batch) {
					try {
						// ここでは再度 buildSeasonDates() しない
						normalizeEntity(entity);

						int result = countryLeagueSeasonMasterRepository.updateById(entity);

						if (result > 0) {
							upsertInitialReadingTarget(entity);
							log.info("[{}][CountryLeagueSeasonDBService#updateInBatch] update success. id={}, country={}, league={}, seasonYear={}, start={}, end={}",
									BM_NUMBER,
									entity.getId(),
									safe(entity.getCountry()),
									safe(entity.getLeague()),
									safe(entity.getSeasonYear()),
									safe(entity.getStartSeasonDate()),
									safe(entity.getEndSeasonDate()));
						} else {
							log.warn("[{}][CountryLeagueSeasonDBService#updateInBatch] update target not found. id={}, country={}, league={}",
									BM_NUMBER,
									entity.getId(),
									safe(entity.getCountry()),
									safe(entity.getLeague()));
						}

					} catch (Exception e) {
						log.error("[{}][CountryLeagueSeasonDBService#updateInBatch] update error. id={}, country={}, league={}, seasonYear={}",
								BM_NUMBER,
								entity.getId(),
								safe(entity.getCountry()),
								safe(entity.getLeague()),
								safe(entity.getSeasonYear()),
								e);
						return 9;
					}
				}
			}

			return 0;

		} catch (Exception e) {
			log.error("[{}][CountryLeagueSeasonDBService#updateInBatch] unexpected error.", BM_NUMBER, e);
			return 9;
		}
	}

	/**
	 * initial_reading_master_csv を insert/update
	 * 新規 or 更新時は initialFlg=0 に戻す
	 */
	private void upsertInitialReadingTarget(CountryLeagueSeasonMasterEntity entity) {
		if (entity == null) {
			return;
		}
		if (!hasMeaningfulValue(entity.getCountry()) || !hasMeaningfulValue(entity.getLeague())) {
			return;
		}

		int count = initialMasterCsvRepository.findCount(
				MasterNameConstant.COUNTRY_LEAGUE_SEASON_MASTER,
				trim(entity.getCountry()),
				trim(entity.getLeague()));

		if (count <= 0) {
			InitialReadingMasterCsvEntity initialEntity = new InitialReadingMasterCsvEntity();
			initialEntity.setMasterName(MasterNameConstant.COUNTRY_LEAGUE_SEASON_MASTER);
			initialEntity.setCountry(trim(entity.getCountry()));
			initialEntity.setLeague(trim(entity.getLeague()));
			initialEntity.setInitialFlg("0");
			initialMasterCsvRepository.insert(initialEntity);
		} else {
			initialMasterCsvRepository.updateInitialFlg(
					MasterNameConstant.COUNTRY_LEAGUE_SEASON_MASTER,
					trim(entity.getCountry()),
					trim(entity.getLeague()),
					"0");
		}
	}

	/**
	 * 更新要否判定
	 * - endSeasonDate が既存で空、incomingで埋まる
	 * - 既存 seasonYear が現年を含まない
	 * - incoming seasonYear が existing より新しい
	 * - 同 seasonYear でも空項目が埋まる
	 * - 削除状態から復帰
	 */
	private boolean shouldUpdate(CountryLeagueSeasonMasterEntity existing, CountryLeagueSeasonMasterEntity incoming) {
		if (existing == null || incoming == null) {
			return false;
		}

		String currentYear = String.valueOf(Year.now(JST).getValue());

		if (!containsYear(existing.getSeasonYear(), currentYear) && hasAnyMeaningfulSeasonData(incoming)) {
			return true;
		}

		if (!hasMeaningfulValue(existing.getEndSeasonDate())
				&& hasMeaningfulValue(incoming.getStartSeasonDate())
				&& hasMeaningfulValue(incoming.getEndSeasonDate())) {
			return true;
		}

		int compare = compareSeasonYear(incoming.getSeasonYear(), existing.getSeasonYear());
		if (compare > 0 && hasAnyMeaningfulSeasonData(incoming)) {
			return true;
		}

		if (compare == 0) {
			if (!hasMeaningfulValue(existing.getStartSeasonDate()) && hasMeaningfulValue(incoming.getStartSeasonDate())) {
				return true;
			}
			if (!hasMeaningfulValue(existing.getEndSeasonDate()) && hasMeaningfulValue(incoming.getEndSeasonDate())) {
				return true;
			}
			if (!hasMeaningfulValue(existing.getRound()) && hasMeaningfulValue(incoming.getRound())) {
				return true;
			}
			if (!hasMeaningfulValue(existing.getPath()) && hasMeaningfulValue(incoming.getPath())) {
				return true;
			}
			if (!hasMeaningfulValue(existing.getIcon()) && hasMeaningfulValue(incoming.getIcon())) {
				return true;
			}
		}

		if (!"0".equals(normalizeFlag(existing.getDelFlg()))) {
			return true;
		}

		CountryLeagueSeasonMasterEntity merged = mergeForUpdate(existing, incoming);
		return !isSameRecord(existing, merged);
	}

	/**
	 * 更新用マージ
	 * 空/null/N/A などでは既存値を上書きしない
	 */
	private CountryLeagueSeasonMasterEntity mergeForUpdate(
			CountryLeagueSeasonMasterEntity existing,
			CountryLeagueSeasonMasterEntity incoming) {

		CountryLeagueSeasonMasterEntity merged = new CountryLeagueSeasonMasterEntity();

		merged.setId(existing.getId());
		merged.setCountry(firstMeaningful(incoming.getCountry(), existing.getCountry()));
		merged.setLeague(firstMeaningful(incoming.getLeague(), existing.getLeague()));
		merged.setSeasonYear(firstMeaningful(incoming.getSeasonYear(), existing.getSeasonYear()));
		merged.setStartSeasonDate(firstMeaningful(incoming.getStartSeasonDate(), existing.getStartSeasonDate()));
		merged.setEndSeasonDate(firstMeaningful(incoming.getEndSeasonDate(), existing.getEndSeasonDate()));
		merged.setRound(firstMeaningful(incoming.getRound(), existing.getRound()));
		merged.setPath(firstMeaningful(incoming.getPath(), existing.getPath()));
		merged.setIcon(firstMeaningful(incoming.getIcon(), existing.getIcon()));
		merged.setValidFlg(firstMeaningful(incoming.getValidFlg(), existing.getValidFlg()));
		merged.setDelFlg("0");

		return merged;
	}

	/**
	 * 同一判定
	 */
	private boolean isSameRecord(CountryLeagueSeasonMasterEntity a, CountryLeagueSeasonMasterEntity b) {
		if (a == null && b == null) {
			return true;
		}
		if (a == null || b == null) {
			return false;
		}

		return normalizeValue(a.getCountry()).equals(normalizeValue(b.getCountry()))
				&& normalizeValue(a.getLeague()).equals(normalizeValue(b.getLeague()))
				&& normalizeValue(a.getSeasonYear()).equals(normalizeValue(b.getSeasonYear()))
				&& normalizeValue(a.getStartSeasonDate()).equals(normalizeValue(b.getStartSeasonDate()))
				&& normalizeValue(a.getEndSeasonDate()).equals(normalizeValue(b.getEndSeasonDate()))
				&& normalizeValue(a.getRound()).equals(normalizeValue(b.getRound()))
				&& normalizeValue(a.getPath()).equals(normalizeValue(b.getPath()))
				&& normalizeValue(a.getIcon()).equals(normalizeValue(b.getIcon()))
				&& normalizeValue(normalizeFlag(a.getValidFlg())).equals(normalizeValue(normalizeFlag(b.getValidFlg())))
				&& normalizeValue(normalizeFlag(a.getDelFlg())).equals(normalizeValue(normalizeFlag(b.getDelFlg())));
	}

	/**
	 * seasonYear と seasonDate から yyyy-MM-dd を構築
	 * - すでに yyyy-MM-dd ならそのまま
	 * - dd.MM. なら buildDate() で変換
	 * - 空/null/N/A/-/未定 は null 扱い
	 */
	private void buildSeasonDates(CountryLeagueSeasonMasterEntity entity) {
		if (entity == null || !hasMeaningfulValue(entity.getSeasonYear())) {
			return;
		}

		String[] years = SeasonDateBuilder.convertSeasonYear(entity.getSeasonYear());
		if (years == null || years.length < 2) {
			return;
		}

		String startDate = normalizeSeasonDate(years[0], entity.getStartSeasonDate());
		String endDate = normalizeSeasonDate(years[1], entity.getEndSeasonDate());

		entity.setStartSeasonDate(startDate);
		entity.setEndSeasonDate(endDate);
	}

	private String normalizeSeasonDate(String year, String seasonDate) {
		if (!hasMeaningfulValue(seasonDate)) {
			return null;
		}

		String value = seasonDate.trim();

		if (isIsoDate(value)) {
			return value;
		}

		if (isDayMonthFormat(value)) {
			return SeasonDateBuilder.buildDate(year, value);
		}

		throw new IllegalArgumentException("Invalid seasonDate format: " + seasonDate);
	}

	private boolean isIsoDate(String value) {
		return value != null && value.matches("^\\d{4}-\\d{2}-\\d{2}$");
	}

	private boolean isDayMonthFormat(String value) {
		return value != null && value.matches("^\\d{2}\\.\\d{2}\\.$");
	}

	private boolean containsYear(String seasonYear, String year) {
		if (!hasMeaningfulValue(seasonYear) || !hasMeaningfulValue(year)) {
			return false;
		}
		return seasonYear.contains(year);
	}

	private int compareSeasonYear(String a, String b) {
		int aYear = extractMaxYear(a);
		int bYear = extractMaxYear(b);
		return Integer.compare(aYear, bYear);
	}

	private int extractMaxYear(String value) {
		if (!hasMeaningfulValue(value)) {
			return -1;
		}

		Matcher matcher = Pattern.compile("(\\d{4})").matcher(value);
		int max = -1;
		while (matcher.find()) {
			int year = Integer.parseInt(matcher.group(1));
			if (year > max) {
				max = year;
			}
		}
		return max;
	}

	private void normalizeEntity(CountryLeagueSeasonMasterEntity entity) {
		if (entity == null) {
			return;
		}

		entity.setId(entity.getId());
		entity.setCountry(trim(entity.getCountry()));
		entity.setLeague(trim(entity.getLeague()));
		entity.setSeasonYear(trim(entity.getSeasonYear()));
		entity.setStartSeasonDate(trim(entity.getStartSeasonDate()));
		entity.setEndSeasonDate(trim(entity.getEndSeasonDate()));
		entity.setRound(trim(entity.getRound()));
		entity.setPath(trim(entity.getPath()));
		entity.setIcon(trim(entity.getIcon()));

		if (!hasMeaningfulValue(entity.getValidFlg())) {
			entity.setValidFlg("1");
		} else {
			entity.setValidFlg(trim(entity.getValidFlg()));
		}

		if (!hasMeaningfulValue(entity.getDelFlg())) {
			entity.setDelFlg("0");
		} else {
			entity.setDelFlg(trim(entity.getDelFlg()));
		}
	}

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

	private boolean hasMeaningfulValue(String value) {
		if (value == null) {
			return false;
		}
		String v = value.trim();
		if (v.isEmpty()) {
			return false;
		}
		return !("N/A".equalsIgnoreCase(v)
				|| "-".equals(v)
				|| "null".equalsIgnoreCase(v)
				|| "未定".equals(v));
	}

	private String firstMeaningful(String incoming, String existing) {
		return hasMeaningfulValue(incoming) ? trim(incoming) : trim(existing);
	}

	private String normalizeValue(String value) {
		return value == null ? "" : value.trim();
	}

	private String normalizeFlag(String value) {
		return hasMeaningfulValue(value) ? trim(value) : "0";
	}

	private String trim(String value) {
		return value == null ? null : value.trim();
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}

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
}
