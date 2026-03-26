package dev.web.api.bm_a013;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MatchKeySaveListResponse {

	/** 件数 */
    private int count;

    /** マッチキーリスト */
    private List<String> matchKeys;

}
