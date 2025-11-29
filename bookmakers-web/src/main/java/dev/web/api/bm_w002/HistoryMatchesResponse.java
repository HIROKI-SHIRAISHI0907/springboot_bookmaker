package dev.web.api.bm_w002;

import java.util.List;

/**
 * PastMatchDTOのWrapperクラス
 * @author shiraishitoshio
 *
 */
public class HistoryMatchesResponse {

	/**
	 * 該当試合レスポンス
	 * @author shiraishitoshio
	 *
	 */
    public static class MatchesResponse {
        private List<HistoryResponseDTO> matches;

        public MatchesResponse(List<HistoryResponseDTO> matches) {
            this.matches = matches;
        }

        public List<HistoryResponseDTO> getMatches() { return matches; }
        public void setMatches(List<HistoryResponseDTO> matches) { this.matches = matches; }
    }

    /**
     * 詳細レスポンス
     * @author shiraishitoshio
     *
     */
    public static class DetailResponse {
        private HistoryDetailResponseDTO detail;

        public DetailResponse(HistoryDetailResponseDTO detail) {
            this.detail = detail;
        }

        public HistoryDetailResponseDTO getDetail() { return detail; }
        public void setDetail(HistoryDetailResponseDTO detail) { this.detail = detail; }
    }

    /**
     * エラーレスポンス
     * @author shiraishitoshio
     *
     */
    public static class ErrorResponse {
        private String message;
        private String detail;

        public ErrorResponse(String message, String detail) {
            this.message = message;
            this.detail = detail;
        }

        public String getMessage() { return message; }
        public String getDetail() { return detail; }
    }
}
