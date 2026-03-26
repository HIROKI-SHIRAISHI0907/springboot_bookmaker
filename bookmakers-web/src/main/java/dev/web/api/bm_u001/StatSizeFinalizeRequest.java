package dev.web.api.bm_u001;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * StatSizeFinalizeRequest
 * @author shiraishitoshio
 *
 */
@Data
public class StatSizeFinalizeRequest {

	/** リスト */
	@NotEmpty(message = "subListは1件以上必要です。")
	private List<SubInput> subList;

}
