package dev.application.domain.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.application.analyze.common.error.AnalyzeErrorMatchEntity;

/**
 * analyze_error_match Mapper（統計処理で登録できなかった試合の記録）。
 *
 * <h2>何をするクラスか</h2>
 * <ul>
 *   <li>{@link #findSeq}: 同じエラーの既存行の seq を取得する（繰り返し起きても番号を消費しないため）。</li>
 *   <li>{@link #upsert}: 記録する。同じ (bm_number, error_type, country, league, data_category, home_team_name, away_team_name)
 *       が既にあれば、発生回数を +1、最後の発生日時・エラー内容を更新し、未対応に戻す。</li>
 *   <li>{@link #findUnresolvedKeys}: BM ごとの未対応の試合キー（国・リーグ・ホーム・アウェー）を取得する（自動解決の判定用）。</li>
 *   <li>{@link #resolveByMatch}: その試合が正常に登録できたとき、未対応のエラーを自動で対応済みにする。</li>
 * </ul>
 * <p>画面用の読み取り（一覧・手動の対応済み更新）は画面を作るときに追加する。</p>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>PostgreSQL 専用構文</b>（ON CONFLICT）。</li>
 *   <li><b>キー項目に NULL を渡さないこと</b>（一意制約は NULL 同士を別物と見なすため、行が増え続ける）。
 *       {@link dev.application.analyze.common.service.AnalyzeErrorWriter} で空文字にしている。</li>
 * </ul>
 */
@Mapper
public interface AnalyzeErrorMatchRepository {

	/**
	 * 同じエラーの既存行の seq（なければ null）。
	 */
	@Select({
			"SELECT seq FROM analyze_error_match ",
			"WHERE bm_number = #{bmNumber} AND error_type = #{errorType} ",
			"AND country = #{country} AND league = #{league} AND data_category = #{dataCategory} ",
			"AND home_team_name = #{homeTeamName} AND away_team_name = #{awayTeamName}"
	})
	String findSeq(
			@Param("bmNumber") String bmNumber,
			@Param("errorType") String errorType,
			@Param("country") String country,
			@Param("league") String league,
			@Param("dataCategory") String dataCategory,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName);

	/**
	 * 記録する（既にあれば発生回数を +1 して更新、未対応に戻す）。
	 *
	 * @param entity 記録内容（seq 設定済み。キー項目は空文字可・NULL 不可）
	 * @return 処理件数（INSERT・UPDATE とも 1）
	 */
	@Insert({
			"INSERT INTO analyze_error_match AS t (",
			"seq, bm_number, error_type, error_message, country, league, data_category, ",
			"home_team_name, away_team_name, match_id, season, detail, exception_class, stack_trace, ",
			"occurred_count, first_occurred_at, last_occurred_at, resolved_flg, ",
			"register_id, register_time, update_id, update_time",
			") VALUES (",
			"#{seq}, #{bmNumber}, #{errorType}, #{errorMessage}, #{country}, #{league}, #{dataCategory}, ",
			"#{homeTeamName}, #{awayTeamName}, #{matchId}, #{season}, #{detail}, #{exceptionClass}, #{stackTrace}, ",
			"1, NOW(), NOW(), FALSE, ",
			"COALESCE(#{registerId}, 'SYSTEM'), NOW(), COALESCE(#{updateId}, 'SYSTEM'), NOW()",
			") ON CONFLICT (bm_number, error_type, country, league, data_category, home_team_name, away_team_name) ",
			"DO UPDATE SET ",
			// seq・キー・first_occurred_at・register_*・resolved_at/by・note は変えない
			"error_message = EXCLUDED.error_message, ",
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
	 * BM の未対応エラーの試合キー（国・リーグ・ホーム・アウェー）。
	 *
	 * @param bmNumber BM 番号
	 * @return キーの一覧（重複なし）
	 */
	@Select({
			"SELECT DISTINCT country, league, home_team_name, away_team_name FROM analyze_error_match ",
			"WHERE bm_number = #{bmNumber} AND resolved_flg = FALSE"
	})
	@Results(id = "aemKey", value = {
			@Result(column = "country", property = "country"),
			@Result(column = "league", property = "league"),
			@Result(column = "home_team_name", property = "homeTeamName"),
			@Result(column = "away_team_name", property = "awayTeamName")
	})
	List<AnalyzeErrorMatchEntity> findUnresolvedKeys(@Param("bmNumber") String bmNumber);

	/**
	 * 正常に登録できた試合の未対応エラーを、自動で対応済みにする（エラー種別・キーの表記は問わない）。
	 * resolved_by = 'AUTO'。行は消さない（履歴として残す。再発したら upsert で未対応に戻る）。
	 *
	 * @return 更新件数（未対応エラーが無ければ 0）
	 */
	@Update({
			"UPDATE analyze_error_match SET resolved_flg = TRUE, resolved_at = NOW(), resolved_by = 'AUTO', ",
			"update_id = 'SYSTEM', update_time = NOW() ",
			"WHERE bm_number = #{bmNumber} AND country = #{country} AND league = #{league} ",
			"AND home_team_name = #{homeTeamName} AND away_team_name = #{awayTeamName} AND resolved_flg = FALSE"
	})
	int resolveByMatch(
			@Param("bmNumber") String bmNumber,
			@Param("country") String country,
			@Param("league") String league,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName);
}