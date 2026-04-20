package dev.web.api.bm_a015;

import java.util.List;

import lombok.Data;

@Data
public class PointSettingsSaveRequest {

	/** item */
	private List<PointSettingItem> items;

}
