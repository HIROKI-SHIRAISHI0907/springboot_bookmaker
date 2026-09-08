package dev.web.api.bm_a030;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 「認証確認(GetCallerIdentity)」専用の軽量リクエスト。
 * 画面の初期ゲート(=キーを入力するまで先の画面に進めない)で使う。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AwsCredentialsRequest {
    private String accessKeyId;
    private String secretAccessKey;
    private String sessionToken;
}
