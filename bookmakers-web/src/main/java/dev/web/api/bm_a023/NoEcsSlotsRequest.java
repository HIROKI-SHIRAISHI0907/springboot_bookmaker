package dev.web.api.bm_a023;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * noEcsRun JSON取得リクエスト
 */
@Data
public class NoEcsSlotsRequest {

    /**
     * S3JobPropertiesConfig に定義されている batchCode
     * 例: NO_ECS_RUN
     */
    private String batchCode;

    /**
     * 対象日
     * 未指定時は JST の当日
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate day;

    /**
     * 任意のファイル名を直接指定したい場合
     * 例: ecs_slots_2026-06-30.json
     *
     * これが指定されていれば day より優先
     */
    private String fileName;
}
