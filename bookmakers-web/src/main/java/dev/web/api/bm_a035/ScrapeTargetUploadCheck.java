package dev.web.api.bm_a035;

import org.springframework.stereotype.Component;

import dev.web.repository.master.SetupPendingRepository;
import lombok.RequiredArgsConstructor;

/**
 * スクレイピング対象データの再アップロード待ち（2026-11-03 追加）
 * <p>リーグ所属チーム付け替え設定でチームを付け替えた・外したあと、スクレイピング対象データ設定の
 * 「対象データアップロード実行」（B009）を押すまで未対応にする。
 * system_view_master の check_code = SCRAPE_TARGET_UPLOAD（path = manual/data/target, block_flg = '1'）で、
 * スクレイピング対象データ設定の画面以外（setup_flg = '1' 以外）を開けなくする。</p>
 * @author shiraishitoshio
 */
@Component
@RequiredArgsConstructor
public class ScrapeTargetUploadCheck implements SetupCheck {

	private final SetupPendingRepository repository;

	@Override
	public String code() {
		return SetupPendingRepository.SCRAPE_TARGET_UPLOAD;
	}

	@Override
	public String name() {
		return "所属チームの変更後の「対象データアップロード実行」が未実行";
	}

	@Override
	public SetupCheckResult check() {
		return SetupCheckResult.of(repository.findPendingDetails(code()));
	}
}
