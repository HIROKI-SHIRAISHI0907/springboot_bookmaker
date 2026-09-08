package dev.web.api.bm_a030;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 画面から送られてくるコスト照会リクエスト。
 *
 * accessKeyId / secretAccessKey はサーバー側に一切保存せず、
 * このリクエスト処理中にのみ AWS SDK のクライアント生成に使い、
 * 処理が終わればインスタンスごと破棄する(ログにも出力しないこと)。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CostQueryRequest {

    /** AWS IAM アクセスキーID */
    private String accessKeyId;

    /** AWS IAM シークレットアクセスキー */
    private String secretAccessKey;

    /** STSの一時的な認証情報を使う場合のセッショントークン(通常は未使用) */
    private String sessionToken;

    /** 取得開始日(yyyy-MM-dd、この日を含む) */
    private String startDate;

    /** 取得終了日(yyyy-MM-dd、この日を含む) */
    private String endDate;

    /** DAILY / MONTHLY */
    private String granularity = "MONTHLY";

    /** サービス別内訳を含めるかどうか */
    private boolean groupByService = true;
}
