package dev.common.getstatinfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.FutureEntity;
import dev.common.find.dto.FindBookInputDTO;
import dev.common.find.dto.FindBookOutputDTO;
import dev.common.findcsv.FindStat;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadFuture;
import dev.common.readfile.dto.ReadFileOutputDTO;

/**
 * 未来情報取得管理クラス
 * @author shiraishitoshio
 *
 */
@Component
public class GetFutureInfo {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = GetFutureInfo.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = GetFutureInfo.class.getSimpleName();

	/** Configクラス */
	@Autowired
	private PathConfig config;

	/**
	 * パス(/Users/shiraishitoshio/bookmaker/の予定)
	 */
	private String PATH;

	/**
	 * 統計データCsv読み取りクラス
	 */
	@Autowired
	private FindStat findStatCsv;

	/**
	 * ファイル読み込みクラス
	 */
	@Autowired
	private ReadFuture readFuture;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 取得メソッド
	 */
	public Map<String, List<FutureEntity>> getData() {
	    final String METHOD_NAME = "getData";
	    PATH = config.getFutureFolder();

	    long startTime = System.nanoTime();

	    FindBookInputDTO findBookInputDTO = setBookInputDTO();
	    FindBookOutputDTO findBookOutputDTO = this.findStatCsv.execute(findBookInputDTO);

	    if (!BookMakersCommonConst.NORMAL_CD.equals(findBookOutputDTO.getResultCd())) {
	        this.manageLoggerComponent.createBusinessException(
	            findBookOutputDTO.getExceptionProject(),
	            findBookOutputDTO.getExceptionClass(),
	            findBookOutputDTO.getExceptionMethod(),
	            findBookOutputDTO.getErrMessage(),
	            findBookOutputDTO.getThrowAble());
	    }

	    List<String> fileStatList = findBookOutputDTO.getBookList();
	    Map<String, List<FutureEntity>> resultMap = new HashMap<>();

	    if (fileStatList == null || fileStatList.isEmpty()) {
	        this.manageLoggerComponent.debugInfoLog(
	            PROJECT_NAME, CLASS_NAME, METHOD_NAME, "データなし", "GetFutureInfo");
	        return resultMap;
	    }

	    final int MAX_THREADS = 8;
	    int poolSize = Math.min(MAX_THREADS, fileStatList.size());
	    ExecutorService executor = Executors.newFixedThreadPool(poolSize);

	    try {
	        List<Callable<ReadFileOutputDTO>> tasks = new ArrayList<>(fileStatList.size());
	        for (String file : fileStatList) {
	            tasks.add(() -> this.readFuture.getFileBody(file));
	        }

	        // タイムアウトは必要に応じて調整
	        List<Future<ReadFileOutputDTO>> futures = executor.invokeAll(tasks, 60, TimeUnit.SECONDS);

	        for (Future<ReadFileOutputDTO> f : futures) {
	            if (f.isCancelled()) {
	                this.manageLoggerComponent.debugErrorLog(
	                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, "ReadFuture timeout/cancel", null);
	                continue;
	            }
	            ReadFileOutputDTO dto = f.get();
	            List<FutureEntity> entity = dto.getFutureList();
	            if (entity == null || entity.isEmpty()) continue;

	            String file = entity.get(0).getFile();
	            resultMap.computeIfAbsent(file, k -> new ArrayList<>()).addAll(entity);
	        }

	    } catch (InterruptedException ie) {
	        Thread.currentThread().interrupt();
	        this.manageLoggerComponent.createBusinessException(
	            PROJECT_NAME, CLASS_NAME, METHOD_NAME, "スレッド中断", ie);

	    } catch (Exception e) {
	        this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, e);
	        this.manageLoggerComponent.createBusinessException(
	            PROJECT_NAME, CLASS_NAME, METHOD_NAME, "Future読み込みエラー", e);

	    } finally {
	        executor.shutdown();
	        try {
	            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
	                executor.shutdownNow();
	            }
	        } catch (InterruptedException ie) {
	            executor.shutdownNow();
	            Thread.currentThread().interrupt();
	        }
	    }

	    long durationMs = (System.nanoTime() - startTime) / 1_000_000;
	    System.out.println("時間: " + durationMs);
	    return resultMap;
	}

	/**
	 * 読み取りinputDTOに設定する
	 * @return
	 */
	private FindBookInputDTO setBookInputDTO() {
		FindBookInputDTO findBookInputDTO = new FindBookInputDTO();
		findBookInputDTO.setDataPath(PATH);
		findBookInputDTO.setCopyFlg(false);
		findBookInputDTO.setGetBookFlg(true);
		String[] containsList = new String[6];
		containsList[0] = "breakfile";
		containsList[1] = "all.csv";
		containsList[3] = "conditiondata/";
		containsList[4] = "python_analytics/";
		containsList[5] = "average_stats/";
		findBookInputDTO.setContainsList(containsList);
		findBookInputDTO.setCsvNumber("0");
		findBookInputDTO.setPrefixFile(BookMakersCommonConst.FUTURE_);
		findBookInputDTO.setSuffixFile(BookMakersCommonConst.CSV);
		return findBookInputDTO;
	}

}
