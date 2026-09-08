package dev.web.api.bm_a030;

import org.springframework.stereotype.Component;

import dev.web.exception.AwsCostException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.sts.StsClient;

/**
 * 画面から渡された一時的な認証情報(アクセスキー/シークレットキー)を使って、
 * リクエスト単位で AWS SDK のクライアントを組み立てるファクトリ。
 *
 * ポイント:
 * - 認証情報はサーバーに保存しない(DB/ファイル/キャッシュに書き込まない)
 * - クライアントは try-with-resources で必ず close() する
 * - Cost Explorer は AWS 上ではグローバルなサービスだが、SDK上は
 *   us-east-1 リージョンのエンドポイントを使う必要がある
 */
@Component
public class AwsClientFactory {

    private static final Region COST_EXPLORER_REGION = Region.US_EAST_1;

    public CostExplorerClient createCostExplorerClient(String accessKeyId, String secretAccessKey, String sessionToken) {
        return CostExplorerClient.builder()
                .region(COST_EXPLORER_REGION)
                .credentialsProvider(StaticCredentialsProvider.create(toCredentials(accessKeyId, secretAccessKey, sessionToken)))
                .build();
    }

    public StsClient createStsClient(String accessKeyId, String secretAccessKey, String sessionToken) {
        return StsClient.builder()
                .region(COST_EXPLORER_REGION)
                .credentialsProvider(StaticCredentialsProvider.create(toCredentials(accessKeyId, secretAccessKey, sessionToken)))
                .build();
    }

    private AwsCredentials toCredentials(String accessKeyId, String secretAccessKey, String sessionToken) {
        if (accessKeyId == null || accessKeyId.isBlank() || secretAccessKey == null || secretAccessKey.isBlank()) {
            throw new AwsCostException("アクセスキーIDとシークレットアクセスキーを入力してください。", 401);
        }
        if (sessionToken != null && !sessionToken.isBlank()) {
            return AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken);
        }
        return AwsBasicCredentials.create(accessKeyId, secretAccessKey);
    }

    public static AwsCredentialsRequest requireCredentials(AwsCredentialsRequest req) {
        if (req == null) {
            throw new AwsCostException("認証情報が指定されていません。", 401);
        }
        return req;
    }
}
