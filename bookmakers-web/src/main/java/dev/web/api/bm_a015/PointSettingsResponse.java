package dev.web.api.bm_a015;

import java.util.List;

import dev.common.entity.PointSettingEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointSettingsResponse {

	/** items */
	private List<PointSettingEntity> items;

}
