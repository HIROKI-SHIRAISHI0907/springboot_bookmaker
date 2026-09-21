package dev.common.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * record_time（static_data.record_time 等）用の日時変換Utilクラス。
 *
 * <p>
 * record_time は timestamp without time zone のカラムで、値は
 * future_master.future_time と同様にJST(Asia/Tokyo)の壁時計としてそのまま
 * 記録されている(UTCではない)。この前提を毎回書くと、
 * 「rs.getTimestamp().toInstant().atOffset(ZoneOffset.UTC)」のような
 * JVMのデフォルトタイムゾーンに依存した誤変換(JSTの数字をUTCの数字として
 * 誤って扱ってしまうバグ)を繰り返し埋め込みやすいため、
 * record_timeを読む/比較する/APIへ出す処理はすべてこのクラス経由に統一する。
 * </p>
 *
 * @author shiraishitoshio
 */
public final class RecordTimeConvertUtil {

    private RecordTimeConvertUtil() {
    }

    /**
     * ResultSet から record_time（またはそれに準ずるJST壁時計カラム）を、
     * 明示的にJSTのOffsetDateTimeとして取得する。
     *
     * @param rs ResultSet
     * @param columnLabel カラム名(またはエイリアス)
     * @return JSTのOffsetDateTime。値がNULLなら null
     */
    public static OffsetDateTime readAsJst(ResultSet rs, String columnLabel) throws SQLException {
        return DateOffsetDecisionUtil.getOffsetDateTime(rs, columnLabel);
    }

    /**
     * JSTのOffsetDateTimeを、API出力用のUTC("Z")表記のISO文字列に変換する。
     *
     * <p>
     * フロント側が「APIから受け取る日時文字列はUTCである」という前提で
     * +9時間してJST表示に変換する実装になっているため、record_timeの値を
     * そのままJSTオフセット付きで返すのではなく、必ずこのメソッドで
     * UTC表記に変換してから返す。値そのもの(指している瞬間)は変わらないので、
     * フロント側の変換を経て画面には元のJSTの数字がそのまま表示される。
     * </p>
     *
     * @param jstTime readAsJst 等で取得したJSTのOffsetDateTime
     * @return UTC("Z")表記のISO文字列。jstTimeがnullならnull
     */
    public static String toApiUtcString(OffsetDateTime jstTime) {
        return jstTime == null
                ? null
                : jstTime.withOffsetSameInstant(ZoneOffset.UTC).toString();
    }

    /**
     * ResultSet から record_time を読み取り、そのままAPI出力用の
     * UTC("Z")表記のISO文字列に変換する（readAsJst + toApiUtcString のショートカット）。
     *
     * @param rs ResultSet
     * @param columnLabel カラム名(またはエイリアス)
     * @return UTC("Z")表記のISO文字列。値がNULLならnull
     */
    public static String readAsApiUtcString(ResultSet rs, String columnLabel) throws SQLException {
        return toApiUtcString(readAsJst(rs, columnLabel));
    }

    /**
     * 「JSTの現在時刻からwithinを引いた時刻」を、record_timeと直接比較できる
     * timestamp without time zone 用のnaive Timestampとして返す。
     *
     * <p>
     * record_timeはJST壁時計のnaive値なので、UTC基準のInstant.now()から
     * しきい値を作ってしまうと、常にrecord_time(JST)の方が数字上大きくなり、
     * 鮮度切れを検知できなくなる。必ずこのメソッド経由でしきい値を作ること。
     * </p>
     *
     * @param within 現在時刻からどれだけ遡るか
     * @return record_timeと直接比較可能な、JST壁時計のnaive Timestamp
     */
    public static Timestamp freshnessThreshold(Duration within) {
        LocalDateTime thresholdJst = LocalDateTime.now(DateOffsetDecisionUtil.getZoneId()).minus(within);
        return Timestamp.valueOf(thresholdJst);
    }
}