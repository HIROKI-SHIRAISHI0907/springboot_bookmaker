package dev.application.analyze.bm_m022;

import lombok.Data;

/**
 * CsvSequenceEntity
 * @author shiraishitoshio
 *
 */
@Data
public class CsvSequenceEntity {

	/** 1固定 */
    private Integer id;        // 1固定運用

    /** 読み込み済連番 */
    private Integer csvNumber; // 最終読み込み済み連番

}
