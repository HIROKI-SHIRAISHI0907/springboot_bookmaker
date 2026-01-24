package dev.common.getinfo;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadStat;
import dev.common.readfile.dto.ReadFileOutputDTO;
import dev.common.s3.S3Operator;
import dev.common.util.ExecuteMainUtil;

/**
 * 統計情報取得管理クラス
 * @author shiraishitoshio
 *
 */
@Component
public class GetStatInfo {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = GetStatInfo.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = GetStatInfo.class.getSimpleName();

	/** 取得バケット正規表現：X.csv */
	private static final Pattern SEQ_CSV_KEY =
	        Pattern.compile("^\\d+\\.csv$"); // 例: 1.csv, 12.csv

	/** S3オペレーター */
	@Autowired
	private S3Operator s3Operator;

	/** パス設定 */
	@Autowired
	private PathConfig config;

	/** ファイル読み込みクラス */
	@Autowired
	private ReadStat readStat;

	/** CSV用のスレッドプール（CsvQueueConfigで定義） */
	@Autowired
	private ThreadPoolTaskExecutor csvTaskExecutor;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 取得メソッド
	 */
	public Map<String, Map<String, List<BookDataEntity>>> getData(String csvNumber, String csvBackNumber) {
	    final String METHOD_NAME = "getData";

	    String bucket = config.getS3BucketsStats();
	    List<String> fileStatList = s3Operator.listSeqCsvKeysInRoot(bucket, SEQ_CSV_KEY);

	    // csvNumber/csvBackNumber で連番範囲フィルタ
	    fileStatList = filterKeysBySeqRange(fileStatList, csvNumber, csvBackNumber);

	    Map<String, Map<String, List<BookDataEntity>>> resultMap = new HashMap<>();
	    if (fileStatList.isEmpty()) {
	        this.manageLoggerComponent.debugInfoLog(
	                PROJECT_NAME, CLASS_NAME, METHOD_NAME, "データなし(S3)", "GetStatInfo");
	        return resultMap;
	    }

	    final int concurrency = 8;
	    final Semaphore gate = new Semaphore(concurrency);

	    List<CompletableFuture<ReadFileOutputDTO>> futures = new ArrayList<>(fileStatList.size());
	    for (String key : fileStatList) {
	        try {
	            gate.acquire();
	        } catch (InterruptedException ie) {
	            Thread.currentThread().interrupt();
	            String msgCd = MessageCdConst.MCD00004E_THREAD_INTERRUPTION;
		        this.manageLoggerComponent.createBusinessException(
		            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null, ie);
	            break;
	        }

	        CompletableFuture<ReadFileOutputDTO> cf = CompletableFuture
	                .supplyAsync(() -> {
	                    try (InputStream is = s3Operator.download(bucket, key)) {
	                        return this.readStat.getFileBodyFromStream(is, key);
	                    } catch (Exception e) {
	                        ReadFileOutputDTO dto = new ReadFileOutputDTO();
	                        dto.setExceptionProject(PROJECT_NAME);
	                        dto.setExceptionClass(CLASS_NAME);
	                        dto.setExceptionMethod(METHOD_NAME);
	                        dto.setResultCd(BookMakersCommonConst.ERR_CD_ERR_FILE_READS);
	                        dto.setErrMessage("S3 download/read error key=" + key);
	                        dto.setThrowAble(e);
	                        return dto;
	                    }
	                }, csvTaskExecutor)
	                .whenComplete((r, t) -> gate.release());

	        futures.add(cf);
	    }

	    for (CompletableFuture<ReadFileOutputDTO> cf : futures) {
	        try {
	            ReadFileOutputDTO dto = cf.join();
	            if (dto == null) {
	            	String msgCd = MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP;
	            	this.manageLoggerComponent.debugInfoLog(
		                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, "dto null");
	            	 this.manageLoggerComponent.createBusinessException(
	         	            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null, null);
	                continue;
	            }
	            if (!BookMakersCommonConst.NORMAL_CD.equals(dto.getResultCd())) {
	            	String msgCd = MessageCdConst.MCD00003E_EXECUTION_SKIP;
	                this.manageLoggerComponent.debugErrorLog(
	                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null, dto.getErrMessage());
	                continue;
	            }

	            List<BookDataEntity> entity = dto.getReadHoldDataList();
	            if (entity == null || entity.isEmpty()) continue;

	            String[] data_key = ExecuteMainUtil.splitLeagueInfo(entity.get(0).getGameTeamCategory());
	            String country = data_key[0];
	            String league = data_key[1];
	            String home = entity.get(0).getHomeTeamName();
	            String away = entity.get(0).getAwayTeamName();

	            if (country == null || league == null || home == null || away == null) {
	            	String msgCd = MessageCdConst.MCD00003E_EXECUTION_SKIP;
	                this.manageLoggerComponent.debugErrorLog(
	                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null, "country/league/home/away: nullエラー, " + country + ", " + league + ", " + home + ", " + away);
	                this.manageLoggerComponent.createBusinessException(
	        	            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null, null);
	                continue;
	            }

	            String mapKey = entity.get(0).getGameTeamCategory();
	            String teamKey = home + "-" + away;

	            resultMap
	                    .computeIfAbsent(mapKey, k -> new HashMap<>())
	                    .computeIfAbsent(teamKey, s -> new ArrayList<>())
	                    .addAll(entity);

	        } catch (Exception e) {
	        	String msgCd = MessageCdConst.MCD00006E_ASYNCHRONOUS_ERROR;
                this.manageLoggerComponent.debugErrorLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null);
                this.manageLoggerComponent.createBusinessException(
        	            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, null, null);
	        }
	    }
	    return resultMap;
	}

	/**
     * フィルター付きソート
     * @param keys
     * @param csvNumber
     * @param csvBackNumber
     * @return
     */
    public static List<String> filterKeysBySeqRange(List<String> keys, String csvNumber, String csvBackNumber) {
        Integer from = null;
        Integer to = null;

        try {
            if (csvNumber != null && !csvNumber.isBlank()) from = Integer.parseInt(csvNumber.trim());
        } catch (NumberFormatException ignore) {}
        try {
            if (csvBackNumber != null && !csvBackNumber.isBlank()) to = Integer.parseInt(csvBackNumber.trim());
        } catch (NumberFormatException ignore) {}

        if (from == null && to == null) return keys;

        final Integer fFrom = from;
        final Integer fTo = to;

        return keys.stream()
                .filter(k -> {
                    Integer seq = extractSeqFromKey(k);
                    if (seq == null) return false;
                    if (fFrom != null && seq < fFrom) return false;
                    if (fTo != null && seq > fTo) return false;
                    return true;
                })
                .collect(Collectors.toList());
    }

    /**
     * ソート用整数変換
     * @param key
     * @return
     */
    private static Integer extractSeqFromKey(String key) {
        if (key == null) return null;
        var m = java.util.regex.Pattern.compile("^(\\d+)\\.csv$").matcher(key);
        if (!m.find()) return null;
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
