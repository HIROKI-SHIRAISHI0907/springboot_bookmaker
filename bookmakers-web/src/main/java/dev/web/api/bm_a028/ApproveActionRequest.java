package dev.web.api.bm_a028;

import lombok.Data;

/**
 * 承認 / 差し戻し / 取り消し / 確認 の各アクション共通のリクエストボディ。
 * comment は差し戻し・取り消しの場合は必須（サービス層でバリデーションする）。
 * 承認・確認の場合は省略可。
 */
@Data
public class ApproveActionRequest {

    private String comment;
}
