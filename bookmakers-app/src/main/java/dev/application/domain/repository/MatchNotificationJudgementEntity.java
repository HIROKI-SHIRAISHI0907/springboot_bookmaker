package dev.application.domain.repository;

import lombok.Data;

@Data
public class MatchNotificationJudgementEntity {

	/** 通番ID */
    private String seq;

	/** ID */
    private String conditionResultDataSeqId;

    /** 判定結果 */
    private String judge;

    /** 更新時間 */
    private String updateTime;

}
