package dev.web.api.bm_w009;

import lombok.Data;

/**
 * 選手のDTO
 * /api/{country}/{league}/{team}/players
 *
 * @author shiraishitoshio
 */
@Data
public class PlayerDTO {

	/** id */
    private Long id;

    /** 背番号 */
    private Integer jersey;

    /** 名前 */
    private String name;

    /** 顔写真 */
    private String face;

    /** ポジション */
    private String position;       // 例: ゴールキーパー / ディフェンダー / ...

    /** 誕生日 */
    private String birth;          // YYYY-MM-DD

    /** 年齢 */
    private Integer age;

    /** 市場価値 */
    private String marketValue;

    /** 身長 */
    private String height;         // "180cm" など

    /** 体重 */
    private String weight;         // "75kg" など

    /** ローン所属元 */
    private String loanBelong;

    /** 所属リスト */
    private String belongList;

    /** 怪我 */
    private String injury;

    /** 契約年月日 */
    private String contractUntil;  // YYYY-MM-DD

    /** 最新情報日付 */
    private String latestInfoDate; // YYYY-MM-DD
}
