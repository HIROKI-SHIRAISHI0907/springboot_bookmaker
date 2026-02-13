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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.FutureEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadFuture;
import dev.common.readfile.dto.ReadFileOutputDTO;
import dev.common.s3.S3Operator;

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

	/** LoggerFactory */
	private static final Logger log = LoggerFactory.getLogger(GetFutureInfo.class);

	/** 取得バケット正規表現：future_X.csv */
	private static final Pattern DATE_FUTURE_CSV_KEY =
			Pattern.compile("^.*future_[^/]+\\.csv$");

	/** S3オペレーター */
	@Autowired
	private S3Operator s3Operator;

	/** パス設定 */
	@Autowired
	private PathConfig config;

	/** ファイル読み込みクラス */
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

	    String bucket = config.getS3BucketsFuture();

	    // 最終更新時間を昇順に取得
	    List<String> fileStatList = s3Operator
	            .listAllDateCsvObjectsSortedByLastModifiedAsc(bucket, DATE_FUTURE_CSV_KEY)
	            .stream()
	            .map(o -> o.key())
	            .collect(Collectors.toList());

	    log.info("[B005] S3 bucket={} prefix={} keys.size={} keys(sample)={}",
	    		  bucket, DATE_FUTURE_CSV_KEY,
	    		  (fileStatList==null ? -1 : fileStatList.size()),
	    		  (fileStatList==null ? null : fileStatList.stream().limit(5).collect(Collectors.toList()))
	    		);

	    Map<String, List<FutureEntity>> resultMap = new HashMap<>();

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
	                    return readFuture.getFileBodyFromStream(is, key);
	                }
	            });
	        }

	        List<Future<ReadFileOutputDTO>> futures = executor.invokeAll(tasks, 60, TimeUnit.SECONDS);

	        for (Future<ReadFileOutputDTO> f : futures) {
	            if (f.isCancelled()) {
	            	String msgCd = MessageCdConst.MCD00003E_EXECUTION_SKIP;
	                this.manageLoggerComponent.debugErrorLog(
	                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null, "ReadFutureS3 timeout/cancel");
	                continue;
	            }
	            ReadFileOutputDTO dto = f.get();
	            if (!BookMakersCommonConst.NORMAL_CD.equals(dto.getResultCd())) {
	            	String msgCd = MessageCdConst.MCD00003E_EXECUTION_SKIP;
	                this.manageLoggerComponent.debugErrorLog(
	                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null,
	                    "ReadFutureS3 failed [" +
	                    dto.getThrowAble() + "]");
	                continue;
	            }
	            List<FutureEntity> entity = dto.getFutureList();
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
	        this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, e, "S3 Future読み込みエラー");
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
