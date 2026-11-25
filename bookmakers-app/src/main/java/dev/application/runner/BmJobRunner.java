package dev.application.runner;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import dev.application.analyze.bm_m097.AnalyzeManualStat;
import dev.application.main.service.MainStat;
import dev.application.analyze.bm_b011.ExportCsvBatch;
import dev.common.constant.BatchConstant;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class BmJobRunner implements ApplicationRunner {

	@Autowired
	private MainStat mainStat;

	@Autowired
	private AnalyzeManualStat analyzeManualStat;

	/** B011 統計CSVデータ生成（2026-11-20 bookmakers-batch から移動） */
	@Autowired
	private ExportCsvBatch exportCsvBatch;

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
			case "B008" -> {
				// 2026-11-20: B008（開催中データ登録）は bookmakers-batch（@Service("B008") RealDataBatch）に移動した
				log.warn("B008 は bookmakers-batch に移動しました（bookmakers-app では実行しません）。skip.");
				exit = 0;
			}
			case "B011" -> {
				// 2026-11-20: bookmakers-batch から移動（統計用のプロジェクトで動かす）
				log.info("Execute B011 -> ExportCsvBatch");
				int rc = exportCsvBatch.execute(false);
				exit = (rc == BatchConstant.BATCH_SUCCESS) ? 0 : 1;
				// 2026-11-24: B011 が終わったら、同じタスクで続けて B006（統計CSV取り込み）を動かす。
				// 5:00 の B006 は B011 が実行中だと起動しないため、B011 が長引く日が続くと取り込まれなくなる。
				// B011 が失敗しても、作れた CSV は取り込む（B006 は未取り込みの CSV だけを対象にする）。
				// 止めたいときは環境変数 B011_CHAIN_B006=false。
				if (chainB006AfterB011()) {
					log.info("Execute B006 -> MainStat (B011 の続き。B011 exit={})", exit);
					int b006 = runB006AfterB011();
					log.info("B006 (B011 の続き) end: exit={}", b006);
					exit = Math.max(exit, b006);
					// 2026-11-25: 続けて B018（終了済データだけの試合の反映）。日付で絞らず、未反映のものを過去分も含めて全部。
					// 止めたいときは B011_CHAIN_B018=false。
					if (chainEnabled("B011_CHAIN_B018")) {
						log.info("Execute B018 -> AnalyzeManualStat (B011・B006 の続き)");
						int b018 = runAfterB011("B018", () -> analyzeManualStat.manualStat());
						log.info("B018 (B011 の続き) end: exit={}", b018);
						exit = Math.max(exit, b018);
					} else {
						log.info("B011_CHAIN_B018=false のため、B018 は続けて動かしません");
					}
				} else {
					log.info("B011_CHAIN_B006=false のため、B006 は続けて動かしません");
				}
			}
			case "B018" -> {
				// 2026-11-24: B012 から B018 に変更（bookmakers-batch の B012 FinDataJsonOutputs と番号が重なっていたため）
				log.info("Execute B018 -> AnalyzeManualStat");
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

	/** B011 の後に続けて B006 を動かすか（環境変数 B011_CHAIN_B006。未設定は true） */
	private static boolean chainB006AfterB011() {
		return chainEnabled("B011_CHAIN_B006");
	}

	/** 環境変数が false 以外（未設定を含む）なら true */
	private static boolean chainEnabled(String name) {
		String v = System.getenv(name);
		return v == null || v.isBlank() || !"false".equalsIgnoreCase(v.trim());
	}

	@FunctionalInterface
	private interface Job {
		int run() throws Exception;
	}

	/** B011 の続きのジョブ。例外は B011 の結果を消さないようにここで受ける（exit 9） */
	private int runAfterB011(String name, Job job) {
		try {
			return job.run();
		} catch (Exception e) {
			log.error("{} (B011 の続き) failed", name, e);
			return 9;
		}
	}

	/** B011 の続きの B006。例外は B011 の結果を消さないようにここで受ける（exit 9） */
	private int runB006AfterB011() {
		try {
			return mainStat.execute();
		} catch (Exception e) {
			log.error("B006 (B011 の続き) failed", e);
			return 9;
		}
	}
}
