package dev.common.getinfo;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
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
import dev.common.util.CsvContentUtil;

/**
 * 未来情報取得管理クラス
 *
 * <p>2026-10 修正（ヘッダしかない CSV が S3 に残り続ける問題）</p>
 * <ul>
 *   <li>ヘッダのみ（データ行 0 件）のファイルは、読み込みクラスに渡す前に判定し（{@link CsvContentUtil#isHeaderOnly}）、
 *       key = S3 キー、value = 空のリスト で返す。B005（FutureStat）はこのファイルを登録せずに削除する。
 *       （以前は結果に入れていなかったため、全ファイルがヘッダのみだと B005 が「データなし」で終わり、削除されずに毎回読み続けていた）</li>
 *   <li>読み込みクラスが 0 件と判定した場合も同じく空のリストで返す。</li>
 *   <li>読み込みに失敗したファイル（タイムアウト・例外・異常コード・結果なし）は結果に入れない（削除せず次回もう一度読む）。
 *       以前は 1 ファイルの例外で全体が null（何も登録しない）になっていたが、そのファイルだけ飛ばして他は続ける。</li>
 * </ul>
 * <p>戻り値: key = ファイル（S3 キー）、value = その試合予定（ヘッダのみなら空のリスト）。読めた順（最終更新日時の昇順）。</p>
 * @author shiraishitoshio
 *
 */
@Component
public class GetFutureInfo {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = GetFutureInfo.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = GetFutureInfo.class.getName();

	/** LoggerFactory */
	private static final Logger log = LoggerFactory.getLogger(GetFutureInfo.class);

	/** 取得バケット正規表現：future_X.csv */
	private static final Pattern DATE_FUTURE_CSV_KEY =
			Pattern.compile("^.*future_[^/]+\\.csv$");

	/** ヘッダのみのファイルを表す読み込み結果 */
	private static final Object HEADER_ONLY = new Object();

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

	    // 読めた順（最終更新日時の昇順）を保つ
	    Map<String, List<FutureEntity>> resultMap = new LinkedHashMap<>();

	    if (fileStatList.isEmpty()) {
	    	String msgCd = MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP;
	        this.manageLoggerComponent.debugInfoLog(
	            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, "データなし(S3)");
	        return resultMap;
	    }

	    final int MAX_THREADS = 8;
	    int poolSize = Math.min(MAX_THREADS, fileStatList.size());
	    ExecutorService executor = Executors.newFixedThreadPool(poolSize);

	    int emptyFiles = 0;
	    int failedFiles = 0;
	    try {
	        List<Callable<Object>> tasks = new ArrayList<>(fileStatList.size());

	        for (String key : fileStatList) {
	            tasks.add(() -> {
	                byte[] bytes;
	                try (InputStream is = s3Operator.download(bucket, key)) {
	                    bytes = is.readAllBytes();
	                }
	                // ヘッダのみは読み込みクラスに渡さない
	                if (CsvContentUtil.isHeaderOnly(bytes)) {
	                    return HEADER_ONLY;
	                }
	                try (InputStream is = new ByteArrayInputStream(bytes)) {
	                    // ★ ReadFuture 側に「InputStreamから読む」メソッドを用意
	                    return readFuture.getFileBodyFromStream(is, key);
	                }
	            });
	        }

	        // invokeAll の結果は tasks と同じ順番（i 番目 = fileStatList の i 番目）
	        List<Future<Object>> futures = executor.invokeAll(tasks, 60, TimeUnit.SECONDS);

	        for (int i = 0; i < futures.size(); i++) {
	            String s3Key = fileStatList.get(i);
	            Future<Object> f = futures.get(i);

	            if (f.isCancelled()) {
	                failedFiles++;
	            	String msgCd = MessageCdConst.MCD00003E_EXECUTION_SKIP;
	                this.manageLoggerComponent.debugErrorLog(
	                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null,
	                    "ReadFutureS3 timeout/cancel key=" + s3Key + "（削除せず次回もう一度読む）");
	                continue;
	            }

	            Object res;
	            try {
	                res = f.get();
	            } catch (ExecutionException e) {
	                failedFiles++;
	            	String msgCd = MessageCdConst.MCD00003E_EXECUTION_SKIP;
	                this.manageLoggerComponent.debugErrorLog(
	                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, e,
	                    "ReadFutureS3 read failed key=" + s3Key + "（削除せず次回もう一度読む）");
	                continue;
	            }

	            if (res == HEADER_ONLY) {
	                // ヘッダのみ: 空のリストで返し、FutureStat で削除する
	                emptyFiles++;
	                resultMap.putIfAbsent(s3Key, new ArrayList<>());
	                this.manageLoggerComponent.debugInfoLog(
	                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
	                    "データ行なし（ヘッダのみ）のため登録せず削除対象にします: key=" + s3Key);
	                continue;
	            }

	            ReadFileOutputDTO dto = (ReadFileOutputDTO) res;
	            if (dto == null) {
	                failedFiles++;
	            	String msgCd = MessageCdConst.MCD00003E_EXECUTION_SKIP;
	                this.manageLoggerComponent.debugErrorLog(
	                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null,
	                    "ReadFutureS3 no result key=" + s3Key + "（削除せず次回もう一度読む）");
	                continue;
	            }
	            if (!BookMakersCommonConst.NORMAL_CD.equals(dto.getResultCd())) {
	                failedFiles++;
	            	String msgCd = MessageCdConst.MCD00003E_EXECUTION_SKIP;
	                this.manageLoggerComponent.debugErrorLog(
	                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null,
	                    "ReadFutureS3 failed key=" + s3Key + " [" + dto.getThrowAble() + "]（削除せず次回もう一度読む）");
	                continue;
	            }
	            List<FutureEntity> entity = dto.getFutureList();
	            if (entity == null || entity.isEmpty()) {
	                // 読み込みクラスが 0 件と判定した場合もヘッダのみ扱い
	                emptyFiles++;
	                resultMap.putIfAbsent(s3Key, new ArrayList<>());
	            	this.manageLoggerComponent.debugInfoLog(
		                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
		                    "dataList is empty（登録せず削除対象にします）: key=" + s3Key);
	            	continue;
	            }

	            // MapキーはS3キーでまとめるのが自然
	            String key = entity.get(0).getFile(); // getFile にS3 keyを入れる想定
	            if (key == null || key.isBlank()) {
	                key = s3Key;
	            }
	            resultMap.computeIfAbsent(key, k -> new ArrayList<>()).addAll(entity);
	        }

	    } catch (InterruptedException ie) {
	    	Thread.currentThread().interrupt();
	        String msgCd = MessageCdConst.MCD00004E_THREAD_INTERRUPTION;
	        this.manageLoggerComponent.createBusinessException(
	            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, ie.getCause(), ie);

	    } catch (Exception e) {
	    	String msgCd = MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN;
	        this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, e, "S3 Future読み込みエラー");
	        return null;
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

	    log.info("[B005] 読み込み結果: files={} (データあり={}, ヘッダのみ={}, 読み込み失敗={})",
	            fileStatList.size(), resultMap.size() - emptyFiles, emptyFiles, failedFiles);
	    return resultMap;
	}

}