package dev.batch.bm_b011;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.batch.repository.bm.BookCsvDataRepository;
import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.constant.MessageCdConst;
import dev.common.entity.DataEntity;
import dev.common.filemng.FileMngWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;

/**
 * StatデータCSV出力ロジック（S3 直下アップロード版）
 *
 * 流れ（S3モード）：
 * 1) S3（本番prefix）から seqList.txt / data_team_list.txt をローカルへDL
 * 2) DBからグルーピング作成 → 差分計画（recreate/new）作成
 * 3) 対象データをDBから取得し、フィルタ、補完
 * 4) CSV生成（並列）→ ローカル書き出し → S3へ直PUT
 * 5) data_team_list.txt 更新 → S3へPUT
 * 6) seqList.txt 更新 → S3へPUT
 *
 * localOnly=true のとき：
 * - S3操作を一切行わず、ローカルのみで完結
 */
@Component
public class ExportCsvService {

    private static final String PROJECT_NAME = ExportCsvService.class.getProtectionDomain()
            .getCodeSource().getLocation().getPath();

    private static final String CLASS_NAME = ExportCsvService.class.getName();

    private static final String CSV_NEW_PREFIX = "mk";

    private static final com.fasterxml.jackson.databind.ObjectMapper SEQ_JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private static final Pattern ROUND_TOKEN = Pattern.compile("ラウンド\\s*[0-9０-９]+");

    // "6089.csv" だけでなく "prefix/6089.csv" も解析できるようにしておく
    private static final Pattern CSV_NO_PATTERN = Pattern.compile("(^|.*/)(\\d+)\\.csv$", Pattern.CASE_INSENSITIVE);

    @Value("${exportcsv.local-only:false}")
    private boolean localOnly;

    /**
     * S3の本番配置プレフィックス（空文字ならバケット直下）
     * 例: "" / "stats" / "it/20260309-001"
     */
    @Value("${exportcsv.final-prefix:}")
    private String finalPrefix;

    /**
     * 「追加が無い回でも管理ファイルをPUTする」(true推奨)
     * 2回目以降に「上がってない」誤認が起きやすいのでデフォルトtrue
     */
    @Value("${exportcsv.always-put-manage-files:true}")
    private boolean alwaysPutManageFiles;

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

        // 出力先（localOnly時はここにすべて作る）
        Path outDir = Paths.get(config.getCsvFolder()).toAbsolutePath().normalize();
        Files.createDirectories(outDir);

        if (localOnly) {
            executeLocalOnly(outDir);
            return;
        }

        // ====== 設定 ======
        final String statsBucket = config.getS3BucketsStats();
        final String prefix = normalizePrefix(finalPrefix);

        // 管理ファイル名
        final String seqFileName = "seqList.txt";
        final String teamFileName = "data_team_list.txt";

        // 管理ファイルキー（prefix付き）
        final String seqKeyFinal = normalizeS3Key(joinS3Key(prefix, seqFileName));
        final String teamKeyFinal = normalizeS3Key(joinS3Key(prefix, teamFileName));

        // ローカル作業場所
        final Path LOCAL_DIR = outDir;
        ensureDir(LOCAL_DIR);

        final Path localSeqPath = LOCAL_DIR.resolve(seqFileName);
        final Path localTeamPath = LOCAL_DIR.resolve(teamFileName);

