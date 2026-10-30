package dev.web.api.bm_a035;

import org.springframework.stereotype.Component;

import dev.common.constant.MasterNameConstant;
import dev.web.repository.master.SetupCheckRepository;
import lombok.RequiredArgsConstructor;

/**
 * 新規シーズンの未確認（シーズンデータ取得情報のモーダルで確認していない国・リーグ）
 * @author shiraishitoshio
 */
@Component
@RequiredArgsConstructor
public class SeasonNewCheck implements SetupCheck {

	private final SetupCheckRepository repository;

	@Override
	public String code() {
		return "SEASON_NEW";
	}

	@Override
	public String name() {
		return "新規シーズンデータの未確認";
	}

	@Override
	public SetupCheckResult check() {
		return SetupCheckResult.of(repository.findInitialPending(MasterNameConstant.COUNTRY_LEAGUE_SEASON_MASTER));
	}
}
