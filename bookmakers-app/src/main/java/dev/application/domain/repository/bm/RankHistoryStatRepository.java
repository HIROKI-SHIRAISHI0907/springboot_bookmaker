package dev.application.domain.repository.bm;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import dev.application.analyze.bm_m033.RankHistoryEntity;

/**
 * 順位履歴統計データ登録用Repositoryインターフェース
 * 対象テーブル: rank_history
 */
@Mapper
public interface RankHistoryStatRepository {

	@Insert("""
			    INSERT INTO rank_history (
			        country,
			        league,
			        match,
			        team,
			        rank,
			        register_id,
			        register_time,
			        update_id,
			        update_time
			    ) VALUES (
			        #{country},
			        #{league},
			        #{match},
			        #{team},
			        #{rank},
			        #{registerId}, CAST(#{registerTime} AS timestamptz), #{updateId}, CAST(#{updateTime}  AS timestamptz)
			    );
			""")
	int insert(RankHistoryEntity entity);

	@Update("""
		    UPDATE rank_history
		    SET
		    	rank = #{rank}
		    WHERE
		        country = #{country} AND
		        league = #{league} AND
		        match = #{match} AND
		        team = #{team};
		""")
	int update(RankHistoryEntity entity);

	@Select("""
		    SELECT COUNT(*)
		    FROM rank_history
		    WHERE
		        country = #{country} AND
		        league = #{league} AND
		        match = #{match} AND
		        team = #{team};
		""")
	int select(RankHistoryEntity entity);

}
