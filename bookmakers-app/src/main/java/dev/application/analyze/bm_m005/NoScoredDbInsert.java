package dev.application.analyze.bm_m005;

import java.util.ArrayList;
import java.util.List;

import dev.common.constant.UniairConst;


/**
 * zero_score_dataに登録するロジック
 * @author shiraishitoshio
 *
 */
public class NoScoredDbInsert {

	/**
	 * 登録メソッド
	 * @param entities 登録entityリスト
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 */
	public void execute(List<NoGoalMatchStatisticsEntity> entities) {
		String[] selList = new String[1];
		selList[0] = "seq";

		List<NoGoalMatchStatisticsEntity> insertEntities = new ArrayList<NoGoalMatchStatisticsEntity>();
		for (NoGoalMatchStatisticsEntity entity : entities) {
			String whereStr = "seq = '" + entity.getSeq() + "'";

			List<List<String>> selectResultList = null;
			SqlMainLogic select = new SqlMainLogic();
			try {
				selectResultList = select.executeSelect(null, UniairConst.BM_M005, selList,
						whereStr, null, "1");
			} catch (Exception e) {
				System.err.println("zero_score_data select err searchData: " + e);
			}

			if (selectResultList.isEmpty()) {
				insertEntities.add(entity);
			}
		}

		if (!insertEntities.isEmpty()) {
			CsvRegisterImpl csvRegisterImpl = new CsvRegisterImpl();
			try {
				csvRegisterImpl.executeInsert(UniairConst.BM_M005,
						insertEntities, 1, insertEntities.size());
			} catch (Exception e) {
				System.err.println("zero_score_data insert err execute: " + e);
			}
		}
	}
}
