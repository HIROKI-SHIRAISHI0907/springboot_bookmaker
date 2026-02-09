package dev.application.runner;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import dev.application.analyze.bm_m001.OriginService;
import dev.application.main.service.CoreHistoryStat;
import dev.application.main.service.MainStat;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class BmJobRunner implements ApplicationRunner {

	@Autowired
	private MainStat mainStat; // 例: B006 で使う

	@Autowired
	private CoreHistoryStat coreHistoryStat; // 例: B007 で使う

	@Autowired
	private OriginService originService; // 例: B008 で使う

	@Override
	public void run(ApplicationArguments args) throws Exception {
		String mode = System.getenv().getOrDefault("BATCH_MODE", "worker");
		String job = System.getenv("BM_JOB");

		log.info("BmJobRunner start: BATCH_MODE={}, BM_JOB={}", mode, job);

		// worker じゃないなら何もしない、など運用方針に合わせる
		if (!"worker".equalsIgnoreCase(mode)) {
			log.info("Not worker mode. skip.");
			return;
		}

		if (job == null || job.isBlank()) {
			log.warn("BM_JOB is empty. skip.");
			return;
		}

		int exit = 0;

		try {
			switch (job) {
			case "B006" -> {
				log.info("Execute B006 -> MainStat");
				exit = mainStat.execute();
			}
			case "B007" -> {
				log.info("Execute B007 -> CoreHistoryStat");
				exit = coreHistoryStat.execute();
			}
			case "B008" -> {
				log.info("Execute B008 -> OriginService");
				exit = originService.execute();
			}
			default -> {
				log.warn("Unknown BM_JOB={}. skip.", job);
				exit = 0;
			}
			}
		} catch (Exception e) {
			log.error("BM_JOB failed: {}", job, e);
			exit = 9;
		}

		log.info("BmJobRunner end: BM_JOB={}, exit={}", job, exit);

		// ECS タスクを確実に終了させる（重要）
		System.exit(exit);
	}
}
