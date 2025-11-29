package dev.web.api.bm_w003;

import java.util.List;

/**
 * OverviewResponseDTO, SurfaceSnapshotDTOのWrapperクラス
 * @author shiraishitoshio
 *
 */
public class ScheduleOverviewResponse {
    private OverviewResponseDTO match;
    private List<SurfaceSnapshotDTO> surfaces;

    /**
	 * コンストラクタ
	 * @author shiraishitoshio
	 *
	 */
    public ScheduleOverviewResponse(OverviewResponseDTO match, List<SurfaceSnapshotDTO> surfaces) {
        this.match = match;
        this.surfaces = surfaces;
    }

    /**
	 * 試合レスポンス取得
	 * @author shiraishitoshio
	 *
	 */
    public OverviewResponseDTO getMatch() {
        return match;
    }

    /**
	 * 試合リクエスト設定
	 * @author shiraishitoshio
	 *
	 */
    public void setMatch(OverviewResponseDTO match) {
        this.match = match;
    }

    /**
	 * 概要レスポンス取得
	 * @author shiraishitoshio
	 *
	 */
    public List<SurfaceSnapshotDTO> getSurfaces() {
        return surfaces;
    }

    /**
	 * 概要リクエスト設定
	 * @author shiraishitoshio
	 *
	 */
    public void setSurfaces(List<SurfaceSnapshotDTO> surfaces) {
        this.surfaces = surfaces;
    }
}
