package dev.batch.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.common.error.AnalyzeErrorMatchEntity;

/**
 * analyze_error_match Mapper（batch 用。CSV 作成 B011 などで作れなかった試合の記録）。
 *
 * <h2>何をするクラスか</h2>
 * <ul>
 *   <li>{@link #findSeq} / {@link #upsert}: application 側の AnalyzeErrorMatchRepository と同じ SQL
 *       （同じエラーは1行にまとめ、発生回数 +1・未対応に戻す）。</li>
 *   <li>{@link #findUnresolvedTeamKeys}: BM ごとの未対応エラーのホーム・アウェー（自動解決の判定用）。</li>
 *   <li>{@link #resolveByTeams}: その試合の CSV が作れたとき、未対応のエラーを自動で対応済みにする。
 *       CSV は試合単位なので、国・リーグは見ずにホーム・アウェー（NFKC 正規化）で突き合わせる
 *       （キーが読めず国・リーグ空で記録したエラーも、カテゴリが直って CSV が作れたら解決できるように）。</li>
 * </ul>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>PostgreSQL 専用構文</b>（ON CONFLICT・normalize）。</li>
 *   <li><b>キー項目に NULL を渡さないこと</b>（AnalyzeErrorWriter で空文字にしている）。</li>
 *   <li>bm DB の Mapper として読み込まれる前提（dev.batch.repository.bm 配下）。</li>
 * </ul>
 */
@Mapper
public interface AnalyzeErrorMatchBatchRepository {

	/**
	 * 同じエラーの既存行の seq（なければ null）。
	 */
	@Select({
			"SELECT seq FROM analyze_error_match ",
			"WHERE bm_number = #{bmNumber} AND error_type = #{errorType} ",
			"AND country = #{country} AND league = #{league} AND data_category = #{dataCategory} ",
			"AND home_team_name = #{homeTeamName} AND away_team_name = #{awayTeamName} AND error_field = #{errorField}"
	})
	String findSeq(
			@Param("bmNumber") String bmNumber,
			@Param("errorType") String errorType,
			@Param("country") String country,
			@Param("league") String league,
			@Param("dataCategory") String dataCategory,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName,
			@Param("errorField") String errorField);

	/**
	 * 記録する（既にあれば発生回数を +1 して更新、未対応に戻す）。
	 *
	 * @param entity 記録内容（seq 設定済み。キー項目は空文字可・NULL 不可）
	 * @return 処理件数（INSERT・UPDATE とも 1）
	 */
	@Insert({
			"INSERT INTO analyze_error_match AS t (",
			"seq, bm_number, error_type, error_message, country, league, data_category, ",
			"home_team_name, away_team_name, error_field, error_value, match_id, season, detail, exception_class, stack_trace, ",
			"occurred_count, first_occurred_at, last_occurred_at, resolved_flg, ",
			"register_id, register_time, update_id, update_time",
			") VALUES (",
			"#{seq}, #{bmNumber}, #{errorType}, #{errorMessage}, #{country}, #{league}, #{dataCategory}, ",
			"#{homeTeamName}, #{awayTeamName}, #{errorField}, #{errorValue}, #{matchId}, #{season}, #{detail}, #{exceptionClass}, #{stackTrace}, ",
			"1, NOW(), NOW(), FALSE, ",
			"COALESCE(#{registerId}, 'SYSTEM'), NOW(), COALESCE(#{updateId}, 'SYSTEM'), NOW()",
			") ON CONFLICT (bm_number, error_type, country, league, data_category, home_team_name, away_team_name, error_field) ",
			"DO UPDATE SET ",
			"error_message = EXCLUDED.error_message, error_value = EXCLUDED.error_value, ",
			"match_id = COALESCE(EXCLUDED.match_id, t.match_id), ",
			"season = COALESCE(EXCLUDED.season, t.season), ",
			"detail = COALESCE(EXCLUDED.detail, t.detail), ",
			"exception_class = EXCLUDED.exception_class, stack_trace = EXCLUDED.stack_trace, ",
			"occurred_count = t.occurred_count + 1, last_occurred_at = EXCLUDED.last_occurred_at, ",
			"resolved_flg = FALSE, ",
			"update_id = EXCLUDED.update_id, update_time = EXCLUDED.update_time"
	})
	int upsert(AnalyzeErrorMatchEntity entity);

	/**
	 * BM の未対応エラーのホーム・アウェー（重複なし）。
	 *
	 * @param bmNumber BM 番号
	 * @return ホーム・アウェーの一覧
	 */
	@Select({
			"SELECT DISTINCT home_team_name, away_team_name FROM analyze_error_match ",
			"WHERE bm_number = #{bmNumber} AND resolved_flg = FALSE"
	})
	@Results(id = "aemBatchTeamKey", value = {
			@Result(column = "home_team_name", property = "homeTeamName"),
			@Result(column = "away_team_name", property = "awayTeamName")
	})
	List<AnalyzeErrorMatchEntity> findUnresolvedTeamKeys(@Param("bmNumber") String bmNumber);

	/**
	 * その試合（ホーム・アウェー。NFKC 正規化して比較）の未対応エラーを自動で対応済みにする。
	 * resolved_by = 'AUTO'。行は消さない（再発したら upsert で未対応に戻る）。
	 *
	 * @return 更新件数
	 */
	@Update({
			"UPDATE analyze_error_match SET resolved_flg = TRUE, resolved_at = NOW(), resolved_by = 'AUTO', ",
			"update_id = 'SYSTEM', update_time = NOW() ",
			"WHERE bm_number = #{bmNumber} AND resolved_flg = FALSE ",
			"AND normalize(BTRIM(home_team_name), NFKC) = normalize(BTRIM(#{homeTeamName}), NFKC) ",
			"AND normalize(BTRIM(away_team_name), NFKC) = normalize(BTRIM(#{awayTeamName}), NFKC)"
	})
	int resolveByTeams(
			@Param("bmNumber") String bmNumber,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName);
}
