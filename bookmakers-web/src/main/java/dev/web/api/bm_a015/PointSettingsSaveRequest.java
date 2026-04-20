package dev.web.api.bm_a015;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class PointSettingsSaveRequest {

	/** item */
	@NotEmpty(message = "itemsは1件以上指定してください。")
	@Valid
	private List<PointSettingItem> items;

}
