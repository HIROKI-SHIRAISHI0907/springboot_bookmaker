package dev.batch.repository.master;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import dev.common.entity.PointSettingEntity;

/**
 * point_setting_master 操作用リポジトリ
 *
 * 勝ち点設定の登録・更新・取得を行う。
 */
@Mapper
public interface PointSettingMasterBatchRepository {

	@Select("""
			    SELECT
			        country,
					league,
					win,
					lose,
					draw,
					remarks
			    FROM
			    	point_setting_master
			    WHERE
			        country = #{country} AND
			        league = #{league};
			""")
	List<PointSettingEntity> findPoints(@Param("country") String country,
			@Param("league") String league);

	@Select("""
			    SELECT
			        country,
					league,
					win,
					lose,
					draw,
					remarks
			    FROM
			    	point_setting_master;
			""")
	List<PointSettingEntity> findAllPoints();

	@Insert("""
			INSERT INTO point_setting_master (
				country,
				league,
				win,
				lose,
				draw,
				remarks,
				del_flg,
				register_id,
				register_time,
				update_id,
				update_time
			) VALUES (
				#{country},
				#{league},
				#{win},
				#{lose},
				#{draw},
				#{remarks},
				'0',
				'SYSTEM',
				NOW(),
				'SYSTEM',
				NOW()
			)
			""")
	int insert(PointSettingEntity entity);

}
