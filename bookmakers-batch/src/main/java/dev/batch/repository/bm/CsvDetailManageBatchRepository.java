package dev.batch.repository.bm;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CsvDetailManageBatchRepository {

    /**
     * csv_id 指定で削除
     * season_year が存在するデータのみ削除
     */
    @Delete({
        "<script>",
        "DELETE FROM csv_detail_manage",
        "WHERE season_year IS NOT NULL",
        "  AND TRIM(season_year) &lt;&gt; ''",
        "  AND csv_id IN",
        "  <foreach collection='csvIds' item='csvId' open='(' separator=',' close=')'>",
        "    #{csvId}",
        "  </foreach>",
        "</script>"
    })
    int deleteByCsvIds(@Param("csvIds") List<String> csvIds);
}
