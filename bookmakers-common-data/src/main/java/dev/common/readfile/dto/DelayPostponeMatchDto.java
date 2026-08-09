package dev.common.readfile.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 延期・遅延試合DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DelayPostponeMatchDto {

	/** POSTPONED / DELAYED */
    private String statusType;

    /** カテゴリ */
    private String category;

    /** ホーム */
    private String home;

    /** アウェイ */
    private String away;

    /** 読み込み元 S3 key */
    private String sourceKey;

}
