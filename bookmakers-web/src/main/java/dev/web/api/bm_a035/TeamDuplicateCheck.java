package dev.web.api.bm_a035;

import org.springframework.stereotype.Component;

import dev.web.repository.master.SetupCheckRepository;
import lombok.RequiredArgsConstructor;

/**
 * チームの重複（同じ国で、リンクの /team/xxx/yyy/ が同じ有効なチームが2行以上）
 * リーグ所属チーム付け替え設定で解消する。
 * @author shiraishitoshio
 */
@Component
@RequiredArgsConstructor
public class TeamDuplicateCheck implements SetupCheck {

	private final SetupCheckRepository repository;

	@Override
	public String code() {
		return "TEAM_DUPLICATE";
	}

	@Override
	public String name() {
		return "チームの重複";
	}

	@Override
	public SetupCheckResult check() {
		return SetupCheckResult.of(repository.findTeamDuplicates());
	}
}
