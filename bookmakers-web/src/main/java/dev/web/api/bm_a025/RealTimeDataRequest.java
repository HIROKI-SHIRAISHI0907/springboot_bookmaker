package dev.web.api.bm_a025;

import java.util.List;

import lombok.Data;

/**
 * RealTimeDataRequestリクエスト
 * @author shiraishitoshio
 *
 */
@Data
public class RealTimeDataRequest {

	/** リスト */
	private List<RealTimeDataSubDTO> requestDTO;

}
