package dev.application.domain.repository.master;

import java.util.List;

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
public interface PointSettingMasterRepository {

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

}
