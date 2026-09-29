package dev.application.analyze.bm_m018;

import java.sql.Timestamp;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import dev.common.entity.BookDataEntity;
import dev.common.util.RecordTimeConverter;

/**
 * BookDataEntity → MatchClassificationResultEntity の変換（MapStruct）。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * BookDataEntity の1行を classify_result_data の1行に変換する。名前が同じ項目は自動でコピーし、
 * 名前が違う項目だけ {@link Mapping} で対応付ける。分類モード・時点・シーズン・seq は Stat / Writer で設定する。
 * </p>
 *
 * <h2>修正内容</h2>
 * <ul>
 *   <li>分類モードを引数で渡す形（mapStruct(book, classificationMode)）をやめ、1引数にした
 *       （分類は試合全体を見て最後に決まるため、Stat で設定する）。</li>
 *   <li>元データの通番は dataSeq に入れる。seq は Writer が seq_counter で採番する。</li>
 *   <li>recordTime を Timestamp に変換（共通クラス {@link RecordTimeConverter}。BM_M005 と同じ）。</li>
 *   <li>unmappedTargetPolicy を IGNORE → WARN: 名前が合わず値が入らない項目があるとビルド時に警告が出る。</li>
 * </ul>
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.WARN)
public interface BookDataToMatchClassificationResultMapper {

	@Mapping(target = "seq", ignore = true)
	@Mapping(target = "season", ignore = true)
	@Mapping(target = "country", ignore = true)
	@Mapping(target = "league", ignore = true)
	@Mapping(target = "classifyMode", ignore = true)
	@Mapping(target = "snapshotType", ignore = true)
	@Mapping(target = "goalNo", ignore = true)
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
	MatchClassificationResultEntity mapStruct(BookDataEntity book);

	/**
	 * 記録時間の文字列を Timestamp に変換する（変換できなければ null）。
	 */
	@Named("stringToTimestamp")
	default Timestamp stringToTimestamp(String value) {
		return RecordTimeConverter.toTimestamp(value);
	}
}
