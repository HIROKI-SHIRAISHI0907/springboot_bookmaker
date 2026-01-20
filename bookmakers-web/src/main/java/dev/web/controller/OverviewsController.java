// src/main/java/dev/web/controller/OverviewsController.java
package dev.web.controller;

import java.util.List;

import org.apache.coyote.BadRequestException;
import org.apache.ibatis.javassist.NotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w003.OverviewAPIService;
import dev.web.api.bm_w003.OverviewResponseDTO;
import dev.web.api.bm_w003.ScheduleOverviewResponse;

/**
 * OverviewsControllerクラス
 *  - 月次サマリ: GET /api/overview/{country}/{league}/{team}
 *  - 試合概要:   GET /api/{country}/{league}/match/{seq}
 */
@RestController
@RequestMapping("/api/overview")
public class OverviewsController {

  private final OverviewAPIService overviewAPIService;

  public OverviewsController(OverviewAPIService overviewAPIService) {
    this.overviewAPIService = overviewAPIService;
  }

  @GetMapping("/{country}/{league}/{team}")
  public ResponseEntity<?> getMonthlyOverview(
      @PathVariable String country,
      @PathVariable String league,
      @PathVariable("team") String teamSlug
  ) throws BadRequestException, NotFoundException {

    List<OverviewResponseDTO> items = overviewAPIService.getMonthlyOverview(country, league, teamSlug);
    return ResponseEntity.ok(new OverviewListResponse(items));
  }

  @GetMapping("/{country}/{league}/match/{seq}")
  public ResponseEntity<?> getScheduleOverview(
      @PathVariable String country,
      @PathVariable String league,
      @PathVariable long seq
  ) throws BadRequestException, NotFoundException {

    ScheduleOverviewResponse result = overviewAPIService.getScheduleOverview(country, league, seq);
    return ResponseEntity.ok(new ScheduleOverviewResponse(result.getMatch(), result.getSurfaces()));
  }

  static class OverviewListResponse {
    private List<OverviewResponseDTO> items;
    public OverviewListResponse(List<OverviewResponseDTO> items) { this.items = items; }
    public List<OverviewResponseDTO> getItems() { return items; }
    public void setItems(List<OverviewResponseDTO> items) { this.items = items; }
  }
}
