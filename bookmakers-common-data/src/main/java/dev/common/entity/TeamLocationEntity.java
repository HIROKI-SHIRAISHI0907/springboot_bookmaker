package dev.common.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

/**
 * チーム本拠地情報マスタを表すEntityです。
 *
 * <p>
 * チームの本拠地都市、ホームスタジアム、住所、緯度経度を保持し、
 * 遠征距離計算や移動負荷分析に利用します。
 * </p>
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
     * 国名の翻訳値です。
     */
    private String countryTranslate;

    /**
     * チーム名です。
     */
    private String teamName;

    /**
     * チーム名の翻訳値です。
     */
    private String teamNameTranslate;

    /**
     * 本拠地都市名です。
     */
    private String homeCity;

    /**
     * 本拠地都市名（翻訳）です。
     */
    private String homeCityTranslate;

    /**
     * ホームスタジアム名です。
     */
    private String stadiumName;

    /**
     * ホームスタジアム名（翻訳）です。
     */
    private String stadiumNameTranslate;

    /**
     * 既存互換用の住所です。
     * 基本的には英語版住所を格納します。
     */
    private String address;

    /**
     * 既存互換用の緯度です。
     * 基本的には英語版緯度を格納します。
     */
    private BigDecimal latitude;

    /**
     * 既存互換用の経度です。
     * 基本的には英語版経度を格納します。
     */
    private BigDecimal longitude;

    /**
     * Google Places の place ID です。
     */
    private String placeId;

    /**
     * 英語版の表示名です。
     */
    private String displayNameEn;

    /**
     * 英語版の住所です。
     */
    private String addressEn;

    /**
     * 英語版の緯度です。
     */
    private BigDecimal latitudeEn;

    /**
     * 英語版の経度です。
     */
    private BigDecimal longitudeEn;

    /**
     * 現地語版の表示名です。
     */
    private String displayNameLocal;

    /**
     * 現地語版の住所です。
     */
    private String addressLocal;

    /**
     * 現地語版の緯度です。
     */
    private BigDecimal latitudeLocal;

    /**
     * 現地語版の経度です。
     */
    private BigDecimal longitudeLocal;

    /**
     * 現地語版の languageCode です。
     */
    private String localLanguageCode;

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
