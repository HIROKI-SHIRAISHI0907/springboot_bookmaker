package dev.application.analyze.common.util;

/**
 * 試合時間帯区分を表す列挙型です。
 */
public enum MatchTimeBandType {

    /** 前半 0-15分 */
    MIN_0_15,

    /** 前半 16-30分 */
    MIN_16_30,

    /** 前半 31-45+分 */
    MIN_31_45_PLUS,

    /** 後半 46-60分 */
    MIN_46_60,

    /** 後半 61-75分 */
    MIN_61_75,

    /** 後半 76-90+分 */
    MIN_76_90_PLUS

}
