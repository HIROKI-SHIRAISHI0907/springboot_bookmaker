package dev.web.api.bm_a039;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import dev.web.api.dashboard.auth.DashboardAuthResolver;
import dev.web.repository.master.TeamColorMasterWebRepository;
import dev.web.repository.master.TeamLeagueMoveRepository;
import dev.web.repository.user.FavoriteLeagueMoveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * チーム昇格・降格確認設定（BM_A039。2026-11-24 追加）
 *
 * <ul>
 *   <li>チームの所属リーグが変わると、country_league_master のトリガーが team_league_move_history に未確定で記録する
 *       （B004 の付け替え・シーズン終了後の生き返り、どれでも）。候補（昇格・降格）はリーグレベルから決める。</li>
 *   <li>{@link #confirm}: 昇格・降格・その他を決めて確定する。確定したら
 *       <ol>
 *         <li>お気に入り（user DB）のチームを移動先リーグへ付け替える</li>
 *         <li>大会出場チームのリーグを移動先へ</li>
 *         <li>移動したチームの行のリーグレベルを移動先リーグに合わせる</li>
 *         <li>チームカラーを同期（link が同じなら移動前のカラーを引き継ぐ）</li>
 *       </ol>
 *       お気に入りは別 DB のため先に付け替える（途中で失敗しても、もう一度確定すれば続きから付け替わる）。</li>
 *   <li>{@link #cancel}: 誤検知を取り消す（チームマスタは変えない。記録を '9' にするだけ）。</li>
 * </ul>
 * 未確定が残っていると、未対応チェック TEAM_MOVE_UNCONFIRMED で他の管理画面が開けない。
 * @author shiraishitoshio
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamLeagueMoveService {

	/** 確定のときに選べる種類 */
	public static final Set<String> TYPES = Set.of("UP", "DOWN", "OTHER");

	/** 全件表示のときの、確定・取り消し済みの最大件数 */
	private static final int HISTORY_LIMIT = 1000;

	private static final String DEFAULT_UPDATE_ID = "ADMIN";

	private final TeamLeagueMoveRepository repository;

	private final FavoriteLeagueMoveRepository favoriteRepository;

	private final TeamColorMasterWebRepository teamColorRepository;

	private final DashboardAuthResolver authResolver;

	public List<TeamLeagueMoveDTO> findAll(boolean all) {
		return repository.findAll(all, HISTORY_LIMIT);
	}

	/** 確定（1 件でも入力エラーなら何もしない） */
	public TeamLeagueMoveResponse confirm(TeamLeagueMoveConfirmRequest req, boolean all) {
		Map<Long, String> types = new LinkedHashMap<>();
		for (TeamLeagueMoveConfirmRequest.Item it : items(req)) {
			String t = it.getMoveType() == null ? "" : it.getMoveType().trim().toUpperCase();
			if (it.getId() == null) {
				throw new IllegalArgumentException("id がありません");
			}
			if (!TYPES.contains(t)) {
				throw new IllegalArgumentException("id=" + it.getId() + ": 種類は 昇格・降格・その他 から選んでください");
			}
			types.put(it.getId(), t);
		}

		String op = updateId();
		TeamLeagueMoveResponse res = new TeamLeagueMoveResponse();
		boolean colorSync = false;
		for (Map.Entry<Long, String> e : types.entrySet()) {
			TeamLeagueMoveDTO m = repository.findUnconfirmed(e.getKey());
			if (m == null) {
				res.getWarnings().add("id=" + e.getKey() + ": 確定済み・取り消し済みか、見つかりません（画面を再取得してください）");
				continue;
			}
			// 1. お気に入り（user DB。別トランザクション）
			int fav = favoriteRepository.moveTeam(m.getCountry(), m.getTeam(), m.getFromLeague(), m.getToLeague(), op);
			// 2〜4. 大会出場チーム・リーグレベル・記録（soccer_bm_master。1 トランザクション）
			int[] cup = { 0 };
			int[] upd = { 0 };
			repository.inTransaction(() -> {
				cup[0] = repository.moveCupTeams(m.getCountry(), m.getTeam(), m.getFromLeague(), m.getToLeague(), op);
				repository.syncTeamLevel(m.getTeamId(), m.getCountry(), m.getToLeague(), op);
				upd[0] = repository.updateFlg(m.getId(), e.getValue(), TeamLeagueMoveRepository.FLG_CONFIRMED, op);
			});
			res.setFavorites(res.getFavorites() + fav);
			res.setCupTeams(res.getCupTeams() + cup[0]);
			res.setUpdated(res.getUpdated() + upd[0]);
			colorSync = true;
			log.info("[BM_A039] 確定 id={}, {} / {}: {} → {} ({}) お気に入り={}, 大会={}",
					m.getId(), m.getCountry(), m.getTeam(), m.getFromLeague(), m.getToLeague(), e.getValue(), fav, cup[0]);
		}
		if (colorSync) {
			try {
				log.info("[BM_A039] チームカラー同期 {}", teamColorRepository.syncWithTeamMaster());
			} catch (Exception ex) {
				// カラーはチームカラー画面を開いたとき・B004 の最後にも同期するので、ここでは止めない
				log.warn("[BM_A039] チームカラー同期に失敗しました e={}", ex.toString(), ex);
				res.getWarnings().add("チームカラーの同期に失敗しました（チームカラー情報反映設定を開くと同期されます）");
			}
		}
		res.setItems(findAll(all));
		return res;
	}

	/** 取り消し（誤検知） */
	public TeamLeagueMoveResponse cancel(TeamLeagueMoveConfirmRequest req, boolean all) {
		List<TeamLeagueMoveConfirmRequest.Item> list = items(req);
		String op = updateId();
		TeamLeagueMoveResponse res = new TeamLeagueMoveResponse();
		int[] n = { 0 };
		repository.inTransaction(() -> {
			for (TeamLeagueMoveConfirmRequest.Item it : list) {
				if (it.getId() == null) {
					continue;
				}
				int u = repository.updateFlg(it.getId(), null, TeamLeagueMoveRepository.FLG_CANCELED, op);
				if (u == 0) {
					res.getWarnings().add("id=" + it.getId() + ": 確定済み・取り消し済みか、見つかりません");
				}
				n[0] += u;
			}
		});
		res.setUpdated(n[0]);
		res.setItems(findAll(all));
		return res;
	}

	private static List<TeamLeagueMoveConfirmRequest.Item> items(TeamLeagueMoveConfirmRequest req) {
		if (req == null || req.getItems() == null || req.getItems().isEmpty()) {
			throw new IllegalArgumentException("対象を選んでください");
		}
		return req.getItems();
	}

	private String updateId() {
		String name = authResolver.currentUserId();
		if (name == null || name.isBlank()) {
			return DEFAULT_UPDATE_ID;
		}
		String s = name.trim();
		return s.length() > 100 ? s.substring(0, 100) : s;
	}
}
