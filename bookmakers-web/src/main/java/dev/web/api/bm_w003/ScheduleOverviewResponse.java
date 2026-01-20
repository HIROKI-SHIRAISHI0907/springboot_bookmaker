package dev.web.api.bm_w003;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * ScheduleOverviewResponse
 * @author shiraishitoshio
 *
 */
@Data
@AllArgsConstructor
public class ScheduleOverviewResponse {
  private ScheduleMatchDTO match;
  private List<SurfaceSnapshotDTO> surfaces; // [home, away] 順に詰める
}
