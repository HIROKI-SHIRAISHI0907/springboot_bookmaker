package dev.web.api.bm_a039;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * POST /api/admin/team-league-move/confirm  { items: [{ id, moveType }] }
 * POST /api/admin/team-league-move/cancel   { items: [{ id }] }（moveType は使わない）
 * @author shiraishitoshio
 */
@Data
public class TeamLeagueMoveConfirmRequest {

	private List<Item> items = new ArrayList<>();

	@Data
	public static class Item {

		private Long id;

		/** UP / DOWN / OTHER */
		private String moveType;
	}
}
