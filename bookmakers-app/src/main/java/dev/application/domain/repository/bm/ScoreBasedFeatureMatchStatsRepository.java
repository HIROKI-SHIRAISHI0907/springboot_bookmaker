package dev.application.domain.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import dev.application.analyze.bm_m023.ScoreBasedFeatureMatchStatsEntity;

/**
 * score_based_feature_match_stats Mapper（BM_M023 / BM_M026 共通の明細）。
 *
 * <h2>何をするクラスか</h2>
 * <ul>
 *   <li>{@link #findSeqByMatchKey}: 1試合分の既存行の（区分, 特徴量, seq）を取得する（再処理で番号を消費しないため）。</li>
 *   <li>{@link #upsertBatch}: 複数行をまとめて UPSERT する（1試合 約200行を数回の SQL で登録）。</li>
 *   <li>{@link #deleteBySeqs}: 今回の計算に無い既存行を削除する。</li>
 * </ul>
 * <p>
 * 旧 ScoreBasedFeatureStatsRepository / EachTeamScoreBasedFeatureStatsRepository を置き換える。
 * 統計値の読み取りはビュー（score_based_feature_stats / each_team_score_based_feature_stats /
 * card_score_based_feature_stats）から行うこと。
 * </p>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>PostgreSQL 専用構文</b>（ON CONFLICT）。</li>
 *   <li><b>1回の upsertBatch の行数</b>: パラメータ数（1行あたり約40個）が PostgreSQL の上限（65535）を超えないよう、
 *       呼び出し側で 500 行以下に分けること（Writer は 200 行ずつ）。</li>
 *   <li><b>同一文内で同じキーが2回あるとエラー</b>（ON CONFLICT は同じ行を2回更新できない）。Writer で重複を除いている。</li>
 * </ul>
 */
@Mapper
public interface ScoreBasedFeatureMatchStatsRepository {

	/**
	 * 1試合分の既存行の（区分, 特徴量, seq）を取得する。
	 */
	@Select({
			"SELECT chk_body, feature, seq FROM score_based_feature_match_stats ",
			"WHERE season = #{season} AND country = #{country} AND league = #{league} AND round_no = #{roundNo} ",
			"AND home_team_name = #{homeTeamName} AND away_team_name = #{awayTeamName}"
	})
	@Results(id = "sbfmsSeq", value = {
			@Result(column = "chk_body", property = "chkBody"),
			@Result(column = "feature", property = "feature"),
			@Result(column = "seq", property = "seq")
	})
	List<ScoreBasedFeatureMatchStatsEntity> findSeqByMatchKey(
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
			"DELETE FROM score_based_feature_match_stats WHERE seq IN ",
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
			"INSERT INTO score_based_feature_match_stats (",
			"seq, season, country, league, home_team_name, away_team_name, ",
			"match_id, round_no, record_time, situation, chk_body, feature, ",
			"feature_order, home_n, home_s1, home_s2, home_s3, home_s4, ",
			"home_min, home_max, home_tn, home_ts1, home_ts2, home_tmin, ",
			"home_tmax, away_n, away_s1, away_s2, away_s3, away_s4, ",
			"away_min, away_max, away_tn, away_ts1, away_ts2, away_tmin, ",
			"away_tmax, ",
			"register_id, register_time, update_id, update_time",
			") VALUES ",
			"<foreach collection='rows' item='r' separator=','>",
			"(",
			"#{r.seq}, #{r.season}, #{r.country}, #{r.league}, #{r.homeTeamName}, #{r.awayTeamName}, ",
			"#{r.matchId}, #{r.roundNo}, #{r.recordTime}, #{r.situation}, #{r.chkBody}, #{r.feature}, ",
			"#{r.featureOrder}, #{r.homeN}, #{r.homeS1}, #{r.homeS2}, #{r.homeS3}, #{r.homeS4}, ",
			"#{r.homeMin}, #{r.homeMax}, #{r.homeTn}, #{r.homeTs1}, #{r.homeTs2}, #{r.homeTmin}, ",
			"#{r.homeTmax}, #{r.awayN}, #{r.awayS1}, #{r.awayS2}, #{r.awayS3}, #{r.awayS4}, ",
			"#{r.awayMin}, #{r.awayMax}, #{r.awayTn}, #{r.awayTs1}, #{r.awayTs2}, #{r.awayTmin}, ",
			"#{r.awayTmax}, ",
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
			"home_s1 = EXCLUDED.home_s1, ",
			"home_s2 = EXCLUDED.home_s2, ",
			"home_s3 = EXCLUDED.home_s3, ",
			"home_s4 = EXCLUDED.home_s4, ",
			"home_min = EXCLUDED.home_min, ",
			"home_max = EXCLUDED.home_max, ",
			"home_tn = EXCLUDED.home_tn, ",
			"home_ts1 = EXCLUDED.home_ts1, ",
			"home_ts2 = EXCLUDED.home_ts2, ",
			"home_tmin = EXCLUDED.home_tmin, ",
			"home_tmax = EXCLUDED.home_tmax, ",
			"away_n = EXCLUDED.away_n, ",
			"away_s1 = EXCLUDED.away_s1, ",
			"away_s2 = EXCLUDED.away_s2, ",
			"away_s3 = EXCLUDED.away_s3, ",
			"away_s4 = EXCLUDED.away_s4, ",
			"away_min = EXCLUDED.away_min, ",
			"away_max = EXCLUDED.away_max, ",
			"away_tn = EXCLUDED.away_tn, ",
			"away_ts1 = EXCLUDED.away_ts1, ",
			"away_ts2 = EXCLUDED.away_ts2, ",
			"away_tmin = EXCLUDED.away_tmin, ",
			"away_tmax = EXCLUDED.away_tmax, ",
			"update_id = EXCLUDED.update_id, update_time = EXCLUDED.update_time",
			"</script>"
	})
	int upsertBatch(@Param("rows") List<ScoreBasedFeatureMatchStatsEntity> rows);
}
