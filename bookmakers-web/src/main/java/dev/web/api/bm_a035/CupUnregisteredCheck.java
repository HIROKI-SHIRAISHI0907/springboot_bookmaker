package dev.web.api.bm_a035;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.web.repository.master.CupCompetitionRepository;
import lombok.RequiredArgsConstructor;

/**
 * 大会の未登録（2026-11-22 追加）
 * <p>cup_competition_master に有効な大会が 1 件も無い。system_view_master の check_code = CUP_UNREGISTERED
 * （path = csv-upload/cup, block_flg = '1'）で、大会一括アップロードで大会を登録するまで
 * ほかの画面（setup_flg = '1' 以外）を開けなくする。</p>
 * <p>2 年目以降は大会一括アップロードは不要（大会名・URL が変わったら「大会出場チーム登録設定」の「編集」で直す）。</p>
 * @author shiraishitoshio
 */
@Component
@RequiredArgsConstructor
public class CupUnregisteredCheck implements SetupCheck {

	/** チェックのコード */
	public static final String CODE = "CUP_UNREGISTERED";

	private final CupCompetitionRepository repository;

	@Override
	public String code() {
		return CODE;
	}

	@Override
	public String name() {
		return "大会の未登録";
	}

	@Override
	public SetupCheckResult check() {
		if (repository.countCompetitions() > 0) {
			return SetupCheckResult.of(Collections.emptyList());
		}
		return SetupCheckResult.of(List.of("大会が 1 件も登録されていません。大会一括アップロードで大会名・大会URL を登録してください。"));
	}
}
