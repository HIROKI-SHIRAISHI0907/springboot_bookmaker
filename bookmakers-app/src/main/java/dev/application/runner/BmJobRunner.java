package dev.application.runner;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import dev.application.analyze.bm_m001.OriginService;
import dev.application.analyze.bm_m097.AnalyzeManualStat;
import dev.application.main.service.CoreHistoryStat;
import dev.application.main.service.MainStat;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class BmJobRunner implements ApplicationRunner {

	@Autowired
	private MainStat mainStat;

	@Autowired
	private CoreHistoryStat coreHistoryStat;

	@Autowired
	private OriginService originService;

	@Autowired
	private AnalyzeManualStat analyzeManualStat;

	@Override
	public void run(ApplicationArguments args) throws Exception {
		String mode = System.getenv().getOrDefault("BATCH_MODE", "worker");
		String job = System.getenv("BM_JOB");
		String country = System.getenv("BM_COUNTRY");
		String league = System.getenv("BM_LEAGUE");

		log.info("BmJobRunner start: BATCH_MODE={}, BM_JOB={}, BM_COUNTRY={}, BM_LEAGUE={}",
				mode, job, country, league);

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
			case "B014" -> {
				log.info("Execute B014 -> MainStat. country={}, league={}", country, league);
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
			case "B012" -> {
				log.info("Execute B012 -> AnalyzeManualStat");
				exit = analyzeManualStat.manualStat();
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
		System.exit(exit);
	}
}
