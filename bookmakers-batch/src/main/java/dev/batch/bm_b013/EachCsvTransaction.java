package dev.batch.bm_b013;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.bm.CsvDetailManageBatchRepository;
import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.entity.CsvDetailManageEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;

/**
 * CSV関係の削除
 */
@Component
@Transactional(rollbackFor = Exception.class)
public class EachCsvTransaction {

    private static final String PROJECT_NAME = EachCsvTransaction.class.getProtectionDomain()
            .getCodeSource().getLocation().getPath();

    private static final String CLASS_NAME = EachCsvTransaction.class.getName();

    @Value("${exportcsv.local-only:false}")
    private boolean localOnly;

    @Value("${exportcsv.final-prefix:}")
    private String finalPrefix;

    @Autowired
    private CsvDetailManageBatchRepository csvDetailManageBatchRepository;

    @Autowired
    private PathConfig config;

    @Autowired
    private S3Operator s3Operator;

    @Autowired
    private ManageLoggerComponent manageLoggerComponent;

    /**
     * 実行メソッド
     */
    public void execute(TransactionDTO dto) throws Exception {
        final String METHOD_NAME = "execute";

        this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, "start");

        List<String> prefixes = buildDataCategoryPrefixes(dto);
        if (prefixes.isEmpty()) {
            this.manageLoggerComponent.debugInfoLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    MessageCdConst.MCD00099I_LOG,
                    "削除対象の countryLeague が空のため処理終了");
            this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, "end");
            return;
        }

        List<CsvDetailManageEntity> targets =
                this.csvDetailManageBatchRepository.findDeleteTargetsByDataCategoryPrefixes(prefixes);

        if (targets == null || targets.isEmpty()) {
            this.manageLoggerComponent.debugInfoLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    MessageCdConst.MCD00099I_LOG,
                    "削除対象の csv_detail_manage が存在しません");
            this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, "end");
            return;
        }

        Set<String> csvIdSet = new LinkedHashSet<>();
        for (CsvDetailManageEntity entity : targets) {
            if (entity == null) {
                continue;
            }
            String csvId = safe(entity.getCsvId()).trim();
            if (!csvId.isEmpty()) {
                csvIdSet.add(csvId);
            }
        }

        if (csvIdSet.isEmpty()) {
            this.manageLoggerComponent.debugInfoLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    MessageCdConst.MCD00099I_LOG,
                    "削除対象 csv_id が存在しません");
            this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, "end");
            return;
        }

        List<String> csvIds = new ArrayList<>(csvIdSet);

        this.manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                MessageCdConst.MCD00099I_LOG,
                "削除対象 csv_id 件数=" + csvIds.size());

        // 1) 実CSV削除（local / S3）
        deletePhysicalCsvFiles(csvIds, METHOD_NAME);

        // 2) data_team_list.txt からも除去
        updateDataTeamList(csvIdSet, METHOD_NAME);

        // 3) csv_detail_manage 削除
        int deleted = this.csvDetailManageBatchRepository.deleteByCsvIds(csvIds);

        this.manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                MessageCdConst.MCD00099I_LOG,
                "csv_detail_manage 削除件数=" + deleted);

        this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, "end");
    }

    /**
     * DTO の countryLeague から data_category prefix を作成
     * 例: 日本-J1リーグ -> 日本: J1リーグ
     */
    private List<String> buildDataCategoryPrefixes(TransactionDTO dto) {
        List<String> prefixes = new ArrayList<>();
        if (dto == null || dto.getCountryLeague() == null) {
            return prefixes;
        }

        for (String value : dto.getCountryLeague()) {
            String[] pair = splitCountryLeague(value);
            String country = safe(pair[0]).trim();
            String league = safe(pair[1]).trim();

            if (country.isEmpty() || league.isEmpty()) {
                continue;
            }

            prefixes.add(country + ": " + league);
        }

        return prefixes;
    }

    /**
     * ExportCsvService が作成した CSV 実体を削除
     * - ローカル: csvFolder/csv_id
     * - S3: finalPrefix/csv_id
     */
    private void deletePhysicalCsvFiles(List<String> csvIds, String parentMethod) throws IOException {
        final String METHOD_NAME = "deletePhysicalCsvFiles";

        Path baseDir = Paths.get(config.getCsvFolder()).toAbsolutePath().normalize();
        String bucket = config.getS3BucketsStats();
        String prefix = normalizePrefix(finalPrefix);

        for (String csvId : csvIds) {
            if (csvId == null || csvId.isBlank()) {
                continue;
            }

            Path localPath = baseDir.resolve(csvId).normalize();

            try {
                boolean deletedLocal = Files.deleteIfExists(localPath);

                this.manageLoggerComponent.debugInfoLog(
                        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                        MessageCdConst.MCD00099I_LOG,
                        "ローカルCSV削除: csvId=" + csvId
                                + ", path=" + localPath
                                + ", deleted=" + deletedLocal);
            } catch (Exception e) {
                this.manageLoggerComponent.debugErrorLog(
                        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                        MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
                        "ローカルCSV削除失敗: csvId=" + csvId + ", path=" + localPath);
                throw (e instanceof IOException) ? (IOException) e : new IOException(e);
            }

            if (!localOnly) {
                String s3Key = normalizeS3Key(joinS3Key(prefix, csvId));

                try {
                    // 実際の S3Operator の削除メソッド名に合わせてここだけ調整してください
                    // 例: s3Operator.deleteObject(bucket, s3Key);
                    s3Operator.delete(bucket, s3Key);
                    this.manageLoggerComponent.debugInfoLog(
                            PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                            MessageCdConst.MCD00099I_LOG,
                            "S3 CSV削除: bucket=" + bucket + ", key=" + s3Key);
                } catch (Exception e) {
                    this.manageLoggerComponent.debugErrorLog(
                            PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                            MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
                            "S3 CSV削除失敗: bucket=" + bucket + ", key=" + s3Key);
                    throw new IOException(e);
                }
            }
        }
    }

    /**
     * ExportCsvService が管理している data_team_list.txt から対象 csv_id を削除
     */
    private void updateDataTeamList(Set<String> deleteCsvIds, String parentMethod) throws IOException {
        final String METHOD_NAME = "updateDataTeamList";

        Path baseDir = Paths.get(config.getCsvFolder()).toAbsolutePath().normalize();
        Path localTeamPath = baseDir.resolve("data_team_list.txt");

        String bucket = config.getS3BucketsStats();
        String prefix = normalizePrefix(finalPrefix);
        String teamKey = normalizeS3Key(joinS3Key(prefix, "data_team_list.txt"));

        if (!localOnly) {
            try {
                if (localTeamPath.getParent() != null) {
                    Files.createDirectories(localTeamPath.getParent());
                }
                s3Operator.downloadToFile(bucket, teamKey, localTeamPath);
            } catch (Exception e) {
                this.manageLoggerComponent.debugWarnLog(
                        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                        MessageCdConst.MCD00099I_LOG,
                        "data_team_list.txt ダウンロード失敗または未存在. bucket=" + bucket + ", key=" + teamKey);
            }
        }

        if (!Files.exists(localTeamPath)) {
            this.manageLoggerComponent.debugInfoLog(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    MessageCdConst.MCD00099I_LOG,
                    "data_team_list.txt が存在しないため更新スキップ");
            return;
        }

        List<String> lines = Files.readAllLines(localTeamPath, StandardCharsets.UTF_8);
        List<String> newLines = new ArrayList<>();

        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }

            String[] parts = line.split("\t", 2);
            String csvKey = safe(parts[0]).trim();

            if (deleteCsvIds.contains(csvKey)) {
                continue;
            }

            newLines.add(line);
        }

        Files.write(
                localTeamPath,
                newLines,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        this.manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                MessageCdConst.MCD00099I_LOG,
                "data_team_list.txt 更新完了. path=" + localTeamPath + ", remaining=" + newLines.size());

        if (!localOnly) {
            try {
                s3Operator.uploadFile(bucket, teamKey, localTeamPath);

                this.manageLoggerComponent.debugInfoLog(
                        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                        MessageCdConst.MCD00099I_LOG,
                        "data_team_list.txt S3反映完了. bucket=" + bucket + ", key=" + teamKey);
            } catch (Exception e) {
                this.manageLoggerComponent.debugErrorLog(
                        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                        MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION, e,
                        "data_team_list.txt S3反映失敗. bucket=" + bucket + ", key=" + teamKey);
                throw new IOException(e);
            }
        }
    }

    private String[] splitCountryLeague(String value) {
        if (value == null || value.isBlank()) {
            return new String[] { "", "" };
        }

        int idx = value.indexOf('-');
        if (idx < 0) {
            return new String[] { value.trim(), "" };
        }

        String country = value.substring(0, idx).trim();
        String league = value.substring(idx + 1).trim();
        return new String[] { country, league };
    }

    private static String safe(String s) {
        return (s == null) ? "" : s;
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null) {
            return "";
        }
        String p = prefix.trim();
        p = p.replaceAll("^/+", "");
        p = p.replaceAll("/+$", "");
        return p;
    }

    private static String normalizeS3Key(String key) {
        if (key == null) {
            return null;
        }
        String k = key;
        while (k.startsWith("/")) {
            k = k.substring(1);
        }
        return k;
    }

    private static String joinS3Key(String prefix, String fileName) {
        String p = (prefix == null) ? "" : prefix.trim();
        p = p.replaceAll("^/+", "");
        p = p.replaceAll("/+$", "");

        String f = (fileName == null) ? "" : fileName.trim();
        f = f.replaceAll("^/+", "");

        if (p.isBlank()) {
            return f;
        }
        return p + "/" + f;
    }
}
