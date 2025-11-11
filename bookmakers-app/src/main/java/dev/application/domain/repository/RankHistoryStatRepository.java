package dev.application.domain.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import dev.application.analyze.bm_m033.RankHistoryEntity;

/**
 * 順位履歴統計データ登録用Repositoryインターフェース
 * 対象テーブル: rank_history
 */
@Mapper
public interface RankHistoryStatRepository {

	@Insert("""
			    INSERT INTO no_goal_match_stats (
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

}
