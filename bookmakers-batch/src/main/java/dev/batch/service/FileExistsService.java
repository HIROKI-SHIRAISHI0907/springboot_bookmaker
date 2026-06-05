package dev.batch.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;

/**
 * 管理ファイル存在確認＆ダウンロード／アップロード用サービス
 *
 * 対象:
 * - seqList.txt
 * - data_team_list.txt
 *
 * 動作:
 * - 指定bucket/prefix配下に対象ファイルが存在すればローカルへダウンロード
 * - ローカルに対象ファイルが存在すればS3へアップロード
 * - 存在しない/取得失敗時は false を返却
 */
@Service
public class FileExistsService {

    private static final String PROJECT_NAME = FileExistsService.class.getProtectionDomain()
            .getCodeSource().getLocation().getPath();

    private static final String CLASS_NAME = FileExistsService.class.getName();

    public static final String SEQ_FILE_NAME = "seqList.txt";
    public static final String TEAM_FILE_NAME = "data_team_list.txt";

    @Autowired
    private S3Operator s3Operator;

    @Autowired
    private PathConfig pathConfig;

    @Autowired
    private ManageLoggerComponent manageLoggerComponent;

    /**
     * seqList.txt を存在確認し、存在すればダウンロードする
     *
     * @param bucket S3 bucket
     * @param prefix S3 prefix
     * @return ダウンロード成功時 true
     */
    public boolean downloadSeqListIfExists(String bucket, String prefix) {
        Path localDir = Paths.get(pathConfig.getCsvFolder()).toAbsolutePath().normalize();
        Path localPath = localDir.resolve(SEQ_FILE_NAME);
        return downloadIfExists(bucket, prefix, SEQ_FILE_NAME, localPath, "seqList.txt download");
    }

    /**
     * data_team_list.txt を存在確認し、存在すればダウンロードする
     *
     * @param bucket S3 bucket
     * @param prefix S3 prefix
     * @return ダウンロード成功時 true
     */
    public boolean downloadDataTeamListIfExists(String bucket, String prefix) {
        Path localDir = Paths.get(pathConfig.getCsvFolder()).toAbsolutePath().normalize();
        Path localPath = localDir.resolve(TEAM_FILE_NAME);
        return downloadIfExists(bucket, prefix, TEAM_FILE_NAME, localPath, "data_team_list.txt download");
    }

    /**
     * 任意ファイルの存在確認兼ダウンロード
     *
     * @param bucket S3 bucket
     * @param prefix S3 prefix
     * @param fileName 対象ファイル名
     * @param localPath ローカル保存先
     * @param label ログ用ラベル
     * @return ダウンロード成功時 true
     */
    public boolean downloadIfExists(
            String bucket,
            String prefix,
            String fileName,
            Path localPath,
            String label) {

        final String METHOD_NAME = "downloadIfExists";

        String key = normalizeS3Key(joinS3Key(prefix, fileName));

        try {
            if (localPath != null && localPath.getParent() != null) {
                Files.createDirectories(localPath.getParent());
            }

            manageLoggerComponent.debugInfoLog(
                    PROJECT_NAME,
                    CLASS_NAME,
                    METHOD_NAME,
                    MessageCdConst.MCD00099I_LOG,
                    "開始 label=" + label
                            + ", bucket=" + bucket
                            + ", key=" + key
                            + ", localPath=" + localPath);

            s3Operator.downloadToFile(bucket, key, localPath);

            boolean exists = Files.exists(localPath);

            manageLoggerComponent.debugInfoLog(
                    PROJECT_NAME,
                    CLASS_NAME,
                    METHOD_NAME,
                    MessageCdConst.MCD00099I_LOG,
                    "成功 label=" + label
                            + ", bucket=" + bucket
                            + ", key=" + key
                            + ", downloaded=" + exists);

            return exists;

        } catch (Exception e) {
            manageLoggerComponent.debugWarnLog(
                    PROJECT_NAME,
                    CLASS_NAME,
                    METHOD_NAME,
                    MessageCdConst.MCD00099I_LOG,
                    "未存在または取得失敗 label=" + label
                            + ", bucket=" + bucket
                            + ", key=" + key
                            + ", localPath=" + localPath
                            + ", reason=" + e.getMessage());

            return false;
        }
    }

    /**
     * seqList.txt をローカルからS3へアップロードする
     *
     * @param bucket S3 bucket
     * @param prefix S3 prefix
     * @return アップロード成功時 true
     */
    public boolean uploadSeqListIfExists(String bucket, String prefix) {
        Path localDir = Paths.get(pathConfig.getCsvFolder()).toAbsolutePath().normalize();
        Path localPath = localDir.resolve(SEQ_FILE_NAME);
        return uploadIfExists(bucket, prefix, SEQ_FILE_NAME, localPath, "seqList.txt upload");
    }

    /**
     * data_team_list.txt をローカルからS3へアップロードする
     *
     * @param bucket S3 bucket
     * @param prefix S3 prefix
     * @return アップロード成功時 true
     */
    public boolean uploadDataTeamListIfExists(String bucket, String prefix) {
        Path localDir = Paths.get(pathConfig.getCsvFolder()).toAbsolutePath().normalize();
        Path localPath = localDir.resolve(TEAM_FILE_NAME);
        return uploadIfExists(bucket, prefix, TEAM_FILE_NAME, localPath, "data_team_list.txt upload");
    }

