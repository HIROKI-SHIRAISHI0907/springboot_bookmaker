package dev.application.analyze.bm_m007_bm_m016;

import lombok.Data;

/**
 * 更新保持データ
 * @author shiraishitoshio
 *
 */
@Data
public class UpdateData {

	/** id */
	private String id;

	/** target */
    private String target;

    /** search */
    private String search;

    /** table */
    private String table;

    /**
     * コンストラクタ
     * @param id ID
     * @param target 対象数
     * @param search 探索数
     * @param table テーブル
     */
    public UpdateData(String id, String target, String search, String table) {
        this.id = id;
        this.target = target;
        this.search = search;
        this.table = table;
    }

}
