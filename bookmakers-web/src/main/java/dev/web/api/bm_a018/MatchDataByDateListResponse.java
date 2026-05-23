package dev.web.api.bm_a018;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class MatchDataByDateListResponse {

    /** 対象日 */
    private String targetDate;

    /** 現在ページ(1開始) */
    private int page;

    /** 1ページ件数 */
    private int size;

    /** 総件数 */
    private int count;

    /** 総ページ数 */
    private int totalPages;

    /** items */
    private List<MatchDataByDateItemResource> items = new ArrayList<>();
}