    /**
     * 任意ファイルの存在確認兼アップロード
     *
     * @param bucket S3 bucket
     * @param prefix S3 prefix
     * @param fileName 対象ファイル名
     * @param localPath ローカルファイル
     * @param label ログ用ラベル
     * @return アップロード成功時 true
     */
    public boolean uploadIfExists(
            String bucket,
            String prefix,
            String fileName,
            Path localPath,
            String label) {

        final String METHOD_NAME = "uploadIfExists";

        String key = normalizeS3Key(joinS3Key(prefix, fileName));

        try {
            if (localPath == null || !Files.exists(localPath) || !Files.isRegularFile(localPath)) {
                manageLoggerComponent.debugWarnLog(
                        PROJECT_NAME,
                        CLASS_NAME,
                        METHOD_NAME,
                        MessageCdConst.MCD00099I_LOG,
                        "対象ファイルなし label=" + label
                                + ", bucket=" + bucket
                                + ", key=" + key
                                + ", localPath=" + localPath);
                return false;
            }

            manageLoggerComponent.debugInfoLog(
                    PROJECT_NAME,
                    CLASS_NAME,
                    METHOD_NAME,
                    MessageCdConst.MCD00099I_LOG,
                    "開始 label=" + label
                            + ", bucket=" + bucket
                            + ", key=" + key
                            + ", localPath=" + localPath
                            + ", size=" + Files.size(localPath));

            s3Operator.uploadFile(bucket, key, localPath);

            manageLoggerComponent.debugInfoLog(
                    PROJECT_NAME,
                    CLASS_NAME,
                    METHOD_NAME,
                    MessageCdConst.MCD00099I_LOG,
                    "成功 label=" + label
                            + ", bucket=" + bucket
                            + ", key=" + key
                            + ", localPath=" + localPath);

            return true;

        } catch (Exception e) {
            manageLoggerComponent.debugWarnLog(
                    PROJECT_NAME,
                    CLASS_NAME,
                    METHOD_NAME,
                    MessageCdConst.MCD00099I_LOG,
                    "アップロード失敗 label=" + label
                            + ", bucket=" + bucket
                            + ", key=" + key
                            + ", localPath=" + localPath
                            + ", reason=" + e.getMessage());

            return false;
        }
    }

    /**
     * 2ファイルまとめて取得する
     *
     * @param bucket S3 bucket
     * @param prefix S3 prefix
     * @param localDir ローカル出力先ディレクトリ
     * @return 両方ともダウンロード成功した場合 true
     */
    public boolean downloadManageFilesIfExists(String bucket, String prefix, Path localDir) {
        final String METHOD_NAME = "downloadManageFilesIfExists";

        try {
            Files.createDirectories(localDir);
        } catch (Exception e) {
            manageLoggerComponent.debugWarnLog(
                    PROJECT_NAME,
                    CLASS_NAME,
                    METHOD_NAME,
                    MessageCdConst.MCD00099I_LOG,
                    "ローカルディレクトリ作成失敗 localDir=" + localDir
                            + ", reason=" + e.getMessage());
            return false;
        }

        boolean seqExists = downloadIfExists(
                bucket,
                prefix,
                SEQ_FILE_NAME,
                localDir.resolve(SEQ_FILE_NAME),
                "seqList.txt download");

        boolean teamExists = downloadIfExists(
                bucket,
                prefix,
                TEAM_FILE_NAME,
                localDir.resolve(TEAM_FILE_NAME),
                "data_team_list.txt download");

        manageLoggerComponent.debugInfoLog(
                PROJECT_NAME,
                CLASS_NAME,
                METHOD_NAME,
                MessageCdConst.MCD00099I_LOG,
                "管理ファイル取得結果 seqExists=" + seqExists
                        + ", teamExists=" + teamExists);

        return seqExists && teamExists;
    }

    /**
     * 2ファイルまとめてアップロードする
     *
     * @param bucket S3 bucket
     * @param prefix S3 prefix
     * @param localDir ローカル入力元ディレクトリ
     * @return 両方ともアップロード成功した場合 true
     */
    public boolean uploadManageFilesIfExists(String bucket, String prefix, Path localDir) {
        final String METHOD_NAME = "uploadManageFilesIfExists";

        boolean seqUploaded = uploadIfExists(
                bucket,
                prefix,
                SEQ_FILE_NAME,
                localDir.resolve(SEQ_FILE_NAME),
                "seqList.txt upload");

        boolean teamUploaded = uploadIfExists(
                bucket,
                prefix,
                TEAM_FILE_NAME,
                localDir.resolve(TEAM_FILE_NAME),
                "data_team_list.txt upload");

        manageLoggerComponent.debugInfoLog(
                PROJECT_NAME,
                CLASS_NAME,
                METHOD_NAME,
                MessageCdConst.MCD00099I_LOG,
                "管理ファイルアップロード結果 seqUploaded=" + seqUploaded
                        + ", teamUploaded=" + teamUploaded);

        return seqUploaded && teamUploaded;
    }

    private static String normalizeS3Key(String key) {
        if (key == null) {
            return "";
        }
        String k = key;
        while (k.startsWith("/")) {
            k = k.substring(1);
        }
        return k;
    }

    private static String joinS3Key(String prefix, String fileName) {
        String p = prefix == null ? "" : prefix.trim();
        p = p.replaceAll("^/+", "");
        p = p.replaceAll("/+$", "");

        String f = fileName == null ? "" : fileName.trim();
        f = f.replaceAll("^/+", "");

        if (p.isBlank()) {
            return f;
        }
        return p + "/" + f;
    }
}
