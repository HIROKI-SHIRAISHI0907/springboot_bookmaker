package dev.batch.bm_b010;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.common.entity.DataEntity;

/**
 * static_data に登録する前の行チェック（B010）
 *
 * <ul>
 *   <li>{@link #invalidReason}: ホーム・アウェーのチーム名が空、または同じなら登録しない。
 *       空のまま登録すると data_category が「XXX: YYY - ラウンド 0」になり、
 *       seq_key 採番で「チーム名が空の行」同士（別々の試合）が同じ試合として扱われてしまう。</li>
 *   <li>{@link #alignMatchId}: match_id は game_link の mid= を正とする（空なら補い、違えば合わせる）。</li>
 * </ul>
 * @author shiraishitoshio
 */
public final class StaticDataRowValidator {

	/** game_link の "?mid=xxxx" */
	private static final Pattern MID = Pattern.compile("[?&]mid=([A-Za-z0-9]+)");

	private StaticDataRowValidator() {
	}

	/**
	 * 登録できない理由（登録できるなら null）
	 */
	public static String invalidReason(DataEntity entity) {
		if (entity == null) {
			return "行が null";
		}
		String home = trim(entity.getHomeTeamName());
		String away = trim(entity.getAwayTeamName());
		if (home.isEmpty() || away.isEmpty()) {
			return "チーム名が空";
		}
		if (home.equals(away)) {
			return "ホームとアウェーが同じ";
		}
		return null;
	}

	/**
	 * match_id を game_link の mid= に合わせる
	 * @return 変更した場合は変更前の match_id（空だった場合は ""）、変更しなかった場合は null
	 */
	public static String alignMatchId(DataEntity entity) {
		String mid = extractMid(entity.getGameLink());
		if (mid == null) {
			return null;
		}
		String matchId = trim(entity.getMatchId());
		if (matchId.equals(mid)) {
			return null;
		}
		entity.setMatchId(mid);
		return matchId;
	}

	/** game_link から mid を取り出す（無ければ null） */
	public static String extractMid(String gameLink) {
		if (gameLink == null) {
			return null;
		}
		Matcher m = MID.matcher(gameLink);
		return m.find() ? m.group(1) : null;
	}

	private static String trim(String s) {
		return s == null ? "" : s.trim();
	}
}
