package dev.application.domain.repository.bm;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import dev.application.analyze.bm_m043.ModelEvaluationSummaryEntity;

/**
 * {@link ModelEvaluationSummaryEntity} のRepositoryです。
 */
@Mapper
public interface ModelEvaluationSummaryRepository {

	/**
	 * モデル評価サマリを登録します。
	 *
	 * @param entity 登録対象
	 * @return 登録件数
	 */
	@Insert("""
			INSERT INTO model_evaluation_summary (
			    match_id,
			    season,
			    country,
			    league_id,
			    league_name,
			    team_id,
			    team_name,
			    opponent_team_id,
			    opponent_team_name,
			    model_name,
			    model_version,
			    task_type,
			    target_name,
			    season_range,
			    validation_method,
			    accuracy,
			    precision_score,
			    recall_score,
			    f1_score,
			    roc_auc,
			    pr_auc,
			    brier_score,
			    mae,
			    rmse,
			    mape,
			    r2_score,
			    evaluated_at,
			    note,
			    register_id,
			    register_time,
			    update_id,
			    update_time
			) VALUES (
			    #{matchId},
			    #{season},
			    #{country},
			    #{leagueId},
			    #{leagueName},
			    #{teamId},
			    #{teamName},
			    #{opponentTeamId},
			    #{opponentTeamName},
			    #{modelName},
			    #{modelVersion},
			    #{taskType},
			    #{targetName},
			    #{seasonRange},
			    #{validationMethod},
			    #{accuracy},
			    #{precisionScore},
			    #{recallScore},
			    #{f1Score},
			    #{rocAuc},
			    #{prAuc},
			    #{brierScore},
			    #{mae},
			    #{rmse},
			    #{mape},
			    #{r2Score},
			    #{evaluatedAt},
			    #{note},
			    'SYSTEM',
			    CURRENT_TIMESTAMP,
			    'SYSTEM',
			    CURRENT_TIMESTAMP
			)
			""")
	int insert(ModelEvaluationSummaryEntity entity);
}
