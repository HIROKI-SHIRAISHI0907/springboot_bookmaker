package dev.web.api.bm_a025;

import lombok.Data;

/**
 * TeamColorDTO
 * @author shiraishitoshio
 *
 */
@Data
public class RealTimeDataDTO {

	/** 国カテゴリ */
    private String dataCategory;

    /** ホーム */
    private String homeTeamName;

    /** アウェー */
    private String awayTeamName;

    /** 3パターン(国名: リーグ名 - ラウンド 数字 / リーグ名 - ラウンド 数字 / 国名: リーグ名)のいずれかに一致した場合の dataCategory 値。一致しない場合は null */
    private String formattedDataCategory;

    /** home/away の組み合わせ単位でのカテゴリ形式判定("同一カテゴリ名" または "混在") */
    private String categoryFormatIcon;

    /** グルーピング件数(検索用途では未設定) */
    private Long cnt;

}
