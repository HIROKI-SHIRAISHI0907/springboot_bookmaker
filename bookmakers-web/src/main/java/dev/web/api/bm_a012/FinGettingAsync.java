package dev.web.api.bm_a012;

import java.util.HashMap;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import dev.web.batch.EcsBatchTaskRunner;
import lombok.RequiredArgsConstructor;

/**
 * 非同期実行
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class FinGettingAsync {

	private final FinGettingService service;
	private final EcsBatchTaskRunner runner;

	@Async
	public void waitScrapeAndRunBatch(String batchCode) throws InterruptedException {
		// 進捗管理
		service.getProgress();

		// バッチ実行
		runner.runBatch(batchCode, new HashMap<>());
	}

}
