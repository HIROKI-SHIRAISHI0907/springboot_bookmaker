package dev.application.domain.repository.bm;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.application.analyze.bm_m098.CsvSeqManageEntity;

@Mapper
public interface CsvSeqManageRepository {

    @Insert("""
        INSERT INTO csv_seq_manage (
            id,
            job_name,
            last_success_csv,
            back_range,
            register_id,
            register_time,
            update_id,
            update_time
        ) VALUES (
            #{id},
            #{jobName},
            #{lastSuccessCsv},
            #{backRange},
            'ADMIN',
            CURRENT_TIMESTAMP,
            'ADMIN',
            CURRENT_TIMESTAMP
        )
        """)
    int insert(CsvSeqManageEntity entity);

    /**
     * 排他ロック取得（トランザクション内で呼ぶ）
     */
    @Select("""
        SELECT
            id,
            job_name,
            last_success_csv AS lastSuccessCsv,
            back_range       AS backRange
        FROM csv_seq_manage
        WHERE id = #{id}
        FOR UPDATE
        """)
    CsvSeqManageEntity selectForUpdate(@Param("id") int id);

    /**
     * 前進のみ更新（巻き戻り防止）
     * newLast > current のときだけ更新
     */
    @Update("""
        UPDATE csv_seq_manage
        SET last_success_csv = #{newLastSuccessCsv},
            update_time = CURRENT_TIMESTAMP,
            update_id = 'ADMIN'
        WHERE id = #{id}
          AND last_success_csv < #{newLastSuccessCsv}
        """)
    int updateLastSuccessIfGreater(@Param("id") int id,
                                   @Param("newLastSuccessCsv") int newLastSuccessCsv);
}
