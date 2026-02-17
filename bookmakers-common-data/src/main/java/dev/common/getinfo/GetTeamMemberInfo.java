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
import dev.common.constant.MessageCdConst;
import dev.common.entity.TeamMemberMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadTeamMember;
import dev.common.readfile.dto.ReadFileOutputDTO;
import dev.common.s3.S3Operator;

/**
 * 選手情報取得管理クラス
 * @author shiraishitoshio
 *
 */
@Component
public class GetTeamMemberInfo {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = GetTeamMemberInfo.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = GetTeamMemberInfo.class.getName();

	/** LoggerFactory */
	private static final Logger log = LoggerFactory.getLogger(GetTeamMemberInfo.class);

	/** 取得バケット正規表現：teamMemberData_X.csv */
	private static final Pattern TEAM_MEMBER_SEQ =
	        Pattern.compile("^teamMemberData_(\\d+)\\.csv$");

	/** S3オペレーター */
	@Autowired
	private S3Operator s3Operator;

	/** パス設定 */
	@Autowired
	private PathConfig config;

	/** ファイル読み込みクラス */
	@Autowired
	private ReadTeamMember readTeamMember;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 取得メソッド
	 */
	public Map<String, List<TeamMemberMasterEntity>> getData() {
        final String METHOD_NAME = "getData";
        String bucket = config.getS3BucketsTeamMemberData();
        // ✅ 連番昇順でキー取得（あなたのS3Operator実装でソート済み）
        List<String> keys = s3Operator.listSeqCsvKeysInRoot(bucket, TEAM_MEMBER_SEQ);

	    log.info("[B002] S3 bucket={} prefix={} keys.size={} keys(sample)={}",
	    		  bucket, TEAM_MEMBER_SEQ,
	    		  (keys==null ? -1 : keys.size()),
	    		  (keys==null ? null : keys.stream().limit(5).collect(Collectors.toList()))
	    		);

        Map<String, List<TeamMemberMasterEntity>> resultMap = new HashMap<>();

        if (keys == null || keys.isEmpty()) {
        	String msgCd = MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP;
	        this.manageLoggerComponent.debugInfoLog(
	            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, "データなし(S3)");
            return resultMap;
        }

        int poolSize = Math.min(8, keys.size());
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);

        try {
            List<Callable<ReadFileOutputDTO>> tasks = new ArrayList<>(keys.size());
            for (String key : keys) {
                tasks.add(() -> {
                    try (InputStream is = s3Operator.download(bucket, key)) {
                        return readTeamMember.getFileBodyFromStream(is, key);
                    }
                });
            }

            List<Future<ReadFileOutputDTO>> futures = executor.invokeAll(tasks, 60, TimeUnit.SECONDS);

            for (Future<ReadFileOutputDTO> f : futures) {
                if (f.isCancelled()) {
                    this.manageLoggerComponent.debugErrorLog(
                            PROJECT_NAME, CLASS_NAME, METHOD_NAME, "ReadTeamMemberS3 timeout/cancel", null);
                    continue;
                }

                ReadFileOutputDTO dto = f.get();
                if (dto == null) continue;

                // 異常系コードでも「読めた分は返したい」なら continue にする（今の方針に合わせて）
                if (dto.getMemberList() == null || dto.getMemberList().isEmpty()) continue;

                String key = dto.getMemberList().get(0).getFile();
                resultMap.computeIfAbsent(key, k -> new ArrayList<>()).addAll(dto.getMemberList());
            }

        } catch (Exception e) {
        	String msgCd = MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN;
	        this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, e, "S3 teamMemberData");
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
