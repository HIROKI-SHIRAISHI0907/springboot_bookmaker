package dev.application.domain.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.application.main.service.CsvDetailEntityOutputDTO;
import dev.common.entity.CsvDetailManageEntity;

@Mapper
public interface CsvDetailManageRepository {

	/**
	 * まだ統計に反映していない（check_fin_flg='0'）CSV の csv_id（= 統計 CSV バケットの S3 キー）。登録順。
	 * 2026-11-22 追加: B006 の通常の実行は、CSV 番号の範囲（csv_seq_manage）ではなくこの一覧で読む CSV を決める
	 * （B011 の CSV 名が「国-リーグ-ラウンドN/ホーム-アウェー.csv」になり番号が無いため）。
	 */
	@Select("""
			SELECT csv_id
			FROM csv_detail_manage
			WHERE check_fin_flg = '0'
			  AND COALESCE(csv_id, '') <> ''
			GROUP BY csv_id
			ORDER BY MIN(id)
			""")
	List<String> findUnfinishedCsvIds();

	/**
	 * 同じ試合の行の件数（check_fin_flg を問わない。B018 の対象外判定用。2026-11-25 追加）。
	 * '0' = B011 の CSV が B006 の取り込み待ち、'1' = 反映済み。どちらでも B018 では反映しない。
	 */
	@Select("""
			SELECT COUNT(*)
			FROM csv_detail_manage
			WHERE data_category = #{dataCategory}
			  AND season = #{season}
			  AND home_team_name = #{homeTeamName}
			  AND away_team_name = #{awayTeamName}
			""")
	int existsAnyCount(
			@Param("dataCategory") String dataCategory,
			@Param("season") String season,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName);

	/**
	 * check_fin_flg='1' の既存件数
	 */
	@Select("""
			SELECT COUNT(*)
			FROM csv_detail_manage
			WHERE data_category = #{dataCategory}
			  AND season = #{season}
			  AND home_team_name = #{homeTeamName}
			  AND away_team_name = #{awayTeamName}
			  AND check_fin_flg = '1'
			""")
	int existsCheckedFinCount(
			@Param("dataCategory") String dataCategory,
			@Param("season") String season,
			@Param("homeTeamName") String homeTeamName,
			@Param("awayTeamName") String awayTeamName);

	/**
	 * 各条件に当てはまるcheck_fin_flg='0' のデータ
	 */
	@Select({
		"<script>",
		"SELECT",
		"    csv_id AS csvId,",
		"    data_category AS dataCategory,",
		"    season AS season,",
		"    home_team_name AS homeTeamName,",
		"    away_team_name AS awayTeamName,",
		"    check_fin_flg AS checkFinFlg",
		"FROM csv_detail_manage",
		"WHERE check_fin_flg = '0'",
		"  AND (",
		"  <foreach collection='candidates' item='item' separator=' OR '>",
		"    (",
		"      csv_id = #{item.csvId}",
		"      AND data_category = #{item.dataCategory}",
		"      AND season = #{item.season}",
		"      AND home_team_name = #{item.homeTeamName}",
		"      AND away_team_name = #{item.awayTeamName}",
		"    )",
		"  </foreach>",
		"  )",
		"</script>"
	})
	List<CsvDetailManageEntity> selectCheckedNotFinByExactKeys(
			@Param("candidates") List<CsvDetailEntityOutputDTO> candidates);

	/**
	 * 取り込み待ち（check_fin_flg='0'）の行を csv_id・カテゴリ・チームで探す（シーズンは見ない。2026-11-25 追加）。
	 * B011 は試合日でシーズンを決めるので、B006 側でシーズンを決め直すと食い違って取り込めなくなるため。
	 * 返す行のシーズンは csv_detail_manage の値（B011 が決めたもの）。
	 */
	@Select({
		"<script>",
		"SELECT",
		"    csv_id AS csvId,",
		"    data_category AS dataCategory,",
		"    season AS season,",
		"    home_team_name AS homeTeamName,",
		"    away_team_name AS awayTeamName,",
		"    check_fin_flg AS checkFinFlg",
		"FROM csv_detail_manage",
		"WHERE check_fin_flg = '0'",
		"  AND (",
		"  <foreach collection='candidates' item='item' separator=' OR '>",
		"    (",
		"      csv_id = #{item.csvId}",
		"      AND data_category = #{item.dataCategory}",
		"      AND home_team_name = #{item.homeTeamName}",
		"      AND away_team_name = #{item.awayTeamName}",
		"    )",
		"  </foreach>",
		"  )",
		"</script>"
	})
	List<CsvDetailManageEntity> selectNotFinByCsvKeys(
			@Param("candidates") List<CsvDetailEntityOutputDTO> candidates);

	/**
	 * 各条件に当てはまるのデータ
	 */
	@Select({
		"<script>",
		"SELECT",
		"    csv_id AS csvId,",
		"    data_category AS dataCategory,",
		"    season AS season,",
		"    home_team_name AS homeTeamName,",
		"    away_team_name AS awayTeamName,",
		"    check_fin_flg AS checkFinFlg",
		"FROM csv_detail_manage",
		"WHERE (",
		"  <foreach collection='candidates' item='item' separator=' OR '>",
		"    (",
		"      csv_id = #{item.csvId}",
		"      AND data_category = #{item.dataCategory}",
		"      AND season = #{item.season}",
		"      AND home_team_name = #{item.homeTeamName}",
		"      AND away_team_name = #{item.awayTeamName}",
		"    )",
		"  </foreach>",
		")",
		"</script>"
	})
	List<CsvDetailManageEntity> selectByExactKeys(
			@Param("candidates") List<CsvDetailEntityOutputDTO> candidates);

	/**
	 * manual用INSERT
	 * UNIQUE(data_category, season, home_team_name, away_team_name) 前提
	 */
	@Insert("""
			INSERT INTO csv_detail_manage (
			    csv_id,
			    data_category,
			    season,
			    home_team_name,
			    away_team_name,
			    check_fin_flg,
			    register_id,
			    register_time,
			    update_id,
			    update_time
			) VALUES (
			    #{csvId},
			    #{dataCategory},
			    #{season},
			    #{homeTeamName},
			    #{awayTeamName},
			    #{checkFinFlg},
			    'ADMIN',
			    CURRENT_TIMESTAMP,
			    'ADMIN',
			    CURRENT_TIMESTAMP
			)
			ON CONFLICT (data_category, season, home_team_name, away_team_name)
			DO UPDATE SET
			    csv_id = EXCLUDED.csv_id,
			    check_fin_flg = EXCLUDED.check_fin_flg,
			    update_id = 'ADMIN',
			    update_time = CURRENT_TIMESTAMP
			""")
	int insert(CsvDetailManageEntity entity);

	/**
	 * manual用UPDATE
	 */
	@Update("""
			UPDATE csv_detail_manage SET
				check_fin_flg = '1',
				update_id = 'ADMIN',
			    update_time = CURRENT_TIMESTAMP
			WHERE
			    csv_id = #{csvId} AND
			    data_category = #{dataCategory} AND
			    season = #{season} AND
			    home_team_name = #{homeTeamName} AND
			    away_team_name = #{awayTeamName};
			""")
	int update(CsvDetailManageEntity entity);
}
