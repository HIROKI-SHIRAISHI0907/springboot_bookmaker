package dev.common.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

/**
 * チーム本拠地情報マスタを表すEntityです。
 *
 * <p>チームの本拠地都市、ホームスタジアム、住所、緯度経度を保持し、
 * 遠征距離計算や移動負荷分析に利用します。</p>
 */
@Data
public class TeamLocationEntity {

    /**
     * 主キーです。
     */
    private Integer id;

    /**
     * 国コードまたは国名です。
     */
    private String country;

    /**
     * チーム名です。
     */
    private String teamName;

    /**
     * 本拠地都市名です。
     */
    private String homeCity;

    /**
     * ホームスタジアム名です。
     */
    private String stadiumName;

    /**
     * スタジアム住所です。
     */
    private String address;

    /**
     * 緯度です。
     */
    private BigDecimal latitude;

    /**
     * 経度です。
     */
    private BigDecimal longitude;

    /**
     * ジオコーディング取得元です。
     */
    private String geocodeSource;

    /**
     * この所在地情報の有効開始日です。
     */
    private LocalDate validFrom;

    /**
     * この所在地情報の有効終了日です。
     */
    private LocalDate validTo;

}
