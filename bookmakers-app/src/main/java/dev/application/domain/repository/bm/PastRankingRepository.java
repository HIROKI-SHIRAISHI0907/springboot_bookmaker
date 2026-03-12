package dev.application.domain.repository.bm;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import dev.application.analyze.bm_m028.PastRankingEntity;

@Mapper
public interface PastRankingRepository {

	@Insert("""
			INSERT INTO past_ranking (
			    country,
			    league,
			    season_year,
			    match,
			    team,
			    win,
			    lose,
			    draw,
			    winning_points,
			    register_id,
			    register_time,
			    update_id,
			    update_time
			) VALUES (
			    #{country},
			    #{league},
			    #{seasonYear},
			    #{match},
			    #{team},
			    #{win},
			    #{lose},
			    #{draw},
			    #{winning_points},
			    'ADMIN',
			    CURRENT_TIMESTAMP,
			    'ADMIN',
			    CURRENT_TIMESTAMP
			)
			""")
	int insert(PastRankingEntity entity);

}
