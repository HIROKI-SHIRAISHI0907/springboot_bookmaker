package dev.batch.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import dev.common.entity.CsvDetailManageEntity;

@Mapper
public interface CsvDetailManageBatchRepository {

	/**
     * 削除対象取得
     * ExportCsvService が登録した csv_id を取得するために使う
     */
	@Select({
        "<script>",
        "SELECT",
        "  csv_id            AS csvId,",
        "  data_category     AS dataCategory,",
        "  season            AS season,",
        "  home_team_name    AS homeTeamName,",
        "  away_team_name    AS awayTeamName,",
        "  check_fin_flg     AS checkFinFlg",
        "FROM csv_detail_manage",
        "WHERE season IS NOT NULL",
        "  AND TRIM(season) &lt;&gt; ''",
        "  AND (",
        "    <foreach collection='dataCategoryPrefixes' item='prefix' separator=' OR '>",
        "      data_category LIKE CONCAT(#{prefix}, '%')",
        "    </foreach>",
        "  )",
        "</script>"
    })
    List<CsvDetailManageEntity> findDeleteTargetsByDataCategoryPrefixes(
            @Param("dataCategoryPrefixes") List<String> dataCategoryPrefixes);

    /**
     * csv_id 指定で削除
     * season_year が存在するデータのみ削除
     */
	@Delete({
	    "<script>",
	    "DELETE FROM csv_detail_manage",
	    "WHERE season IS NOT NULL",
	    "  AND TRIM(season) &lt;&gt; ''",
	    "  AND csv_id IN",
	    "  <foreach collection='csvIds' item='csvId' open='(' separator=',' close=')'>",
	    "    #{csvId}",
	    "  </foreach>",
	    "</script>"
	})
	int deleteByCsvIds(@Param("csvIds") List<String> csvIds);

}
