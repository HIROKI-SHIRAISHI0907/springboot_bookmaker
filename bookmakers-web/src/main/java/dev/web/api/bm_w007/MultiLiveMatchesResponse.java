package dev.web.api.bm_w007;

import java.util.List;

import lombok.Data;

/**
 * LiveMatchesAPI 複数試合レスポンス
 */
@Data
public class MultiLiveMatchesResponse {

	/** 試合一覧 */
    private List<LiveMatchDTO> matches;

    /** 件数 */
    private int count;

}