        this.manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
                "bucket=" + statsBucket
                        + ", finalPrefix=" + prefix
                        + ", localDir=" + LOCAL_DIR
                        + ", seqKeyFinal=" + seqKeyFinal
                        + ", teamKeyFinal=" + teamKeyFinal);

        // ====== 1) 本番S3→ローカルへ管理ファイルDL（無ければ初回扱い） ======
        boolean seqExists = downloadIfExists(statsBucket, seqKeyFinal, localSeqPath, "seqList.txt download");
        downloadIfExists(statsBucket, teamKeyFinal, localTeamPath, "data_team_list.txt download");

        // ====== 2) 現在作成済みCSV読み込み（既存ロジック） ======
        // ここで bean が既存CSV情報を構築する想定（実装に依存）
        bean.init();

        // ====== 3) DBから現在のグルーピングを作る ======
        List<List<Integer>> currentGroups = sortSeqs();
        currentGroups = normalizeGroups(currentGroups);

        // ====== 4) 既存 seqList 読み込み or 初回作成 ======
        boolean firstRun = !seqExists || !Files.exists(localSeqPath);
        List<List<Integer>> textGroups;

        if (firstRun) {
            writeSeqListJson(localSeqPath, currentGroups);
            textGroups = Collections.emptyList();
        } else {
            textGroups = readSeqListJson(localSeqPath);
            textGroups = normalizeGroups(textGroups);
        }

        // ====== 5) 既存CSV情報 ======
        Map<String, List<Integer>> csvInfoRow = (bean != null ? bean.getCsvInfo() : null);
        csvInfoRow = (csvInfoRow != null) ? csvInfoRow : Collections.emptyMap();

        // ====== 6) 照合して plan 作成 ======
        CsvBuildPlan plan;
        if (firstRun) {
            CsvBuildPlan plans = new CsvBuildPlan();
            int i = 0;
            for (List<Integer> curr : currentGroups) {
                plans.newTargets.put(CSV_NEW_PREFIX + "-" + (i++), curr);
            }
            plan = plans;
        } else {
            plan = matchSeqCombPlan(textGroups, currentGroups, csvInfoRow);
        }

        // plan が null/空なら終了（ただし管理ファイルPUTは要件次第で行う）
        if (plan == null || (plan.recreateByCsvNo.isEmpty() && plan.newTargets.isEmpty())) {

            if (alwaysPutManageFiles) {
                putManageFilesEvenIfNoCsv(statsBucket, prefix, localSeqPath, localTeamPath, currentGroups);
            }

            String messageCd = MessageCdConst.MCD00014I_NO_MAP_DATA;
            String fillChar = "追加レコードがないため処理終了 (既存CSV数: " + csvInfoRow.size() + "件)";
            endLog(METHOD_NAME, messageCd, fillChar);
            return;
        }

        this.manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
                "csvInfoRow.size=" + csvInfoRow.size()
                        + ", plan.recreate=" + plan.recreateByCsvNo.size()
                        + ", plan.newTargets=" + plan.newTargets.size()
                        + ", firstRun=" + firstRun);

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

        // ====== 8) 生成候補を集める（recreate / new） ======
        List<SimpleEntry<String, List<DataEntity>>> recreateCandidates = new ArrayList<>();
        List<List<DataEntity>> newCandidates = new ArrayList<>();

        // 8-1) 再作成
        for (Map.Entry<Integer, List<Integer>> rt : plan.recreateByCsvNo.entrySet()) {
            Integer csvNo = rt.getKey();
            if (csvNo == null) continue;

            String fileName = csvNo + BookMakersCommonConst.CSV;
            String path = LOCAL_DIR.resolve(fileName).toString();
            List<Integer> ids = normalizeSeqList(rt.getValue());

            List<DataEntity> result = fetchAndFilter(ids, csvArtifactResource, METHOD_NAME, "recreate findByData");
            if (result == null) continue;

            // 既存CSVと一致なら recreate を飛ばす（必要ならこの if を外して常に上書きでもOK）
            if (matchCsvInfo(csvInfoRow, result)) {
                continue;
            }
            recreateCandidates.add(new SimpleEntry<>(path, result));
        }

        // 8-2) 新規（ここでは採番しない）
        for (Map.Entry<String, List<Integer>> entry : plan.newTargets.entrySet()) {
            List<Integer> ids = normalizeSeqList(entry.getValue());
            if (ids.isEmpty()) continue;

            List<DataEntity> result = fetchAndFilter(ids, csvArtifactResource, METHOD_NAME, "new findByData");
            if (result == null) continue;

            // 既存CSVと一致なら new を飛ばす（欠番の原因になるので“採番前”に除外）
            if (matchCsvInfo(csvInfoRow, result)) {
                continue;
            }
            newCandidates.add(result);
        }

        this.manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
                "candidates: recreate=" + recreateCandidates.size() + ", new=" + newCandidates.size());

        // recreate + new が両方空なら（追加無し扱い）
        if (recreateCandidates.isEmpty() && newCandidates.isEmpty()) {

            if (alwaysPutManageFiles) {
                putManageFilesEvenIfNoCsv(statsBucket, prefix, localSeqPath, localTeamPath, currentGroups);
            }

            String messageCd = MessageCdConst.MCD00014I_NO_MAP_DATA;
            String fillChar = "追加レコードがないため処理終了 (既存CSV数: " + csvInfoRow.size() + "件)";
            endLog(METHOD_NAME, messageCd, fillChar);
            return;
        }

        // ====== 9) 新規分を欠番なし連番にする（採番はここで初めて） ======
        // 安定させるため、newCandidates を “最小seq” でソートしてから採番
        newCandidates.sort(Comparator.comparingInt(ExportCsvService::minSeqOfResult));

        int maxOnS3 = getMaxCsvNoFromS3(statsBucket, prefix);
        int nextNo = maxOnS3 + 1;

        List<SimpleEntry<String, List<DataEntity>>> toCreate = new ArrayList<>();

        // recreate は csvNo順
        recreateCandidates.sort(Comparator.comparingInt(e -> {
            Integer n = csvNoFromFilePath(e.getKey());
            return (n == null) ? Integer.MAX_VALUE : n;
        }));
        toCreate.addAll(recreateCandidates);

        // new は nextNo から連番（ここでしか番号が増えない＝欠番が出にくい）
        for (int i = 0; i < newCandidates.size(); i++) {
            int csvNo = nextNo + i;
            String fileName = csvNo + BookMakersCommonConst.CSV;
            String path = LOCAL_DIR.resolve(fileName).toString();
            toCreate.add(new SimpleEntry<>(path, newCandidates.get(i)));
        }

        // ====== 10) 並列生成→ローカル書込→S3 本番prefixへ直接PUT ======
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        List<CompletableFuture<CsvArtifact>> futures = new ArrayList<>(toCreate.size());
        for (SimpleEntry<String, List<DataEntity>> e : toCreate) {
            final String path = e.getKey();
            final List<DataEntity> group = e.getValue();
            futures.add(CompletableFuture.supplyAsync(
                    () -> buildCsvArtifact(path, group, csvArtifactResource), pool));
        }

        int success = 0, failed = 0;
        List<SimpleEntry<String, List<DataEntity>>> succeeded = new ArrayList<>();
        List<SimpleEntry<String, List<DataEntity>>> failedEntries = new ArrayList<>();

        for (int i = 0; i < futures.size(); i++) {
            try {
                CsvArtifact art = futures.get(i).join();
                if (art == null || art.getContent() == null || art.getContent().isEmpty()) {
                    continue;
                }

                // 1) ローカルへ書く
                writeLocalCsv(art);

                // 2) S3 本番prefixへ直接PUT
                putLocalFileToFinal(statsBucket, prefix, Paths.get(art.getFilePath()));

                success++;
                succeeded.add(toCreate.get(i));
            } catch (Exception ex) {
                failed++;
                failedEntries.add(toCreate.get(i));
                this.manageLoggerComponent.debugErrorLog(
                        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                        MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, ex,
                        "CSV作成/PUT(final) 失敗");
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
                "CSVアップロード結果(final put) (成功: " + success + "件, 失敗: " + failed + "件)");

        // ====== 11) data_team_list.txt 更新（ローカル）→ S3へPUT ======
        try {
            upsertDataTeamList(localTeamPath, succeeded, failedEntries);
            putLocalFileToFinal(statsBucket, prefix, localTeamPath);
        } catch (Exception e) {
            this.manageLoggerComponent.debugErrorLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
                    "data_team_list.txt 更新/PUT(final) 失敗");
            throw (e instanceof IOException) ? (IOException) e : new IOException(e);
        }

        // ====== 12) seqList.txt 更新（ローカル）→ S3へPUT ======
        try {
            writeSeqListJson(localSeqPath, currentGroups);
            putLocalFileToFinal(statsBucket, prefix, localSeqPath);
        } catch (Exception e) {
            this.manageLoggerComponent.debugErrorLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
                    "seqList.txt 更新/PUT(final) 失敗");
            throw (e instanceof IOException) ? (IOException) e : new IOException(e);
        }

        // ====== 13) 失敗があれば警告 ======
        if (failed > 0) {
            this.manageLoggerComponent.debugWarnLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    MessageCdConst.MCD00099I_LOG,
                    "失敗があるため一部のみアップロードされている可能性があります（直下PUT方式）");
        }

        endLog(METHOD_NAME, null, null);
    }

    // =========================================================
    // localOnly（S3一切なし）: 欠番を出しにくい採番へ調整
    // =========================================================
    private void executeLocalOnly(Path outDir) throws IOException {
        final String METHOD_NAME = "executeLocalOnly";

        final Path LOCAL_DIR = outDir;
        ensureDir(LOCAL_DIR);

        final Path localSeqPath = LOCAL_DIR.resolve("seqList.txt");
        final Path localTeamPath = LOCAL_DIR.resolve("data_team_list.txt");

        this.manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
                "localOnly=true: S3処理を完全にスキップします。localDir=" + LOCAL_DIR);

        Map<String, List<Integer>> csvInfoRow;
        try {
            bean.init();
            csvInfoRow = (bean != null ? bean.getCsvInfo() : null);
        } catch (Exception e) {
            csvInfoRow = Collections.emptyMap();
            this.manageLoggerComponent.debugWarnLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    MessageCdConst.MCD00099I_LOG,
                    "localOnly: ReaderCurrentCsvInfoBean.init() をスキップ（外部参照の可能性）");
        }
        csvInfoRow = (csvInfoRow != null) ? csvInfoRow : Collections.emptyMap();

        List<List<Integer>> currentGroups = sortSeqs();
        currentGroups = normalizeGroups(currentGroups);

        boolean firstRun = !Files.exists(localSeqPath);
        List<List<Integer>> textGroups;

        if (firstRun) {
            writeSeqListJson(localSeqPath, currentGroups);
            textGroups = Collections.emptyList();
        } else {
            textGroups = readSeqListJson(localSeqPath);
            textGroups = normalizeGroups(textGroups);
        }

        CsvBuildPlan plan;
        if (firstRun) {
            CsvBuildPlan plans = new CsvBuildPlan();
            int i = 0;
            for (List<Integer> curr : currentGroups) {
                plans.newTargets.put(CSV_NEW_PREFIX + "-" + (i++), curr);
            }
            plan = plans;
        } else {
            plan = matchSeqCombPlan(textGroups, currentGroups, csvInfoRow);
        }

        if (plan == null || (plan.recreateByCsvNo.isEmpty() && plan.newTargets.isEmpty())) {
            // localOnlyでも管理ファイルは更新しておく
            upsertDataTeamList(localTeamPath, Collections.emptyList(), Collections.emptyList());
            writeSeqListJson(localSeqPath, currentGroups);

            String messageCd = MessageCdConst.MCD00014I_NO_MAP_DATA;
            String fillChar = "追加レコードがないため処理終了 (既存CSV数: " + csvInfoRow.size() + "件)";
            endLog(METHOD_NAME, messageCd, fillChar);
            return;
        }

        CsvArtifactResource csvArtifactResource;
        try {
            csvArtifactResource = this.helper.getData();
        } catch (Exception e) {
            this.manageLoggerComponent.debugErrorLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e);
            throw (e instanceof IOException) ? (IOException) e : new IOException(e);
        }

        // recreateはパス確定、newは結果だけ
        List<SimpleEntry<String, List<DataEntity>>> recreateCandidates = new ArrayList<>();
        List<List<DataEntity>> newCandidates = new ArrayList<>();

        for (Map.Entry<Integer, List<Integer>> rt : plan.recreateByCsvNo.entrySet()) {
            Integer csvNo = rt.getKey();
            if (csvNo == null) continue;

            String fileName = csvNo + BookMakersCommonConst.CSV;
            String path = LOCAL_DIR.resolve(fileName).toString();
            List<Integer> ids = normalizeSeqList(rt.getValue());

            List<DataEntity> result = fetchAndFilter(ids, csvArtifactResource, METHOD_NAME, "recreate findByData");
            if (result == null) continue;

            if (matchCsvInfo(csvInfoRow, result)) {
                continue;
            }
            recreateCandidates.add(new SimpleEntry<>(path, result));
        }

        for (Map.Entry<String, List<Integer>> entry : plan.newTargets.entrySet()) {
            List<Integer> ids = normalizeSeqList(entry.getValue());
            if (ids.isEmpty()) continue;

            List<DataEntity> result = fetchAndFilter(ids, csvArtifactResource, METHOD_NAME, "new findByData");
            if (result == null) continue;

            if (matchCsvInfo(csvInfoRow, result)) {
                continue;
            }
            newCandidates.add(result);
        }

        // 追加無しでも管理ファイル更新
        if (recreateCandidates.isEmpty() && newCandidates.isEmpty()) {
            upsertDataTeamList(localTeamPath, Collections.emptyList(), Collections.emptyList());
            writeSeqListJson(localSeqPath, currentGroups);

            String messageCd = MessageCdConst.MCD00014I_NO_MAP_DATA;
            String fillChar = "追加レコードがないため処理終了 (既存CSV数: " + csvInfoRow.size() + "件)";
            endLog(METHOD_NAME, messageCd, fillChar);
            return;
        }

        newCandidates.sort(Comparator.comparingInt(ExportCsvService::minSeqOfResult));
        int maxLocal = getMaxCsvNoFromLocal(LOCAL_DIR);
        int nextNo = maxLocal + 1;

        List<SimpleEntry<String, List<DataEntity>>> toCreate = new ArrayList<>();
        recreateCandidates.sort(Comparator.comparingInt(e -> {
            Integer n = csvNoFromFilePath(e.getKey());
            return (n == null) ? Integer.MAX_VALUE : n;
        }));
        toCreate.addAll(recreateCandidates);

        for (int i = 0; i < newCandidates.size(); i++) {
            int csvNo = nextNo + i;
            String fileName = csvNo + BookMakersCommonConst.CSV;
            String path = LOCAL_DIR.resolve(fileName).toString();
            toCreate.add(new SimpleEntry<>(path, newCandidates.get(i)));
        }

        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        List<CompletableFuture<CsvArtifact>> futures = new ArrayList<>(toCreate.size());
        for (SimpleEntry<String, List<DataEntity>> e : toCreate) {
            final String path = e.getKey();
            final List<DataEntity> group = e.getValue();
            futures.add(CompletableFuture.supplyAsync(
                    () -> buildCsvArtifact(path, group, csvArtifactResource), pool));
        }

        int success = 0, failed = 0;
        List<SimpleEntry<String, List<DataEntity>>> succeeded = new ArrayList<>();
        List<SimpleEntry<String, List<DataEntity>>> failedEntries = new ArrayList<>();

        for (int i = 0; i < futures.size(); i++) {
            try {
                CsvArtifact art = futures.get(i).join();
                if (art == null || art.getContent() == null || art.getContent().isEmpty()) {
                    continue;
                }
                writeLocalCsv(art);

                success++;
                succeeded.add(toCreate.get(i));
            } catch (Exception ex) {
                failed++;
                failedEntries.add(toCreate.get(i));
                this.manageLoggerComponent.debugErrorLog(
                        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                        MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, ex,
                        "CSV作成(ローカル) 失敗");
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
                "localOnly: CSV生成結果 (成功: " + success + "件, 失敗: " + failed + "件)");

        // localOnlyでも管理ファイルは更新する
        upsertDataTeamList(localTeamPath, succeeded, failedEntries);
        writeSeqListJson(localSeqPath, currentGroups);

        if (failed > 0) {
            this.manageLoggerComponent.debugWarnLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    MessageCdConst.MCD00099I_LOG,
                    "localOnly: 失敗があるため一部未生成の可能性があります。localDir=" + LOCAL_DIR);
        }

        endLog(METHOD_NAME, null, null);
    }

    // =========================================================
    // seqList.txt JSON形式読み書き
    // =========================================================
    private void writeSeqListJson(Path out, List<List<Integer>> groups) throws IOException {
        String json = SEQ_JSON.writeValueAsString(groups);
        Files.writeString(out, json, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private List<List<Integer>> readSeqListJson(Path path) {
        if (!Files.exists(path))
            return Collections.emptyList();
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (json.isEmpty())
                return Collections.emptyList();

            if (json.startsWith("[")) {
                return SEQ_JSON.readValue(json,
                        new com.fasterxml.jackson.core.type.TypeReference<List<List<Integer>>>() {
                        });
            }

            // 旧形式 fallback（カンマ区切り複数行）
            List<List<Integer>> result = new ArrayList<>();
            for (String line : json.split("\n")) {
                line = line.trim();
                if (line.isEmpty())
                    continue;
                List<Integer> group = new ArrayList<>();
                for (String s : line.split(",")) {
                    s = s.trim();
                    if (!s.isEmpty()) {
                        try {
                            group.add(Integer.valueOf(s));
                        } catch (NumberFormatException ignore) {
                        }
                    }
                }
                if (!group.isEmpty())
                    result.add(group);
            }
            return result;

        } catch (Exception e) {
            this.manageLoggerComponent.debugWarnLog(
                    PROJECT_NAME, CLASS_NAME, "readSeqListJson",
                    MessageCdConst.MCD00099I_LOG,
                    "seqList.txt の読み込みに失敗しました: " + path);
            return Collections.emptyList();
        }
    }

    // =========================================================
    // S3 upload（直下）
    // =========================================================
    private String putLocalFileToFinal(String bucket, String finalPrefix, Path localFile) {
        final String METHOD_NAME = "putLocalFileToFinal";
        String fileName = localFile.getFileName().toString();

        String finalKey = normalizeS3Key(joinS3Key(finalPrefix, fileName));

        this.manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                MessageCdConst.MCD00099I_LOG,
                "UPLOAD(final) bucket=" + bucket + " key=" + finalKey + " localFile=" + localFile);

        try {
            s3Operator.uploadFile(bucket, finalKey, localFile);
            return finalKey;
        } catch (Exception e) {
            this.manageLoggerComponent.debugErrorLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    MessageCdConst.MCD00023E_S3_UPLOAD_FAILED, e,
                    bucket, finalKey);
            throw e;
        }
    }

    /**
     * S3（prefix配下）にある CSV の最大番号を取得する
     * - prefix="" の場合：直下の "6089.csv" を対象
     * - prefix="stats" の場合："stats/6089.csv" も対象
     */
    private int getMaxCsvNoFromS3(String bucket, String prefix) {
        final String METHOD_NAME = "getMaxCsvNoFromS3";

        String p = normalizePrefix(prefix);
        String listPrefix = p.isBlank() ? "" : (p.endsWith("/") ? p : p + "/");

        List<String> keys = s3Operator.listKeys(bucket, listPrefix);
        int max = 0;

        for (String key : keys) {
            if (key == null) continue;

            // tmpが残っていても最大番号判定に混ぜない
            if (key.startsWith("tmp/")) continue;

            Matcher m = CSV_NO_PATTERN.matcher(key);
            if (!m.find()) continue;

            try {
                int n = Integer.parseInt(m.group(2));
                if (n > max) max = n;
            } catch (NumberFormatException ignore) {
            }
        }

        manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
                "S3上の最大CSV番号=" + max + " (bucket=" + bucket + ", prefix=" + listPrefix + ")");
        return max;
    }

    private int getMaxCsvNoFromLocal(Path localDir) {
        final String METHOD_NAME = "getMaxCsvNoFromLocal";
        int max = 0;

        try {
            if (localDir == null || !Files.isDirectory(localDir)) {
                return 0;
            }

            try (var stream = Files.list(localDir)) {
                for (Path p : stream.collect(Collectors.toList())) {
                    if (p == null) continue;
                    String name = p.getFileName().toString();
                    Matcher m = CSV_NO_PATTERN.matcher(name);
                    if (!m.find()) continue;

                    try {
                        int n = Integer.parseInt(m.group(2));
                        if (n > max) max = n;
                    } catch (NumberFormatException ignore) {
                    }
                }
            }
        } catch (Exception e) {
            this.manageLoggerComponent.debugWarnLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    MessageCdConst.MCD00099I_LOG,
                    "ローカル最大CSV番号の取得に失敗 localDir=" + localDir);
            return 0;
        }

        this.manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
                "ローカル最大CSV番号=" + max + " (dir=" + localDir + ")");
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
    // 既存ロジック（グルーピング、plan、CSV生成など）
    // =========================================================

    private List<List<Integer>> sortSeqs() {
        List<SeqWithKey> rows = this.bookCsvDataRepository.findAllSeqsWithKey();
        List<List<Integer>> result = new ArrayList<>();
        List<Integer> bucket = new ArrayList<>();

        String prevHome = null, prevAway = null;

        for (SeqWithKey r : rows) {
            boolean newGroup = prevHome == null
                    || !Objects.equals(prevHome, r.getHomeTeamName())
                    || !Objects.equals(prevAway, r.getAwayTeamName());

            if (newGroup) {
                if (!bucket.isEmpty()) {
                    bucket.sort(Comparator.naturalOrder());
                    result.add(bucket);
                }
                bucket = new ArrayList<>();
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

    private static List<List<Integer>> normalizeGroups(List<List<Integer>> groups) {
        if (groups == null) return Collections.emptyList();
        List<List<Integer>> out = new ArrayList<>();
        for (List<Integer> g : groups) {
            List<Integer> ng = normalizeSeqListStatic(g);
            if (!ng.isEmpty()) out.add(ng);
        }
        return out;
    }

    private List<Integer> normalizeSeqList(List<Integer> src) {
        return normalizeSeqListStatic(src);
    }

    private static List<Integer> normalizeSeqListStatic(List<Integer> src) {
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

        // immutable対策（sortするためmutable化）
        result = new ArrayList<>(result);

        backfillScores(result);
        applyCanonicalCategory(result);

        return result;
    }

    private CsvArtifact buildCsvArtifact(String path, List<DataEntity> result, CsvArtifactResource resource) {
        if (result == null || result.isEmpty()) return null;
        return new CsvArtifact(path, result);
    }

    private void writeLocalCsv(CsvArtifact art) {
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

    private CsvBuildPlan matchSeqCombPlan(
            List<List<Integer>> textSeqs,
            List<List<Integer>> dbSeqs,
            Map<String, List<Integer>> csvInfoRow) {

        CsvBuildPlan plan = new CsvBuildPlan();

        Map<Integer, Integer> minSeqToCsvNo = new LinkedHashMap<>();
        Map<String, Integer> groupKeyToCsvNo = new LinkedHashMap<>();

        for (Map.Entry<String, List<Integer>> e : csvInfoRow.entrySet()) {
            Integer csvNo = parseCsvNo(e.getKey());
            if (csvNo == null) continue;

            List<Integer> ids = normalizeSeqListStatic(e.getValue());
            if (ids.isEmpty()) continue;

            int min = ids.get(0);
            minSeqToCsvNo.put(min, csvNo);
            groupKeyToCsvNo.put(groupKey(ids), csvNo);
        }

        for (List<Integer> dbGroup : dbSeqs) {
            if (dbGroup == null || dbGroup.isEmpty()) continue;

            String gk = groupKey(dbGroup);

            if (groupKeyToCsvNo.containsKey(gk)) {
                continue;
            }

            int min = dbGroup.get(0);
            Integer csvNo = minSeqToCsvNo.get(min);
            if (csvNo != null) {
                plan.recreateByCsvNo.put(csvNo, dbGroup);
            } else {
                plan.newTargets.put(CSV_NEW_PREFIX + "-" + min, dbGroup);
            }
        }

        return plan;
    }

    private static String groupKey(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (Integer n : ids) {
            if (n == null) continue;
            if (sb.length() > 0) sb.append('-');
            sb.append(n);
        }
        return sb.toString();
    }

    private static Integer parseCsvNo(String keyOrName) {
        if (keyOrName == null) return null;
        Matcher m = CSV_NO_PATTERN.matcher(keyOrName);
        if (!m.find()) return null;
        try {
            return Integer.valueOf(m.group(2));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // =========================================================
    // data_team_list.txt 更新
    // =========================================================
    private void upsertDataTeamList(
            Path out,
            List<SimpleEntry<String, List<DataEntity>>> succeeded,
            List<SimpleEntry<String, List<DataEntity>>> failed) throws IOException {

        Map<Integer, String> csvNoToLine = new LinkedHashMap<>();

        if (Files.exists(out)) {
            List<String> lines = Files.readAllLines(out, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line == null) continue;
                String t = line.trim();
                if (t.isEmpty()) continue;

                String[] parts = t.split("\t", 2);
                if (parts.length < 1) continue;

                Integer csvNo = parseCsvNo(parts[0].trim());
                if (csvNo == null) continue;

                String desc = (parts.length >= 2) ? parts[1] : "";
                csvNoToLine.put(csvNo, (csvNo + BookMakersCommonConst.CSV) + "\t" + desc);
            }
        } else {
            Files.createDirectories(out.getParent());
            Files.writeString(out, "", StandardCharsets.UTF_8);
        }

        if (failed != null) {
            for (SimpleEntry<String, List<DataEntity>> e : failed) {
                Integer csvNo = csvNoFromFilePath(e.getKey());
                if (csvNo != null) {
                    csvNoToLine.remove(csvNo);
                }
            }
        }

        if (succeeded != null) {
            for (SimpleEntry<String, List<DataEntity>> e : succeeded) {
                Integer csvNo = csvNoFromFilePath(e.getKey());
                List<DataEntity> list = e.getValue();
                if (csvNo == null || list == null || list.isEmpty()) continue;

                DataEntity row = findRowWithTeams(list);

                String dataCategory = safe(row.getDataCategory()).trim();
                String home = safe(row.getHomeTeamName()).trim();
                String away = safe(row.getAwayTeamName()).trim();

                String vsPart;
                if (!home.isEmpty() && !away.isEmpty()) {
                    vsPart = home + "vs" + away;
                } else if (!home.isEmpty()) {
                    vsPart = home;
                } else if (!away.isEmpty()) {
                    vsPart = away;
                } else {
                    vsPart = "(team name empty)";
                }

                String desc;
                if (!dataCategory.isEmpty() && !vsPart.isEmpty()) {
                    desc = dataCategory + " - " + vsPart;
                } else if (!dataCategory.isEmpty()) {
                    desc = dataCategory;
                } else {
                    desc = vsPart;
                }

                String line = (csvNo + BookMakersCommonConst.CSV) + "\t" + desc;
                csvNoToLine.put(csvNo, line);
            }
        }

        List<Map.Entry<Integer, String>> entries = new ArrayList<>(csvNoToLine.entrySet());
        entries.sort(Map.Entry.comparingByKey());

        List<String> outLines = new ArrayList<>();
        for (Map.Entry<Integer, String> en : entries) {
            outLines.add(en.getValue());
        }

        Files.write(out, outLines, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static DataEntity findRowWithTeams(List<DataEntity> list) {
        for (DataEntity d : list) {
            String home = safe(d.getHomeTeamName()).trim();
            String away = safe(d.getAwayTeamName()).trim();
            if (!home.isEmpty() || !away.isEmpty()) {
                return d;
            }
        }
        return list.get(0);
    }

    private static String safe(String s) {
        return (s == null) ? "" : s;
    }

    // =========================================================
    // score / category 補完
    // =========================================================
    private static void backfillScores(List<DataEntity> list) {
        if (list == null || list.isEmpty()) return;

        list.sort(Comparator
                .comparing((DataEntity d) -> {
                    String rt = d.getRecordTime();
                    return (rt == null) ? "" : rt;
                })
                .thenComparingInt(d -> {
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

    private static boolean hasRound(String s) {
        return s != null && ROUND_TOKEN.matcher(s).find();
    }

    private static String pickCanonicalCategory(List<DataEntity> group) {
        if (group == null || group.isEmpty()) return "";
        for (DataEntity d : group) {
            String cat = d.getDataCategory();
            if (hasRound(cat)) return cat.trim();
        }
        String first = group.get(0).getDataCategory();
        return first == null ? "" : first.trim();
    }

    private static void applyCanonicalCategory(List<DataEntity> group) {
        String canonical = pickCanonicalCategory(group);
        if (canonical.isBlank()) return;

        for (DataEntity d : group) {
            String cat = d.getDataCategory();
            if (cat == null || cat.trim().isEmpty() || !hasRound(cat)) {
                d.setDataCategory(canonical);
            }
        }
    }

    // =========================================================
    // util
    // =========================================================
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String normalizeS3Key(String key) {
        if (key == null) return null;
        String k = key;
        while (k.startsWith("/")) {
            k = k.substring(1);
        }
        return k;
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null) return "";
        String p = prefix.trim();
        p = p.replaceAll("^/+", "");
        p = p.replaceAll("/+$", "");
        return p;
    }

    private static String joinS3Key(String prefix, String fileName) {
        String p = (prefix == null) ? "" : prefix.trim();
        p = p.replaceAll("^/+", "");
        p = p.replaceAll("/+$", "");

        String f = (fileName == null) ? "" : fileName.trim();
        f = f.replaceAll("^/+", "");

        if (p.isBlank()) return f;
        return p + "/" + f;
    }

    private static int minSeqOfResult(List<DataEntity> list) {
        if (list == null || list.isEmpty()) return Integer.MAX_VALUE;
        int min = Integer.MAX_VALUE;
        for (DataEntity d : list) {
            if (d == null || d.getSeq() == null) continue;
            try {
                int v = Integer.parseInt(String.valueOf(d.getSeq()));
                if (v < min) min = v;
            } catch (NumberFormatException ignore) {
            }
        }
        return min;
    }

    private static Integer csvNoFromFilePath(String filePath) {
        if (filePath == null) return null;
        return parseCsvNo(filePath);
    }

    private void putManageFilesEvenIfNoCsv(
            String statsBucket,
            String prefix,
            Path localSeqPath,
            Path localTeamPath,
            List<List<Integer>> currentGroups) throws IOException {

        // teamList：空でPUTしても「存在保証」になる（内容は維持される）
        upsertDataTeamList(localTeamPath, Collections.emptyList(), Collections.emptyList());
        putLocalFileToFinal(statsBucket, prefix, localTeamPath);

        // seqList：DBの最新グルーピングを書いてPUT
        writeSeqListJson(localSeqPath, currentGroups);
        putLocalFileToFinal(statsBucket, prefix, localSeqPath);
    }

    private void endLog(String method, String messageCd, String fillChar) {
        if (messageCd != null && fillChar != null) {
            this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, method, messageCd, fillChar);
        }
        this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, method, "end");
    }
}
