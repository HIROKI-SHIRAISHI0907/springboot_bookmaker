package dev.web.api.bm_a035;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.web.repository.master.CupCompetitionRepository;
import lombok.RequiredArgsConstructor;

/**
 * 大会出場チームの未登録（2026-11-22 追加）
 * <p>日程を取る大会（cup_competition_master.scrape_flg = '1'）のうち、出場チーム
 * （cup_competition_team_master）が 1 つも無い大会。system_view_master の check_code = CUP_TEAM_UNSET
 * （path = cup-competition, block_flg = '1'）で、出場チームを登録するまでほかの画面を開けなくする。</p>
 * <p>その大会を取らない場合は、「大会出場チーム登録設定」で「日程の取得」を「取らない」にするか削除すれば解消する。</p>
 * @author shiraishitoshio
 */
@Component
@RequiredArgsConstructor
public class CupTeamUnsetCheck implements SetupCheck {

	/** チェックのコード */
	public static final String CODE = "CUP_TEAM_UNSET";

	private final CupCompetitionRepository repository;

	@Override
	public String code() {
		return CODE;
	}

	@Override
	public String name() {
		return "大会出場チームの未登録";
	}

	@Override
	public SetupCheckResult check() {
		List<String> details = new ArrayList<>();
		for (String name : repository.findCompetitionNamesWithoutTeams()) {
			details.add(name + "：出場チームが登録されていません。");
		}
		return SetupCheckResult.of(details);
	}
}
