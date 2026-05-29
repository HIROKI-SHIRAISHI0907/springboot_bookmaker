package dev.web.api.bm_w017;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatCountryLeagueOptionWrapper {
	/** 国 */
	private String country;
	/** リーグ群 */
	private List<String> leagues;
}
