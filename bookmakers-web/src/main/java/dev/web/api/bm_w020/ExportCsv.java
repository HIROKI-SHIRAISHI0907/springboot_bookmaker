package dev.web.api.bm_w020;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.DataEntity;
import dev.common.filemng.FileMngWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.web.repository.bm.BookCsvDataRepository;
import dev.web.util.CsvArtifactHelper;

/**
 * StatデータCSV出力ロジック（S3 tmpステージング版）
 *
 * 流れ：
 * 1) S3（本番prefix）から seqList.txt / data_team_list.txt をローカルへDL
 * 2) DBからグルーピング作成 → 差分計画（recreate/new）作成
 * 3) 生成対象CSVのみローカルに作成
 * 4) 生成物を S3 tmpPrefix へ一旦 put（CSV + seqList + teamList）
 * 5) 全部成功したら tmp → 本番へ copy → tmp delete（コミット）
 */
@Component
public class ExportCsv {

    private static final String PROJECT_NAME = ExportCsv.class.getProtectionDomain()
            .getCodeSource().getLocation().getPath();

    // ロガー名はFQCN推奨（パッケージ単位のログ制御に強い）
    private static final String CLASS_NAME = ExportCsv.class.getName();

    private static final String CSV_NEW_PREFIX = "mk";

    // 末尾が "<数字>.csv" のキーをCSVとみなす
    private static final Pattern CSV_KEY_PATTERN =
            Pattern.compile("^.*?(\\d+)\\.csv$", Pattern.CASE_INSENSITIVE);

    @Autowired
    private S3Operator s3Operator;

    @Autowired
    private PathConfig config;

    @Autowired
    private ReaderCurrentCsvInfoBean bean;

    @Autowired
    private CsvArtifactHelper helper;

    @Autowired
    private BookCsvDataRepository bookCsvDataRepository;

    @Autowired
    private ManageLoggerComponent manageLoggerComponent;

    public void execute() throws IOException {
        final String METHOD_NAME = "execute";
        this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, "start");

        // ====== 設定 ======
        final String statsBucket = config.getS3BucketsStats();

        // 本番prefix（直下なら ""、stats/ 配下なら "stats" 等）
        final String finalPrefix = ""; // 必要なら config から取得に変更

        // S3管理ファイルキー（本番側）
        final String seqKeyFinal  = s3Operator.buildKey(finalPrefix, "seqList.txt");
        final String teamKeyFinal = s3Operator.buildKey(finalPrefix, "data_team_list.txt");

        // ローカル作業場所（ECSコンテナのローカル）。S3 tmpとは別物。
        final Path LOCAL_DIR = Paths.get(config.getCsvFolder());
        ensureDir(LOCAL_DIR);

        final Path localSeqPath  = LOCAL_DIR.resolve("seqList.txt");
        final Path localTeamPath = LOCAL_DIR.resolve("data_team_list.txt");

        // S3 tmp prefix（実行単位でユニークにする）
        // 例: tmp/root/1700000000000/  または tmp/stats/170.../
        final String runId = String.valueOf(System.currentTimeMillis());
        final String prefixLabel = (finalPrefix == null || finalPrefix.isBlank())
                ? "root"
                : finalPrefix.replaceAll("/+$", "");
        final String tmpPrefix = "tmp/" + prefixLabel + "/" + runId;

