package dev.application.analyze.bm_m031;

import java.util.Collections;
import java.util.HashMap;
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

	/** デフォルト設定 */
	private static final PointSettingModel DEFAULT_MODEL = new PointSettingModel(3, 0, 1, "");

	/** 通常設定: key = country-league */
	private volatile Map<String, PointSettingModel> pointSettingMap = new ConcurrentHashMap<>();

	/** 特殊設定: key = country-league, value = { "PK勝ち"=2, "PK負け"=1 } */
	private volatile Map<String, Map<String, Integer>> pointSettingRemarksMap = new ConcurrentHashMap<>();

	/** PointSettingMasterRepository */
	private final PointSettingMasterRepository pointSettingMasterRepository;

	@PostConstruct
	public void init() {
		reload();
	}

	public synchronized void reload() {
		List<PointSettingEntity> entities = pointSettingMasterRepository.findAllPoints();

		Map<String, PointSettingModel> newPointMap = new LinkedHashMap<>();
		Map<String, Map<String, Integer>> newRemarksMap = new LinkedHashMap<>();

		for (PointSettingEntity entity : entities) {
			if (entity == null) {
				continue;
			}

			String country = trimToNull(entity.getCountry());
			String league = trimToNull(entity.getLeague());

			if (country == null || league == null) {
				continue;
			}

			String key = buildKey(country, league);

			PointSettingModel model = new PointSettingModel(
					defaultIfNull(entity.getWin(), DEFAULT_MODEL.getWin()),
					defaultIfNull(entity.getLose(), DEFAULT_MODEL.getLose()),
					defaultIfNull(entity.getDraw(), DEFAULT_MODEL.getDraw()),
					trimToEmpty(entity.getRemarks()));

			newPointMap.put(key, model);

			// remarks が null / 空なら空Map（= 特殊ルールなし）
			newRemarksMap.put(key, parseRemarks(entity.getRemarks()));
		}

		this.pointSettingMap = new ConcurrentHashMap<>(newPointMap);
		this.pointSettingRemarksMap = new ConcurrentHashMap<>(newRemarksMap);
	}

	/**
	 * 通常設定取得
	 */
	public PointSettingModel getPointSetting(String country, String league) {
		String key = buildKey(country, league);
		return pointSettingMap.getOrDefault(key, DEFAULT_MODEL);
	}

	/**
	 * resultType:
	 * - 勝ち
	 * - 負け
	 * - 引分
	 * - PK勝ち
	 * - PK負け
	 *
	 * remarks が null / 空 の場合は remarks 由来の特殊勝ち点は除外し、
	 * 通常の勝ち点設定へフォールバックする。
	 */
	public int getPoint(String country, String league, String resultType) {
		PointSettingModel model = getPointSetting(country, league);
		Map<String, Integer> remarksMap = pointSettingRemarksMap.getOrDefault(
				buildKey(country, league),
				Collections.emptyMap());

		String type = trimToEmpty(resultType);

		switch (type) {
		case "PK勝ち": {
			Integer pkWin = getRemarksPoint(remarksMap, "PK勝ち");
			return pkWin != null
					? pkWin
					: defaultIfNull(model.getWin(), DEFAULT_MODEL.getWin());
		}
		case "PK負け": {
			Integer pkLose = getRemarksPoint(remarksMap, "PK負け");
			return pkLose != null
					? pkLose
					: defaultIfNull(model.getLose(), DEFAULT_MODEL.getLose());
		}
		case "引分":
			return defaultIfNull(model.getDraw(), DEFAULT_MODEL.getDraw());
		case "負け":
			return defaultIfNull(model.getLose(), DEFAULT_MODEL.getLose());
		case "勝ち":
		default:
			return defaultIfNull(model.getWin(), DEFAULT_MODEL.getWin());
		}
	}

	private Integer getRemarksPoint(Map<String, Integer> remarksMap, String key) {
		if (remarksMap == null || remarksMap.isEmpty()) {
			return null;
		}
		return remarksMap.get(key);
	}

	/**
	 * 例:
	 * "PK勝ち=2,PK負け=1"
	 *
	 * remarks が null / 空なら空Mapを返す
	 */
	private Map<String, Integer> parseRemarks(String remarks) {
		Map<String, Integer> result = new HashMap<>();

		if (remarks == null || remarks.isBlank()) {
			return result;
		}

		String[] tokens = remarks.split("[,，]");
		for (String token : tokens) {
			if (token == null || token.isBlank()) {
				continue;
			}

			String[] pair = token.split("[=＝]", 2);
			if (pair.length != 2) {
				continue;
			}

			String ruleKey = trimToEmpty(pair[0]);
			String ruleValue = trimToEmpty(pair[1]);

			if (ruleKey.isEmpty() || ruleValue.isEmpty()) {
				continue;
			}

			try {
				result.put(ruleKey, Integer.parseInt(ruleValue));
			} catch (NumberFormatException ignore) {
				// 不正値は無視
			}
		}

		return result;
	}

	private String buildKey(String country, String league) {
		return trimToEmpty(country) + "-" + trimToEmpty(league);
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

	public Map<String, PointSettingModel> getPointSettingMap() {
		return pointSettingMap;
	}

	public Map<String, Map<String, Integer>> getPointSettingRemarksMap() {
		return pointSettingRemarksMap;
	}
}
