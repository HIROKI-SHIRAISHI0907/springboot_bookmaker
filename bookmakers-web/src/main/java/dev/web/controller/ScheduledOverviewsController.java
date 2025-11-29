package dev.web.controller;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w004.ScheduledOverviewsMatchDTO;
import dev.web.api.bm_w004.ScheduledOverviewsResponse;
import dev.web.api.bm_w004.ScheduledSurfaceSnapshotDTO;
import dev.web.repository.ScheduledOverviewsRepository;

/**
 * ScheduledOverviewsAPI コントローラー
 *
 * エンドポイント:
 *   GET /api/{country}/{league}/scheduled-overviews/{seq}?home=...&away=...
 *
 * フロント側:
 *   fetchScheduleOverview(country, league, seq, { home, away })
 *   （src/api/scheduled_overviews.ts）
 *
 * を想定した実装。
 *
 * @author shiraishitoshio
 */
@RestController
@RequestMapping("/api")
public class ScheduledOverviewsController {

	private final ScheduledOverviewsRepository scheduledOverviewsRepository;

	public ScheduledOverviewsController(ScheduledOverviewsRepository scheduledOverviewsRepository) {
		this.scheduledOverviewsRepository = scheduledOverviewsRepository;
	}

	/**
	 * 開催予定 詳細取得 API
	 *
	 * GET /api/{country}/{league}/scheduled-overviews/{seq}?home=...&away=...
	 *
	 * クエリパラメータ home / away のどちらかは必須（両方指定も可）。
	 * surface_overview から、指定チームの月別データを集約したスナップショットを返す。
	 */
	@GetMapping("/{country}/{league}/scheduled-overviews/{seq}")
	public ResponseEntity<?> getScheduledOverview(
			@PathVariable("country") String countryRaw,
			@PathVariable("league") String leagueRaw,
			@PathVariable("seq") String seqStr,
			@RequestParam(name = "home", required = false) String homeParam,
			@RequestParam(name = "away", required = false) String awayParam) {

		// パスパラメータのデコード（フロントは encodeURIComponent を使用しているため）
		String country = safeDecode(countryRaw);
		String league = safeDecode(leagueRaw);

		long seq;
		try {
			seq = Long.parseLong(seqStr);
		} catch (NumberFormatException e) {
			return ResponseEntity.badRequest()
					.body(new SimpleMessage("country/league/seq are required"));
		}

		String homeTeam = trimSafe(homeParam);
		String awayTeam = trimSafe(awayParam);

		if (!StringUtils.hasText(country) || !StringUtils.hasText(league) || seqStr.isEmpty()) {
			return ResponseEntity.badRequest()
					.body(new SimpleMessage("country/league/seq are required"));
		}
		if (!StringUtils.hasText(homeTeam) && !StringUtils.hasText(awayTeam)) {
			return ResponseEntity.badRequest()
					.body(new SimpleMessage("home or away query parameter is required (at least one)"));
		}

		try {
			// home / away それぞれ必要なら snapshot を取得
			ScheduledSurfaceSnapshotDTO homeSnap = null;
			ScheduledSurfaceSnapshotDTO awaySnap = null;

			if (StringUtils.hasText(homeTeam)) {
				homeSnap = scheduledOverviewsRepository.findLatestSnapshot(country, league, homeTeam, "home");
			}
			if (StringUtils.hasText(awayTeam)) {
				awaySnap = scheduledOverviewsRepository.findLatestSnapshot(country, league, awayTeam, "away");
			}

			if (homeSnap == null && awaySnap == null) {
				// フロント側の runtime ガードと揃えたメッセージ
				return ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(new SimpleMessage("no surface_overview snapshot for given team(s)"));
			}

			// 年月決定（home/away のうち、最新の year/month を採用）
			Integer latestYear = null;
			Integer latestMonth = null;

			List<ScheduledSurfaceSnapshotDTO> snapsForYm = new ArrayList<>();
			if (homeSnap != null)
				snapsForYm.add(homeSnap);
			if (awaySnap != null)
				snapsForYm.add(awaySnap);

			// ★ Optional に一旦出してから通常の if で代入
			var latestOpt = snapsForYm.stream()
					.filter(s -> s.getGameYear() != null && s.getGameMonth() != null)
					.max(Comparator.comparingInt((ScheduledSurfaceSnapshotDTO s) -> s.getGameYear())
							.thenComparingInt(ScheduledSurfaceSnapshotDTO::getGameMonth));

			if (latestOpt.isPresent()) {
				ScheduledSurfaceSnapshotDTO latest = latestOpt.get();
				latestYear = latest.getGameYear();
				latestMonth = latest.getGameMonth();
			}

			// match DTO 組み立て
			ScheduledOverviewsMatchDTO match = new ScheduledOverviewsMatchDTO();
			match.setSeq(seq);
			match.setCountry(country);
			match.setLeague(league);
			match.setHomeTeam(homeSnap != null ? homeSnap.getTeam() : (homeTeam != null ? homeTeam : ""));
			match.setAwayTeam(awaySnap != null ? awaySnap.getTeam() : (awayTeam != null ? awayTeam : ""));
			match.setFutureTime(null); // 既存 Node 実装に合わせて null 固定
			match.setRoundNo(null); // 将来拡張用
			match.setGameYear(latestYear);
			match.setGameMonth(latestMonth);

			// surfaces: null でない側だけ返却（通常は [home, away]）
			List<ScheduledSurfaceSnapshotDTO> surfaces = new ArrayList<>();
			if (homeSnap != null)
				surfaces.add(homeSnap);
			if (awaySnap != null)
				surfaces.add(awaySnap);

			ScheduledOverviewsResponse body = new ScheduledOverviewsResponse(match, surfaces);
			return ResponseEntity.ok(body);

		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new SimpleMessage("server error: " + e.getMessage()));
		}
	}

	// ----------------- private helpers -----------------

	private String safeDecode(String s) {
		if (s == null)
			return null;
		try {
			return URLDecoder.decode(s, StandardCharsets.UTF_8);
		} catch (IllegalArgumentException ex) {
			return s;
		}
	}

	private String trimSafe(String s) {
		return s == null ? null : s.trim();
	}

	/**
	 * フロント側の「{ message: string }」と互換の簡易 DTO。
	 * fetchScheduleOverview の runtime 判定と合わせるため。
	 */
	public static class SimpleMessage {
		/** メッセージ */
		private final String message;

		public SimpleMessage(String message) {
			this.message = message;
		}

		public String getMessage() {
			return message;
		}
	}
}
