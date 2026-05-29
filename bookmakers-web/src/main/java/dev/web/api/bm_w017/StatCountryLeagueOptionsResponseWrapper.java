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
public class StatCountryLeagueOptionsResponseWrapper {
	/** 国群 */
	private List<StatCountryLeagueOptionWrapper> countries;
}
