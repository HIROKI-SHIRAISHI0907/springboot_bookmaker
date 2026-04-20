package dev.application.analyze.bm_m031;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PointSettingModel {

	/** 勝ち */
    private Integer win;

    /** 負け */
    private Integer lose;

    /** 引き分け */
    private Integer draw;

    /** 備考 */
    private String remarks;

}
