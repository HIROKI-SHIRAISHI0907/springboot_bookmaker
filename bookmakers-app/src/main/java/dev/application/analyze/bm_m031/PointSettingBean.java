package dev.application.analyze.bm_m031;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import dev.application.domain.repository.master.PointSettingMasterRepository;
import dev.common.entity.PointSettingEntity;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PointSettingBean {

	/** デフォルト勝ち点設定 */
	private static final PointSettingModel DEFAULT_MODEL = new PointSettingModel(3, 0, 1, "");

	/**
	 * 外側キー: country-league
	 * 内側キー: remarks
	 */
	private volatile Map<String, Map<String, PointSettingModel>> pointSettingMap = new ConcurrentHashMap<>();

	/** PointSettingMasterRepository */
	private final PointSettingMasterRepository pointSettingMasterRepository;

	/** 初期化 */
	@PostConstruct
	public void init() {
		reload();
	}

	/**
	 * DBから再読込
	 * 管理画面で勝ち点設定更新後に呼べば即時反映できる
	 */
	public synchronized void reload() {
		List<PointSettingEntity> entities = pointSettingMasterRepository.findAllPoints();

		Map<String, Map<String, PointSettingModel>> newMap = new LinkedHashMap<>();

		for (PointSettingEntity entity : entities) {
			if (entity == null) {
				continue;
			}

			String country = trimToNull(entity.getCountry());
			String league = trimToNull(entity.getLeague());

			if (country == null || league == null) {
				continue;
			}

			String leagueKey = buildLeagueKey(country, league);
			String remarksKey = normalizeRemarks(entity.getRemarks());

			PointSettingModel model = new PointSettingModel(
					defaultIfNull(entity.getWin(), DEFAULT_MODEL.getWin()),
					defaultIfNull(entity.getLose(), DEFAULT_MODEL.getLose()),
					defaultIfNull(entity.getDraw(), DEFAULT_MODEL.getDraw()),
					remarksKey);

			newMap.computeIfAbsent(leagueKey, k -> new LinkedHashMap<>())
			      .put(remarksKey, model);
		}

		this.pointSettingMap = new ConcurrentHashMap<>(newMap);
	}

	/**
	 * 通常設定取得（remarksなし）
	 */
	public PointSettingModel getPointSetting(String country, String league) {
		return getPointSetting(country, league, "");
	}

	/**
	 * remarks付き設定取得
	 * 1. exact remarks
	 * 2. remarks="" の通常設定
	 * 3. デフォルト(3/0/1)
	 */
	public PointSettingModel getPointSetting(String country, String league, String remarks) {
		String leagueKey = buildLeagueKey(country, league);
		String remarksKey = normalizeRemarks(remarks);

		Map<String, PointSettingModel> remarksMap = pointSettingMap.get(leagueKey);
		if (remarksMap == null || remarksMap.isEmpty()) {
			return DEFAULT_MODEL;
		}

		PointSettingModel exact = remarksMap.get(remarksKey);
		if (exact != null) {
			return exact;
		}

		PointSettingModel normal = remarksMap.get("");
		if (normal != null) {
			return normal;
		}

		return DEFAULT_MODEL;
	}

	/**
	 * 勝ち点計算（通常）
	 */
	public int calcWinningPoints(String country, String league, int winCount, int loseCount, int drawCount) {
		return calcWinningPoints(country, league, winCount, loseCount, drawCount, "");
	}

	/**
	 * 勝ち点計算（remarks指定）
	 */
	public int calcWinningPoints(
			String country,
			String league,
			int winCount,
			int loseCount,
			int drawCount,
			String remarks) {

		PointSettingModel model = getPointSetting(country, league, remarks);

		int winPoint = defaultIfNull(model.getWin(), DEFAULT_MODEL.getWin());
		int losePoint = defaultIfNull(model.getLose(), DEFAULT_MODEL.getLose());
		int drawPoint = defaultIfNull(model.getDraw(), DEFAULT_MODEL.getDraw());

		return winCount * winPoint
			 + loseCount * losePoint
			 + drawCount * drawPoint;
	}

	/**
	 * 平坦な通常設定マップが必要な場合の互換getter
	 * key = country-league
	 */
	public Map<String, PointSettingModel> getDefaultPointSettingMap() {
		Map<String, PointSettingModel> result = new LinkedHashMap<>();

		for (Map.Entry<String, Map<String, PointSettingModel>> entry : pointSettingMap.entrySet()) {
			Map<String, PointSettingModel> remarksMap = entry.getValue();

			if (remarksMap == null || remarksMap.isEmpty()) {
				result.put(entry.getKey(), DEFAULT_MODEL);
				continue;
			}

			PointSettingModel model = remarksMap.get("");
			if (model == null) {
				model = remarksMap.values().iterator().next();
			}
			result.put(entry.getKey(), model);
		}

		return result;
	}

	/**
	 * 全件取得
	 */
	public Map<String, Map<String, PointSettingModel>> getPointSettingMap() {
		return pointSettingMap;
	}

	private String buildLeagueKey(String country, String league) {
		return trimToEmpty(country) + "-" + trimToEmpty(league);
	}

	private String normalizeRemarks(String remarks) {
		return trimToEmpty(remarks);
	}

	private String trimToEmpty(String value) {
		return value == null ? "" : value.trim();
	}

	private String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private int defaultIfNull(Integer value, Integer defaultValue) {
		return value == null ? defaultValue : value;
	}
}
