package dev.web.api.bm_w001;

import java.util.List;

/**
 * FuturesResponseDTOのWrapperクラス
 * @author shiraishitoshio
 *
 */
public class FutureMatchesResponse {

	/** マッチDTO */
    private List<FuturesResponseDTO> matches;

    /** コンストラクタ */
    public FutureMatchesResponse(List<FuturesResponseDTO> matches) {
        this.matches = matches;
    }

    /** 試合データ取得 */
    public List<FuturesResponseDTO> getMatches() {
        return matches;
    }

    /** 試合データ設定 */
    public void setMatches(List<FuturesResponseDTO> matches) {
        this.matches = matches;
    }
}
