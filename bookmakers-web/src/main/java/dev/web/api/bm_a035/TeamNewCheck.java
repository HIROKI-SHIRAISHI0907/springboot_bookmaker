package dev.web.api.bm_a035;

import org.springframework.stereotype.Component;

import dev.common.constant.MasterNameConstant;
import dev.web.repository.master.SetupCheckRepository;
import lombok.RequiredArgsConstructor;

/**
 * 新規チームの未確認（チームデータ取得情報のモーダルで確認していない国・リーグ）
 * @author shiraishitoshio
 */
@Component
@RequiredArgsConstructor
public class TeamNewCheck implements SetupCheck {

	private final SetupCheckRepository repository;

	@Override
	public String code() {
		return "TEAM_NEW";
	}

	@Override
	public String name() {
		return "新規チームデータの未確認";
	}

	@Override
	public SetupCheckResult check() {
		return SetupCheckResult.of(repository.findInitialPending(MasterNameConstant.COUNTRY_LEAGUE_MASTER));
	}
}
