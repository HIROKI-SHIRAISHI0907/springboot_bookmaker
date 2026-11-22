package dev.web.api.bm_a036;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 試合予定から出場チームを自動で登録する（2026-11-22 追加）。
 * <p>毎時 40 分（JST）に {@link CupCompetitionService#autoRegisterTeams(String)} を呼ぶ。
 * 日程スクレイパーは Lambda cup-schedule-runner（毎時 10 分）から起動されるので、その後に拾う。
 * 新しく登録したら大会と出場チームの JSON を出力し直すので、次の Lambda の実行で日程も取り直される。</p>
 * <p>@EnableScheduling は {@code dev.web.config.AwsStatsSchedulingConfig} にある。</p>
 * @author shiraishitoshio
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CupTeamAutoRegisterScheduler {

	/** 自動登録の登録者 ID */
	private static final String OPERATOR = "AUTO_CUP_SCHEDULE";

	private final CupCompetitionService service;

	@Scheduled(cron = "0 40 * * * *", zone = "Asia/Tokyo")
	public void run() {
		try {
			CupCompetitionResponse.Result r = service.autoRegisterTeams(OPERATOR);
			if (r.getCount() > 0) {
				log.info("[A036] 出場チームの自動登録: {}", r.getMessage());
			}
		} catch (Exception e) {
			log.error("[A036] 出場チームの自動登録に失敗しました（次の時間にもう一度行います）", e);
		}
	}
}
