package dev.application.domain.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import dev.application.analyze.bm_m024.CalcCorrelationMatchStatsEntity;

/**
 * calc_correlation_match_stats Mapper（BM_M024 相関分析の明細）。
 *
 * <h2>何をするクラスか</h2>
 * <ul>
 *   <li>{@link #findSeqByMatchKey}: 1試合分の既存行の（区分, 特徴量, seq）を取得する（再処理で番号を消費しないため）。</li>
 *   <li>{@link #upsertBatch}: 複数行をまとめて UPSERT する（1試合 約110行を数回の SQL で登録）。</li>
 *   <li>{@link #deleteBySeqs}: 今回の計算に無い既存行を削除する。</li>
 * </ul>
 * <p>
 * 旧 CalcCorrelationRepository を置き換える。相関係数の読み取りはビュー（calc_correlation_stats /
 * each_team_calc_correlation_stats / card_calc_correlation_stats / *_trend）から行うこと。
 * </p>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>PostgreSQL 専用構文</b>（ON CONFLICT）。</li>
 *   <li><b>1回の upsertBatch の行数</b>: パラメータ数（1行あたり約30個）が PostgreSQL の上限（65535）を超えないよう、
 *       呼び出し側で 500 行以下に分けること（Writer は 200 行ずつ）。</li>
 *   <li><b>同一文内で同じキーが2回あるとエラー</b>（ON CONFLICT は同じ行を2回更新できない）。Writer で重複を除いている。</li>
 * </ul>
 */
@Mapper
public interface CalcCorrelationMatchStatsRepository {

	/**
	 * 1試合分の既存行の（区分, 特徴量, seq）を取得する。
	 */
	@Select({
			"SELECT chk_body, feature, seq FROM calc_correlation_match_stats ",
			"WHERE season = #{season} AND country = #{country} AND league = #{league} AND round_no = #{roundNo} ",
			"AND home_team_name = #{homeTeamName} AND away_team_name = #{awayTeamName}"
	})
	@Results(id = "ccmsSeq", value = {
			@Result(column = "chk_body", property = "chkBody"),
			@Result(column = "feature", property = "feature"),
			@Result(column = "seq", property = "seq")
	})
	List<CalcCorrelationMatchStatsEntity> findSeqByMatchKey(
			@Param("season") String season,
			@Param("country") String country,
			@Param("league") String league,
			@Param("roundNo") Integer roundNo,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName);

	/**
	 * seq を指定して削除する。
	 *
	 * @return 削除件数
	 */
	@Delete({
			"<script>",
			"DELETE FROM calc_correlation_match_stats WHERE seq IN ",
			"<foreach collection='seqs' item='s' open='(' separator=',' close=')'>#{s}</foreach>",
			"</script>"
	})
	int deleteBySeqs(@Param("seqs") List<String> seqs);

	/**
	 * 複数行をまとめて UPSERT する。
	 *
	 * @param rows 登録対象（seq・season 設定済み。同じキーの行を含めないこと）
	 * @return 処理件数（INSERT・UPDATE とも1行1件）
	 */
	@Insert({
			"<script>",
			"INSERT INTO calc_correlation_match_stats (",
			"seq, season, country, league, home_team_name, away_team_name, ",
			"match_id, round_no, record_time, situation, chk_body, feature, ",
			"feature_order, home_n, home_sx, home_sy, home_sxx, home_syy, ",
			"home_sxy, away_n, away_sx, away_sy, away_sxx, away_syy, ",
			"away_sxy, ",
			"register_id, register_time, update_id, update_time",
			") VALUES ",
			"<foreach collection='rows' item='r' separator=','>",
			"(",
			"#{r.seq}, #{r.season}, #{r.country}, #{r.league}, #{r.homeTeamName}, #{r.awayTeamName}, ",
			"#{r.matchId}, #{r.roundNo}, #{r.recordTime}, #{r.situation}, #{r.chkBody}, #{r.feature}, ",
			"#{r.featureOrder}, #{r.homeN}, #{r.homeSx}, #{r.homeSy}, #{r.homeSxx}, #{r.homeSyy}, ",
			"#{r.homeSxy}, #{r.awayN}, #{r.awaySx}, #{r.awaySy}, #{r.awaySxx}, #{r.awaySyy}, ",
			"#{r.awaySxy}, ",
			"COALESCE(#{r.registerId}, 'SYSTEM'), COALESCE(CAST(#{r.registerTime} AS timestamptz), NOW()), ",
			"COALESCE(#{r.updateId}, 'SYSTEM'), COALESCE(CAST(#{r.updateTime} AS timestamptz), NOW())",
			")",
			"</foreach>",
			" ON CONFLICT (season, country, league, round_no, home_team_name, away_team_name, chk_body, feature) DO UPDATE SET ",
			// seq・一意キー・register_* は変えない
			"match_id = EXCLUDED.match_id, ",
			"round_no = EXCLUDED.round_no, ",
			"record_time = EXCLUDED.record_time, ",
			"situation = EXCLUDED.situation, ",
			"feature_order = EXCLUDED.feature_order, ",
			"home_n = EXCLUDED.home_n, ",
			"home_sx = EXCLUDED.home_sx, ",
			"home_sy = EXCLUDED.home_sy, ",
			"home_sxx = EXCLUDED.home_sxx, ",
			"home_syy = EXCLUDED.home_syy, ",
			"home_sxy = EXCLUDED.home_sxy, ",
			"away_n = EXCLUDED.away_n, ",
			"away_sx = EXCLUDED.away_sx, ",
			"away_sy = EXCLUDED.away_sy, ",
			"away_sxx = EXCLUDED.away_sxx, ",
			"away_syy = EXCLUDED.away_syy, ",
			"away_sxy = EXCLUDED.away_sxy, ",
			"update_id = EXCLUDED.update_id, update_time = EXCLUDED.update_time",
			"</script>"
	})
	int upsertBatch(@Param("rows") List<CalcCorrelationMatchStatsEntity> rows);
}
