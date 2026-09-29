package dev.application.analyze.bm_m005;

import java.sql.Timestamp;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import dev.common.entity.BookDataEntity;
import dev.common.util.RecordTimeConverter;

/**
 * BookDataEntity → NoGoalMatchStatisticsEntity の変換（MapStruct）。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * BookDataEntity の1行（ある時点のスナップショット）を、no_goal_match_stats の1行に変換する。
 * 名前が同じ項目は自動でコピーし、名前が違う項目だけ {@link Mapping} で対応付ける。
 * </p>
 *
 * <h2>修正内容</h2>
 * <ul>
 *   <li><b>seq をコピーしない</b>: 元データの通番は dataSeq に入れる。seq は Writer が seq_counter で採番する。</li>
 *   <li><b>season / snapshotType / メタデータは Mapper では設定しない</b>（明示的に ignore）。</li>
 *   <li><b>unmappedTargetPolicy を IGNORE → WARN</b>: 名前が合わず値が入らない項目があると、ビルド時に警告が出る。
 *       （以前は黙って null になっていた。ビルドの警告に出た項目は BookDataEntity 側の名前を確認し、@Mapping を追加すること）</li>
 *   <li><b>記録時間の変換を修正</b>:
 *     <ul>
 *       <li>10桁の数字（秒単位の UNIX 時刻）をミリ秒として扱い、1970年になっていた → 10桁は秒、13桁はミリ秒として扱う。</li>
 *       <li>PostgreSQL の timestamptz の文字列（"2025-01-01 12:00:00+09"）や小数秒（最大9桁）に対応。</li>
 *       <li>変換処理は共通クラス RecordTimeConverter に移した（BM_M019 と共通）。</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>オフセットなしの日時は JVM のタイムゾーンで解釈する</b>（サーバの TZ が変わると時刻がずれる）。</li>
 *   <li><b>変換できない記録時間は null</b>（record_time は NULL 許可）。例外にはしない。</li>
 * </ul>
 */
@Mapper(
		componentModel = "spring",
		unmappedTargetPolicy = ReportingPolicy.WARN)
public interface BookDataToNoGoalMatchMapper {

	@Mapping(target = "seq", ignore = true)
	@Mapping(target = "season", ignore = true)
	@Mapping(target = "snapshotType", ignore = true)
	@Mapping(target = "registerId", ignore = true)
	@Mapping(target = "registerTime", ignore = true)
	@Mapping(target = "updateId", ignore = true)
	@Mapping(target = "updateTime", ignore = true)
	@Mapping(source = "seq", target = "dataSeq")
	@Mapping(source = "recordTime", target = "recordTime", qualifiedByName = "stringToTimestamp")
	@Mapping(source = "gameTeamCategory", target = "dataCategory")
	@Mapping(source = "time", target = "times")
	@Mapping(source = "homeBallPossesion", target = "homeDonation")
	@Mapping(source = "awayBallPossesion", target = "awayDonation")
	@Mapping(source = "homeShootBlocked", target = "homeBlockShoot")
	@Mapping(source = "awayShootBlocked", target = "awayBlockShoot")
	@Mapping(source = "homeCornerKick", target = "homeCorner")
	@Mapping(source = "awayCornerKick", target = "awayCorner")
	@Mapping(source = "homeOffSide", target = "homeOffside")
	@Mapping(source = "awayOffSide", target = "awayOffside")
	NoGoalMatchStatisticsEntity mapStruct(BookDataEntity book);

	/**
	 * 記録時間の文字列を Timestamp に変換する（変換できなければ null）。
	 * 変換本体は共通クラス {@link RecordTimeConverter}（BM_M019 と共通）。
	 */
	@Named("stringToTimestamp")
	default Timestamp stringToTimestamp(String value) {
		return RecordTimeConverter.toTimestamp(value);
	}
}