        this.manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
                "bucket=" + statsBucket + ", finalPrefix=" + (finalPrefix == null ? "" : finalPrefix)
                        + ", tmpPrefix=" + tmpPrefix + ", localDir=" + LOCAL_DIR);

        // ====== 1) 本番S3→ローカルへ管理ファイルDL（無ければ初回扱い） ======
        boolean seqExists = downloadIfExists(statsBucket, seqKeyFinal, localSeqPath,
                "seqList.txt download");
        downloadIfExists(statsBucket, teamKeyFinal, localTeamPath,
                "data_team_list.txt download");

        // ====== 2) 現在作成済みCSV読み込み（既存ロジック） ======
        // ※beanがS3を見る/ローカルを見るかは不明だが、既存のまま利用
        bean.init();

        // ====== 3) DBから現在のグルーピングを作る ======
        List<List<Integer>> currentGroups = sortSeqs();

        // ====== 4) 既存 seqList 読み込み or 初回作成 ======
        FileMngWrapper fileIO = new FileMngWrapper();
        final String SEQ_LIST = localSeqPath.toString();
        final String DATA_TEAM_LIST_TXT = localTeamPath.toString();

        boolean firstRun = !seqExists || !Files.exists(localSeqPath);
        List<List<Integer>> textGroups;

        if (firstRun) {
            // 初回：定義を書き出しておく
            fileIO.write(SEQ_LIST, currentGroups.toString());
            textGroups = Collections.emptyList();
        } else {
            textGroups = fileIO.readSeqBuckets(SEQ_LIST);
        }

        // ====== 5) 既存CSV情報 ======
        Map<String, List<Integer>> csvInfoRow = (bean != null ? bean.getCsvInfo() : null);
        csvInfoRow = (csvInfoRow != null) ? csvInfoRow : Collections.emptyMap();

        // ====== 6) 照合して plan 作成 ======
        CsvBuildPlan plan;
        if (firstRun) {
            CsvBuildPlan plans = new CsvBuildPlan();
            for (List<Integer> curr : currentGroups) {
                plans.onlyNew(CSV_NEW_PREFIX, curr);
            }
            plan = plans;
        } else {
            plan = matchSeqCombPlan(textGroups, currentGroups, csvInfoRow);
        }

        // ====== 7) 条件マスタ取得 ======
        CsvArtifactResource csvArtifactResource;
        try {
            csvArtifactResource = this.helper.getData();
        } catch (Exception e) {
            this.manageLoggerComponent.debugErrorLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e);
            throw e;
        }

        // ====== 8) 生成キュー作成（再作成→新規） ======
        List<SimpleEntry<String, List<DataEntity>>> ordered = new ArrayList<>();

        if (plan != null) {
            // 8-1) 再作成
            for (Map.Entry<Integer, List<Integer>> rt : plan.recreateByCsvNo.entrySet()) {
                String path = LOCAL_DIR.resolve(rt.getKey() + BookMakersCommonConst.CSV).toString();
                List<Integer> ids = normalizeSeqList(rt.getValue());

                List<DataEntity> result = fetchAndFilter(ids, csvArtifactResource, METHOD_NAME,
                        "recreate findByData");
                if (result == null) continue;

                ordered.add(new SimpleEntry<>(path, result));
            }

            // 8-2) 新規（最大CSV番号はS3本番prefixから取得）
            int maxOnS3 = getMaxCsvNoFromS3(statsBucket, finalPrefix);
            int nextNo = maxOnS3 + 1;

            int diff = 0;
            for (Map.Entry<String, List<Integer>> entry : plan.newTargets.entrySet()) {
                List<Integer> ids = normalizeSeqList(entry.getValue());
                if (ids.isEmpty()) continue;

                int csvNo = nextNo + diff;
                String path = LOCAL_DIR.resolve(csvNo + BookMakersCommonConst.CSV).toString();

                List<DataEntity> result = fetchAndFilter(ids, csvArtifactResource, METHOD_NAME,
                        "new findByData");
                if (result == null) continue;

                ordered.add(new SimpleEntry<>(path, result));
                diff++;
            }
        }

        // ====== 9) 既存CSVと一致するものは除外（※以前のバグ修正：除外結果を本当に使う） ======
        List<SimpleEntry<String, List<DataEntity>>> toCreate = new ArrayList<>();
        for (SimpleEntry<String, List<DataEntity>> ord : ordered) {
            boolean match = matchCsvInfo(csvInfoRow, ord.getValue());
            if (!match) toCreate.add(ord);
        }

        if (toCreate.isEmpty()) {
            String messageCd = MessageCdConst.MCD00014I_NO_MAP_DATA;
            String fillChar = "追加レコードがないため処理終了 (既存CSV数: " + csvInfoRow.size() + "件)";
            endLog(METHOD_NAME, messageCd, fillChar);
            return;
        }

        // ====== 10) 並列生成→ローカル書込→S3 tmpへPUT ======
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        List<CompletableFuture<CsvArtifact>> futures = new ArrayList<>(toCreate.size());
        for (SimpleEntry<String, List<DataEntity>> e : toCreate) {
            final String path = e.getKey();
            final List<DataEntity> group = e.getValue();
            futures.add(CompletableFuture.supplyAsync(() -> buildCsvArtifact(path, group, csvArtifactResource), pool));
        }

        int success = 0, failed = 0;
        List<SimpleEntry<String, List<DataEntity>>> succeeded = new ArrayList<>();
        List<SimpleEntry<String, List<DataEntity>>> failedEntries = new ArrayList<>();

        // tmpへPUTするキーを収集（commit対象）
        List<String> tmpPutKeys = new ArrayList<>();

        for (int i = 0; i < futures.size(); i++) {
            try {
                CsvArtifact art = futures.get(i).join();
                if (art == null || art.getContent().isEmpty()) {
                    // 条件で空になった等はスキップ（失敗扱いにするかは運用次第）
                    continue;
                }

                // 1) ローカルへ書く
                writeLocalCsv(art);

                // 2) S3 tmpへPUT（ステージング）
                String tmpKey = putLocalFileToTmp(statsBucket, tmpPrefix, Paths.get(art.getFilePath()));
                tmpPutKeys.add(tmpKey);

                success++;
                succeeded.add(toCreate.get(i));
            } catch (Exception ex) {
                failed++;
                failedEntries.add(toCreate.get(i));
                this.manageLoggerComponent.debugErrorLog(
                        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                        MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, ex,
                        "CSV作成/PUT(tmp) 失敗");
            }
        }

        pool.shutdown();
        try {
            pool.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
        }

        this.manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
                "CSVステージング結果(tmp put) (成功: " + success + "件, 失敗: " + failed + "件)");

        // ====== 11) data_team_list.txt 更新（ローカル）→ S3 tmpへPUT ======
        try {
            upsertDataTeamList(Paths.get(DATA_TEAM_LIST_TXT), this.config.getCsvFolder(), succeeded, failedEntries);
            String tmpKeyTeam = putLocalFileToTmp(statsBucket, tmpPrefix, localTeamPath);
            tmpPutKeys.add(tmpKeyTeam);
        } catch (Exception e) {
            // ここが失敗したら commit 不可
            this.manageLoggerComponent.debugErrorLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
                    "data_team_list.txt 更新/PUT(tmp) 失敗");
            cleanupTmp(statsBucket, tmpPrefix); // 任意：掃除
            throw (e instanceof IOException) ? (IOException) e : new IOException(e);
        }

        // ====== 12) seqList.txt 更新（ローカル）→ S3 tmpへPUT ======
        try {
            fileIO.overwrite(SEQ_LIST, currentGroups.toString());
            String tmpKeySeq = putLocalFileToTmp(statsBucket, tmpPrefix, localSeqPath);
            tmpPutKeys.add(tmpKeySeq);
        } catch (Exception e) {
            this.manageLoggerComponent.debugErrorLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
                    "seqList.txt 更新/PUT(tmp) 失敗");
            cleanupTmp(statsBucket, tmpPrefix);
            throw (e instanceof IOException) ? (IOException) e : new IOException(e);
        }

        // ====== 13) commit: tmp → 本番へ copy & tmp delete ======
        // 失敗があるなら commit しない（中途半端反映防止）
        if (failed > 0) {
            this.manageLoggerComponent.debugWarnLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    MessageCdConst.MCD00099I_LOG,
                    "失敗があるためcommitしません。tmpPrefixに残します: " + tmpPrefix);
            // cleanupしたいなら cleanupTmp(bucket, tmpPrefix) に変更
            endLog(METHOD_NAME, null, null);
            return;
        }

        try {
            commitTmpToFinal(statsBucket, tmpPrefix, finalPrefix);
        } catch (Exception e) {
            this.manageLoggerComponent.debugErrorLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
                    "commit(tmp->final) 失敗 tmpPrefix=" + tmpPrefix);
            // commit失敗時は tmp残す（復旧/再実行のため）
            throw (e instanceof IOException) ? (IOException) e : new IOException(e);
        }

        endLog(METHOD_NAME, null, null);
    }

    // =========================================================
    // S3 tmp ステージング関連
    // =========================================================

    private String putLocalFileToTmp(String bucket, String tmpPrefix, Path localFile) {
        final String METHOD_NAME = "putLocalFileToTmp";
        String fileName = localFile.getFileName().toString();
        String tmpKey = s3Operator.buildKey(tmpPrefix, fileName);

        try {
            s3Operator.uploadFile(bucket, tmpKey, localFile);
            return tmpKey;
        } catch (Exception e) {
            this.manageLoggerComponent.debugErrorLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    MessageCdConst.MCD00023E_S3_UPLOAD_FAILED, e,
                    bucket, tmpKey);
            throw e;
        }
    }

    private void commitTmpToFinal(String bucket, String tmpPrefix, String finalPrefix) {
        final String METHOD_NAME = "commitTmpToFinal";

        List<String> tmpKeys = s3Operator.listKeys(bucket, tmpPrefix);
        for (String tmpKey : tmpKeys) {
            if (tmpKey == null) continue;

            String fileName = Paths.get(tmpKey).getFileName().toString();
            String finalKey = s3Operator.buildKey(finalPrefix, fileName);

            // tmp → final copy → tmp delete
            s3Operator.copy(bucket, tmpKey, bucket, finalKey);
            s3Operator.delete(bucket, tmpKey);
        }

        this.manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                MessageCdConst.MCD00099I_LOG,
                "commit完了 tmpPrefix=" + tmpPrefix + " -> finalPrefix=" + (finalPrefix == null ? "" : finalPrefix));
    }

    private void cleanupTmp(String bucket, String tmpPrefix) {
        try {
            List<String> tmpKeys = s3Operator.listKeys(bucket, tmpPrefix);
            for (String k : tmpKeys) {
                try { s3Operator.delete(bucket, k); } catch (Exception ignore) {}
            }
        } catch (Exception ignore) {}
    }

    /**
     * S3（本番prefix）にある CSV の最大番号を取得する
     */
    private int getMaxCsvNoFromS3(String bucket, String finalPrefix) {
        final String METHOD_NAME = "getMaxCsvNoFromS3";
        String prefix = (finalPrefix == null) ? "" : finalPrefix;

        List<String> keys = s3Operator.listKeys(bucket, prefix);
        int max = 0;

        for (String key : keys) {
            if (key == null) continue;
            if (!CSV_KEY_PATTERN.matcher(key).matches()) continue;

            String name = Paths.get(key).getFileName().toString(); // 4710.csv
            int dot = name.indexOf('.');
            if (dot <= 0) continue;

            try {
                int n = Integer.parseInt(name.substring(0, dot));
                if (n > max) max = n;
            } catch (NumberFormatException ignore) {}
        }

        this.manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
                "S3上の最大CSV番号=" + max + " (bucket=" + bucket + ", prefix=" + prefix + ")");
        return max;
    }

    private boolean downloadIfExists(String bucket, String key, Path out, String label) {
        final String METHOD_NAME = "downloadIfExists";
        try {
            s3Operator.downloadToFile(bucket, key, out);
            return true;
        } catch (Exception e) {
            this.manageLoggerComponent.debugWarnLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    MessageCdConst.MCD00099I_LOG,
                    label + " failed or not found. bucket=" + bucket + ", key=" + key);
            return false;
        }
    }

    // =========================================================
    // 既存ロジック（必要な部分を安全化）
    // =========================================================

    private List<List<Integer>> sortSeqs() {
        List<SeqWithKey> rows = this.bookCsvDataRepository.findAllSeqsWithKey();
        List<List<Integer>> result = new ArrayList<>();
        List<Integer> bucket = new ArrayList<>();
        String prevCat = null, prevHome = null, prevAway = null;

        for (SeqWithKey r : rows) {
            boolean newGroup = prevCat == null
                    || !Objects.equals(prevCat, r.getDataCategory())
                    || !Objects.equals(prevHome, r.getHomeTeamName())
                    || !Objects.equals(prevAway, r.getAwayTeamName());

            if (newGroup) {
                if (!bucket.isEmpty()) {
                    bucket.sort(Comparator.naturalOrder());
                    result.add(bucket);
                }
                bucket = new ArrayList<>();
                prevCat = r.getDataCategory();
                prevHome = r.getHomeTeamName();
                prevAway = r.getAwayTeamName();
            }
            if (r.getSeq() != null) {
                bucket.add(r.getSeq());
            }
        }
        if (!bucket.isEmpty()) {
            bucket.sort(Comparator.naturalOrder());
            result.add(bucket);
        }
        return result;
    }

    private List<Integer> normalizeSeqList(List<Integer> src) {
        if (src == null || src.isEmpty()) return Collections.emptyList();
        List<Integer> ids = new ArrayList<>(src.size());
        for (Integer n : new TreeSet<>(src)) {
            if (n != null) ids.add(n);
        }
        return ids;
    }

    private List<DataEntity> fetchAndFilter(
            List<Integer> ids,
            CsvArtifactResource csvArtifactResource,
            String parentMethod,
            String label) {

        if (ids == null || ids.isEmpty()) return null;

        List<DataEntity> result;
        try {
            result = this.bookCsvDataRepository.findByData(ids);
        } catch (Exception e) {
            this.manageLoggerComponent.debugErrorLog(
                    PROJECT_NAME, CLASS_NAME, parentMethod,
                    MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
                    label);
            throw e;
        }

        if (!this.helper.csvCondition(result, csvArtifactResource)) return null;

        result = this.helper.abnormalChk(result);
        if (result == null || result.isEmpty()) return null;

        backfillScores(result);
        return result;
    }

    private CsvArtifact buildCsvArtifact(String path, List<DataEntity> result, CsvArtifactResource resource) {
        if (result == null || result.isEmpty()) return null;
        return new CsvArtifact(path, result);
    }

    private void writeLocalCsv(CsvArtifact art) {
        // ここは “ローカルファイル作成” だけにする（S3 put は別）
        FileMngWrapper fw = new FileMngWrapper();
        fw.csvWrite(art.getFilePath(), art.getContent());
    }

    private static void ensureDir(Path dir) throws IOException {
        Files.createDirectories(dir);
    }

    private boolean matchCsvInfo(Map<String, List<Integer>> csvInfoRow, List<DataEntity> resource) {
        if (csvInfoRow == null || csvInfoRow.isEmpty() || resource == null || resource.isEmpty()) {
            return false;
        }
        StringBuilder resourceBuilder = new StringBuilder();
        for (DataEntity d : resource) {
            if (resourceBuilder.length() > 0) resourceBuilder.append("-");
            resourceBuilder.append(d.getSeq());
        }
        for (Map.Entry<String, List<Integer>> list : csvInfoRow.entrySet()) {
            List<Integer> vals = list.getValue();
            if (vals == null || vals.isEmpty()) continue;

            StringBuilder sBuilder = new StringBuilder();
            for (Integer d : vals) {
                if (sBuilder.length() > 0) sBuilder.append("-");
                sBuilder.append(d);
            }
            if (resourceBuilder.toString().equals(sBuilder.toString())) {
                return true;
            }
        }
        return false;
    }

    /** 既存の matchSeqCombPlan / chkComb / toKeySet は、あなたの現行版をそのまま持ってきてOK */
    private CsvBuildPlan matchSeqCombPlan(List<List<Integer>> textSeqs, List<List<Integer>> dbSeqs,
                                         Map<String, List<Integer>> csvInfoRow) {
        // ここは “あなたの現行実装” をそのまま貼り付けてください（省略しない方が良いなら続けて展開します）
        // ※この回答では「全体の処理フロー」を完成させるため、既存を利用する想定にしています。
        return null;
    }

    private void upsertDataTeamList(
            Path out, String baseFolder,
            List<SimpleEntry<String, List<DataEntity>>> succeeded,
            List<SimpleEntry<String, List<DataEntity>>> failed) throws IOException {
        // ここもあなたの現行実装をそのまま
    }

    private static void backfillScores(List<DataEntity> list) {
        if (list == null || list.isEmpty()) return;

        list.sort(Comparator.comparingInt(d -> {
            try {
                return Integer.parseInt(Objects.toString(d.getSeq(), "0"));
            } catch (NumberFormatException e) {
                return Integer.MAX_VALUE;
            }
        }));

        String lastHome = null;
        String lastAway = null;
        for (DataEntity d : list) {
            if (isBlank(d.getHomeScore()) && lastHome != null) d.setHomeScore(lastHome);
            if (isBlank(d.getAwayScore()) && lastAway != null) d.setAwayScore(lastAway);

            if (!isBlank(d.getHomeScore())) lastHome = d.getHomeScore();
            if (!isBlank(d.getAwayScore())) lastAway = d.getAwayScore();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private void endLog(String method, String messageCd, String fillChar) {
        if (messageCd != null && fillChar != null) {
            this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, method, messageCd, fillChar);
        }
        this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, method, "end");
    }
}
