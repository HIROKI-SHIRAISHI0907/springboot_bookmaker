package dev.web.api.bm_a038;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 統計詳細データ参照: 検索結果
 * @author shiraishitoshio
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchDetailReferenceResponse {

	/** 条件に合う試合の数 */
	private int total;

	private int offset;

	private int limit;

	private List<MatchDetailReferenceDTO> items = new ArrayList<>();
}
