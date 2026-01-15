package dev.common.s3;

import java.nio.file.Files;
import java.nio.file.Path;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3オペレーター
 * @author shiraishitoshio
 */
public class S3Operator {

    private final S3Client s3;

    public S3Operator(String region) {
        this.s3 = S3Client.builder()
                .region(Region.of(region))
                .build();
    }

    public void uploadFile(String bucket, String key, Path file) {
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        s3.putObject(req, RequestBody.fromFile(file));
    }

    /** ディレクトリ配下のファイルをまとめてアップロード（必要なら） */
    public void uploadDirectory(String bucket, String prefix, Path dir) throws Exception {
        if (!Files.exists(dir)) return;

        Files.walk(dir)
                .filter(Files::isRegularFile)
                .forEach(p -> {
                    String key = prefix + "/" + dir.relativize(p).toString().replace("\\", "/");
                    uploadFile(bucket, key, p);
                });
    }
}
