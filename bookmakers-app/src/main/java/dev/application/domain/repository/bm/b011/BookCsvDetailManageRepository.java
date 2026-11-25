package dev.application.domain.repository.bm.b011;

// 2026-11-20 bookmakers-batch（dev.batch.repository.bm.BookCsvDetailManageRepository）から移動（B011 を統計用の bookmakers-app で動かすため）

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.common.entity.CsvDetailManageEntity;

@Mapper
public interface BookCsvDetailManageRepository {

	@Select("""
			SELECT
				csv_id         AS csvId,
				data_category  AS dataCategory,
				season         AS season,
				home_team_name AS homeTeamName,
				away_team_name AS awayTeamName,
				check_fin_flg  AS checkFinFlg
			FROM
				csv_detail_manage
			WHERE
				data_category = #{dataCategory}
			AND season = #{season}
			AND home_team_name = #{homeTeamName}
			AND away_team_name = #{awayTeamName}
			""")
	CsvDetailManageEntity select(CsvDetailManageEntity entity);

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
			""")
	int insert(CsvDetailManageEntity entity);

	@Update("""
			UPDATE csv_detail_manage
			SET
			    csv_id = #{csvId},
			    check_fin_flg = #{checkFinFlg},
			    update_time = CURRENT_TIMESTAMP,
			    update_id = 'ADMIN'
			WHERE
				data_category = #{dataCategory}
			AND season = #{season}
			AND home_team_name = #{homeTeamName}
			AND away_team_name = #{awayTeamName}
			""")
	int update(CsvDetailManageEntity entity);

	/**
	 * 全行の csv_id・season・カテゴリ・チーム（B011 の最後の突き合わせ用。2026-10-21 追加）。
	 * season が空の行は、後からシーズンを補う対象になる。
	 */
	@Select("""
			SELECT
				csv_id         AS csvId,
				data_category  AS dataCategory,
				season         AS season,
				home_team_name AS homeTeamName,
				away_team_name AS awayTeamName,
				check_fin_flg  AS checkFinFlg
			FROM
				csv_detail_manage
			WHERE
				csv_id IS NOT NULL
			AND BTRIM(csv_id) <> ''
			""")
	List<CsvDetailManageEntity> findAllForReconcile();

	/**
	 * シーズンが空の行にシーズンを入れる（csv_id 指定。2026-10-21 追加）。
	 *
	 * @return 更新件数
	 */
	@Update("""
			UPDATE csv_detail_manage
			SET
			    season = #{season},
			    update_time = CURRENT_TIMESTAMP,
			    update_id = 'SYSTEM'
			WHERE
				csv_id = #{csvId}
			AND (season IS NULL OR BTRIM(season) = '')
			""")
	int updateSeasonIfBlank(@Param("csvId") String csvId, @Param("season") String season);

	/**
	 * まだ統計に取り込んでいない（check_fin_flg = '0'）行のシーズンを直す（csv_id 指定。2026-11-24 追加）。
	 * シーズンの開始日・終了日を管理画面で直したとき、試合日で判定し直したシーズンにそろえるため。
	 *
	 * @return 更新件数
	 */
	@Update("""
			UPDATE csv_detail_manage
			SET
			    season = #{season},
			    update_time = CURRENT_TIMESTAMP,
			    update_id = 'SYSTEM'
			WHERE
				csv_id = #{csvId}
			AND check_fin_flg = '0'
			AND season IS DISTINCT FROM #{season}
			""")
	int updateSeasonIfUnfinished(@Param("csvId") String csvId, @Param("season") String season);

	/**
	 * 同じ csv_id の行数（2026-11-23 追加）。
	 * シーズン切替後に同じ試合へ別シーズンが付いても、同じCSVは1行にするため。
	 */
	@Select("""
			SELECT COUNT(*) FROM csv_detail_manage WHERE csv_id = #{csvId}
			""")
	int countByCsvId(@Param("csvId") String csvId);

	/**
	 * csv_id 指定で、カテゴリ・シーズン・チーム・取り込み状態を更新する（2026-11-23 追加）。
	 * シーズンが空で来た場合は、既存のシーズンを残す。
	 *
	 * @return 更新件数
	 */
	@Update("""
			UPDATE csv_detail_manage
			SET
			    data_category = #{dataCategory},
			    season = COALESCE(NULLIF(BTRIM(#{season}), ''), season),
			    home_team_name = #{homeTeamName},
			    away_team_name = #{awayTeamName},
			    -- 2026-11-25: 反映済み（'1'）は取り込み待ちに戻さない（二重反映を防ぐ）
			    check_fin_flg = CASE WHEN check_fin_flg = '1' THEN '1' ELSE #{checkFinFlg} END,
			    update_time = CURRENT_TIMESTAMP,
			    update_id = 'ADMIN'
			WHERE
				csv_id = #{csvId}
			""")
	int updateByCsvId(CsvDetailManageEntity entity);

}
