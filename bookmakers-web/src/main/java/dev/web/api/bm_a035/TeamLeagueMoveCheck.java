package dev.web.api.bm_a035;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.web.api.bm_a039.TeamLeagueMoveDTO;
import dev.web.repository.master.TeamLeagueMoveRepository;
import lombok.RequiredArgsConstructor;

/**
 * チームの昇格・降格の未確定（2026-11-24 追加）
 * <p>country_league_master のトリガーが記録した、チームの所属リーグの移動（team_league_move_history.confirm_flg = '0'）。
 * system_view_master の check_code = TEAM_MOVE_UNCONFIRMED（path = team-league-move, block_flg = '1'）で、
 * 「チーム昇格・降格確認設定」で確定・取り消しするまでほかの画面を開けなくする。</p>
 * @author shiraishitoshio
 */
@Component
@RequiredArgsConstructor
public class TeamLeagueMoveCheck implements SetupCheck {

	/** チェックのコード */
	public static final String CODE = "TEAM_MOVE_UNCONFIRMED";

	/** 内容に出す最大件数 */
	private static final int MAX_DETAILS = 30;

	private final TeamLeagueMoveRepository repository;

	@Override
	public String code() {
		return CODE;
	}

	@Override
	public String name() {
		return "チームの昇格・降格の未確定";
	}

	@Override
	public SetupCheckResult check() {
		List<String> details = new ArrayList<>();
		List<TeamLeagueMoveDTO> list = repository.findAll(false, 0);
		for (TeamLeagueMoveDTO m : list) {
			if (details.size() >= MAX_DETAILS) {
				details.add("ほか " + (list.size() - MAX_DETAILS) + " 件");
				break;
			}
			details.add(m.getCountry() + " / " + m.getTeam() + "：「" + m.getFromLeague() + "」→「" + m.getToLeague()
					+ "」（候補: " + label(m.getSuggestType()) + "）");
		}
		return SetupCheckResult.of(details);
	}

	private static String label(String t) {
		if ("UP".equals(t)) {
			return "昇格";
		}
		if ("DOWN".equals(t)) {
			return "降格";
		}
		return "その他";
	}
}
