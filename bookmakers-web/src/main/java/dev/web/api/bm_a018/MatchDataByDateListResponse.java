package dev.web.api.bm_a018;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class MatchDataByDateListResponse {

	/** 対象日 */
    private String targetDate;

    /** 件数 */
    private int count;

    /** items */
    private List<MatchDataByDateItemResource> items = new ArrayList<>();

}
