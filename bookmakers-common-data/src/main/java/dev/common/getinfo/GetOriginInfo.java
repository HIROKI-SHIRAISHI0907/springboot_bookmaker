package dev.common.getinfo;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.DataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadOrigin;
import dev.common.readfile.dto.ReadFileOutputDTO;
import dev.common.s3.S3Operator;

/**
 * 起源データ取得管理クラス
 * @author shiraishitoshio
 *
 */
@Component
public class GetOriginInfo {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = GetOriginInfo.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = GetOriginInfo.class.getSimpleName();

	/** 取得バケット正規表現 */
	private static final Pattern OUTPUTS_CSV_KEY =
	        Pattern.compile("^\\d{4}-\\d{2}-\\d{2}/.*\\.csv$");

	/** S3オペレーター */
	@Autowired
	private S3Operator s3Operator;

	/** パス設定 */
	@Autowired
	private PathConfig config;

	/** ファイル読み込みクラス */
	@Autowired
	private ReadOrigin readOrigin;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 取得メソッド
	 */
	public Map<String, List<DataEntity>> getData() {
		final String METHOD_NAME = "getData";

	    String bucket = config.getS3BucketsOutputs();

	    // 最終更新時間を昇順に取得
	    List<String> fileStatList = s3Operator
	            .listAllDateCsvObjectsSortedByLastModifiedAsc(bucket, OUTPUTS_CSV_KEY)
	            .stream()
	            .map(o -> o.key())
	            .collect(Collectors.toList());

	    Map<String, List<DataEntity>> resultMap = new HashMap<>();

	    if (fileStatList.isEmpty()) {
	    	String msgCd = MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP;
	        this.manageLoggerComponent.debugInfoLog(
	            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, "データなし(S3)");
	        return resultMap;
	    }

	    final int MAX_THREADS = 8;
	    int poolSize = Math.min(MAX_THREADS, fileStatList.size());
	    ExecutorService executor = Executors.newFixedThreadPool(poolSize);

	    try {
	        List<Callable<ReadFileOutputDTO>> tasks = new ArrayList<>(fileStatList.size());

	        for (String key : fileStatList) {
	            tasks.add(() -> {
	                try (InputStream is = s3Operator.download(bucket, key)) {
	                    // ★ ReadFuture 側に「InputStreamから読む」メソッドを用意
	                    return readOrigin.getFileBodyFromStream(is, key);
	                }
	            });
	        }

	        List<Future<ReadFileOutputDTO>> futures = executor.invokeAll(tasks, 60, TimeUnit.SECONDS);

	        for (Future<ReadFileOutputDTO> f : futures) {
	            if (f.isCancelled()) {
	            	String msgCd = MessageCdConst.MCD00003E_EXECUTION_SKIP;
	                this.manageLoggerComponent.debugErrorLog(
	                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null, "ReadOriginS3 timeout/cancel");
	                continue;
	            }
	            ReadFileOutputDTO dto = f.get();
	            if (!BookMakersCommonConst.NORMAL_CD.equals(dto.getResultCd())) {
	            	String msgCd = MessageCdConst.MCD00003E_EXECUTION_SKIP;
	                this.manageLoggerComponent.debugErrorLog(
	                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null,
	                    "ReadOriginS3 failed [" +
	                    dto.getThrowAble() + "]");
	                continue;
	            }
	            List<DataEntity> entity = dto.getDataList();
	            if (entity == null || entity.isEmpty()) {
	            	String msgCd = MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP;
	            	this.manageLoggerComponent.debugInfoLog(
		                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, "dataList is empty");
	            	continue;
	            }

	            // MapキーはS3キーでまとめるのが自然
	            String key = entity.get(0).getFile(); // getFile にS3 keyを入れる想定
	            resultMap.computeIfAbsent(key, k -> new ArrayList<>()).addAll(entity);
	        }

	    } catch (InterruptedException ie) {
	        Thread.currentThread().interrupt();
	        String msgCd = MessageCdConst.MCD00004E_THREAD_INTERRUPTION;
	        this.manageLoggerComponent.createBusinessException(
	            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null, ie);

	    } catch (Exception e) {
	    	String msgCd = MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN;
	        this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, e, "S3 Origin読み込みエラー");
	        this.manageLoggerComponent.createBusinessException(
	            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null, e);

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

	    return resultMap;
	}
}
