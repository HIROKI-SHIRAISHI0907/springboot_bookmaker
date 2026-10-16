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
import dev.common.entity.TeamMemberMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadTeamMember;
import dev.common.readfile.dto.ReadFileOutputDTO;
import dev.common.s3.S3Operator;
import dev.common.util.CsvContentUtil;
import dev.common.util.S3QuarantineUtil;

/**
 * 選手情報取得管理クラス
 *
 * <p>2026-10 修正（ヘッダしかない CSV が S3 に残り続ける問題）</p>
 * <ul>
 *   <li>以前はデータ行が 0 件のファイル（ヘッダのみ）を結果に入れていなかったため、全ファイルがヘッダのみだと
 *       結果が空になり、バッチが「データなし」で終わって S3 のファイルが削除されなかった（毎回同じファイルを読み続ける）。</li>
 *   <li>→ 読めたがデータ行が 0 件のファイルも、key = S3 キー、value = 空のリスト で結果に入れる。
 *       ヘッダのみかは読み込みクラスに渡す前に判定する（{@link CsvContentUtil#isHeaderOnly}。ReadTeamMember の返し方に左右されない）。
 *       呼び出し側（B002）はこのキーも削除対象にする。</li>
 *   <li>読み込みに失敗したファイル（タイムアウト・例外・結果なし）は結果に入れない（削除せず、次回もう一度読む）。
 *       以前は 1 ファイルの例外で全体が null（何も登録しない）になっていたが、そのファイルだけ飛ばして他は続ける。</li>
 * </ul>
 * <p>戻り値: key = ファイル（S3 キー）、value = その選手データ（ヘッダのみなら空のリスト）。S3 の一覧取得・スレッドの失敗時は null。</p>
 * @author shiraishitoshio
 *
 *
 * <p>2026-10 修正（読み込めない CSV が S3 に残り続ける問題）</p>
 * <ul>
 *   <li>読み込めないファイル（読み込みクラスがエラーを返した、またはデータ行はあるのに正しい行が 1 件も無い）は、
 *       {@link S3QuarantineUtil} で error/＜元のキー＞.error に退避する。残すと Lambda が「.csv あり」と判定して
 *       ECS を起動し続け、毎回同じファイルで失敗するため。中身は退避先に残るので後から調べられる。</li>
 *   <li>ダウンロード失敗・タイムアウトは一時的なエラーとみなし、退避せず次回もう一度読む。</li>
 *   <li>ReadTeamMember が「データ異常」（列数不足の行・日付の変換エラー）を返した場合は、今までどおり読めた行を登録する
 *       （異常な行はエラーログを出して読み飛ばし／日付を空にして登録）。ファイルの読み込み自体が失敗した場合だけ退避する。
 *       以前は読み込み失敗でも結果が空のリストになり、ヘッダのみと同じく削除されていた。</li>
 * </ul>
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

        // 連番順を保つ
        Map<String, List<TeamMemberMasterEntity>> resultMap = new LinkedHashMap<>();

        if (keys == null || keys.isEmpty()) {
        	String msgCd = MessageCdConst.MCD00002I_BATCH_EXECUTION_SKIP;
	        this.manageLoggerComponent.debugInfoLog(
	            PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, "データなし(S3)");
            return resultMap;
        }

        int poolSize = Math.min(8, keys.size());
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);

        int emptyFiles = 0;
        int failedFiles = 0;
        // 中身が原因で読めないファイル（退避する）
        List<String> brokenKeys = new ArrayList<>();
        try {
            List<Callable<Object>> tasks = new ArrayList<>(keys.size());
            for (String key : keys) {
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
                        return readTeamMember.getFileBodyFromStream(is, key);
                    }
                });
            }

            // invokeAll の結果は tasks と同じ順番（i 番目 = keys の i 番目）
            List<Future<Object>> futures = executor.invokeAll(tasks, 60, TimeUnit.SECONDS);

            for (int i = 0; i < futures.size(); i++) {
                String s3Key = keys.get(i);
                Future<Object> f = futures.get(i);

                if (f.isCancelled()) {
                    failedFiles++;
                    this.manageLoggerComponent.debugErrorLog(
                            PROJECT_NAME, CLASS_NAME, METHOD_NAME, "ReadTeamMemberS3 timeout/cancel", null,
                            "key=" + s3Key + "（削除せず次回もう一度読む）");
                    continue;
                }

                Object res;
                try {
                    res = f.get();
                } catch (ExecutionException e) {
                    // このファイルだけ飛ばす（削除せず次回もう一度読む）
                    failedFiles++;
                    this.manageLoggerComponent.debugErrorLog(
                            PROJECT_NAME, CLASS_NAME, METHOD_NAME, "ReadTeamMemberS3 read failed", e,
                            "key=" + s3Key + "（削除せず次回もう一度読む）");
                    continue;
                }

                if (res == HEADER_ONLY) {
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
                    brokenKeys.add(s3Key);
                    this.manageLoggerComponent.debugErrorLog(
                            PROJECT_NAME, CLASS_NAME, METHOD_NAME, "ReadTeamMemberS3 no result", null,
                            "key=" + s3Key + "（error/ に退避します）");
                    continue;
                }

                // ファイルの読み込み自体が失敗した: 退避する
                if (BookMakersCommonConst.ERR_CD_ERR_FILE_READS.equals(dto.getResultCd())) {
                    failedFiles++;
                    brokenKeys.add(s3Key);
                    this.manageLoggerComponent.debugErrorLog(
                            PROJECT_NAME, CLASS_NAME, METHOD_NAME, "ReadTeamMemberS3 read failed", null,
                            "key=" + s3Key + "（error/ に退避します）" + dto.getThrowAble());
                    continue;
                }

                // データ異常（一部の行の列数不足・日付の変換エラー）: 読めた行は登録する（今までどおり）
                if (!BookMakersCommonConst.NORMAL_CD.equals(dto.getResultCd())) {
                    log.warn("[B002] 一部の行にデータ異常あり（読めた行は登録します） key={} resultCd={} detail={}",
                            s3Key, dto.getResultCd(), dto.getErrMessage());
                }

                List<TeamMemberMasterEntity> members = dto.getMemberList();
                if (members == null || members.isEmpty()) {
                    // データ行はあるのに正しい行が 1 件も無い（ヘッダのみは上で判定済み）: 壊れたファイルとして退避する
                    failedFiles++;
                    brokenKeys.add(s3Key);
                    this.manageLoggerComponent.debugErrorLog(
                            PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00003E_EXECUTION_SKIP, null,
                            "正しいデータ行が0件（全行が列数不足など）: key=" + s3Key + "（error/ に退避します）");
                    continue;
                }

                String fileKey = members.get(0).getFile();
                if (fileKey == null || fileKey.isBlank()) {
                    fileKey = s3Key;
                }
                resultMap.computeIfAbsent(fileKey, k -> new ArrayList<>()).addAll(members);
            }

        } catch (Exception e) {
        	String msgCd = MessageCdConst.MCD00005E_OTHER_EXECUTION_GREEN_FIN;
	        this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, msgCd, e, "S3 teamMemberData");
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

        // 中身が原因で読めないファイルを退避する（残すと Lambda が ECS を起動し続けるため）
        int quarantined = 0;
        for (String brokenKey : brokenKeys) {
            if (S3QuarantineUtil.quarantine(bucket, brokenKey, "読み込みエラー（ReadTeamMember）",
                    s3Operator, manageLoggerComponent, PROJECT_NAME, CLASS_NAME, METHOD_NAME)) {
                quarantined++;
            }
        }

        log.info("[B002] 読み込み結果: files={} (データあり={}, ヘッダのみ={}, 読み込み失敗={}, うち退避={})",
                keys.size(), resultMap.size() - emptyFiles, emptyFiles, failedFiles, quarantined);
        return resultMap;
    }
}
