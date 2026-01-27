package dev.application.domain.repository.bm;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.application.analyze.bm_m022.CsvSequenceEntity;

/**
 * CsvSequenceRepository
 * @author shiraishitoshio
 *
 */
@Mapper
public interface CsvSequenceRepository {

	/**
	 * 初期行作成（id=1固定）
	 * 既に存在する場合は何もしない（PostgreSQL）
	 */
	@Insert("""
			INSERT INTO csv_sequence (id, csv_number)
			VALUES (#{id}, #{csvNumber})
			ON CONFLICT (id) DO NOTHING;
			""")
	int initRow(CsvSequenceEntity entity);

	/**
	 * 現在のチェックポイント（最後に読み込んだcsv_number）を取得
	 */
	@Select("""
			SELECT
			    id,
			    csv_number AS csvNumber
			FROM csv_sequence
			WHERE id = #{id};
			""")
	CsvSequenceEntity selectById(@Param("id") int id);

	/**
	 * 最後に読み込んだcsv_numberを更新（単純更新）
	 */
	@Update("""
			UPDATE csv_sequence
			SET csv_number = #{csvNumber}
			WHERE id = #{id};
			""")
	int updateCsvNumber(@Param("id") int id, @Param("csvNumber") int csvNumber);

	/**
	 * 進める方向にだけ更新したい場合（巻き戻り防止）
	 * csvNumber が現在値より大きい時だけ更新
	 */
	@Update("""
			UPDATE csv_sequence
			SET csv_number = #{csvNumber}
			WHERE id = #{id}
			  AND csv_number < #{csvNumber};
			""")
	int updateCsvNumberIfGreater(@Param("id") int id, @Param("csvNumber") int csvNumber);

	/**
	 * 排他を取りたい場合の「ロック取得」用途
	 * トランザクション内で呼んでください（SELECT FOR UPDATE）
	 */
	@Select("""
			SELECT
			    id,
			    csv_number AS csvNumber
			FROM csv_sequence
			WHERE id = #{id}
			FOR UPDATE;
			""")
	CsvSequenceEntity selectForUpdate(@Param("id") int id);
}
