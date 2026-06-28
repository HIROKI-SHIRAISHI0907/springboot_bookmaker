package dev.application.domain.repository.bm;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import dev.application.analyze.bm_m044.ModelCalibrationDetailEntity;

/**
 * model_calibration_detail を操作する Repository です。
 */
@Mapper
public interface ModelCalibrationDetailRepository {

    /**
     * モデルのキャリブレーション詳細を1件登録します。
     *
     * @param entity 登録対象Entity
     * @return 登録件数
     */
    @Insert("""
        INSERT INTO model_calibration_detail (
            model_name,
            model_version,
            target_name,
            bin_index,
            predicted_prob_avg,
            actual_rate,
            sample_count,
            register_id,
            register_time,
            update_id,
            update_time
        ) VALUES (
            #{modelName},
            #{modelVersion},
            #{targetName},
            #{binIndex},
            #{predictedProbAvg},
            #{actualRate},
            #{sampleCount},
            'SYSTEM',
            CURRENT_TIMESTAMP,
            'SYSTEM',
            CURRENT_TIMESTAMP
        )
        """)
    int insert(ModelCalibrationDetailEntity entity);
}
