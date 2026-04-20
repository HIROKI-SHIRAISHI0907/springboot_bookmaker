package dev.application.analyze.bm_m031;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.application.domain.repository.master.PointSettingMasterRepository;
import dev.common.entity.PointSettingEntity;

public class PointSettingBean {

	private Map<String, PointSettingModel> pointSettingMap;

	/** PointSettingMasterRepository */
	private PointSettingMasterRepository pointSettingMasterRepository;

	/** 初期化 */
	public void init() {
		List<PointSettingEntity> entities = pointSettingMasterRepository.findAllPoints();

		pointSettingMap = new HashMap<>();

		for (PointSettingEntity entity : entities) {
			if (entity == null) {
				continue;
			}

			String country = trim(entity.getCountry());
			String league = trim(entity.getLeague());

			if (country == null || league == null) {
				continue;
			}

			String key = country + "-" + league;

			PointSettingModel model = new PointSettingModel(
					entity.getWin(),
					entity.getLose(),
					entity.getDraw(),
					entity.getRemarks());

			pointSettingMap.put(key, model);
		}
	}

	private String trim(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	/** getter */
	public Map<String, PointSettingModel> getPointSettingMap() {
		return pointSettingMap;
	}
}
