package dev.mng.domain.repository;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.mng.analyze.bm_c002.TargetLeagueStatusEntity;


@Mapper
public interface TargetLeagueStatusRepository {

	@Insert("""
			INSERT INTO csv_mng
			  (country, league, status, register_id, register_time, update_id, update_time)
			VALUES
			  (#{country}, #{league}, #{status}, #{registerId}, #{registerTime}, #{updateId}, #{updateTime})
			""")
	int insert(TargetLeagueStatusEntity entity);

	@Select("""
			SELECT
			  id,
			  country,
			  league,
			  status,
			  register_id,
			  register_time,
			  update_id,
			  update_time
			FROM csv_mng
			WHERE country = #{country} AND
			league = #{league}
			""")
	List<TargetLeagueStatusEntity> findByData(String country, String league);

	// 全件
	@Select("""
			SELECT
			  id,
			  country,
			  league,
			  status,
			  register_id  AS registerId,
			  register_time AS registerTime,
			  update_id    AS updateId,
			  update_time  AS updateTime
			FROM csv_mng
			""")
	List<TargetLeagueStatusEntity> findAll();

	// status で検索（必要なら）
	@Select("""
			SELECT
			  id,
			  country,
			  league,
			  status,
			  register_id,
			  register_time,
			  update_id,
			  update_time
			FROM csv_mng
			WHERE status = #{status}
			""")
	List<TargetLeagueStatusEntity> findByStatus(String status);

	// 全項目更新（id が必須）
	@Update("""
			UPDATE csv_mng
			SET
			  status       = #{status}
			WHERE id = #{id}
			""")
	int updateById(String id, String status);

}
