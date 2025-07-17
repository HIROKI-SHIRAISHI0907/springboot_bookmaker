package dev.application.analyze.bm_m022;

import java.util.ArrayList;
import java.util.List;

import dev.common.constant.UniairConst;


/**
 * scoring_playstyle_past_dataに登録するロジック
 * @author shiraishitoshio
 *
 */
public class ScoringPlaystylePastDataDbInsert {

	/**
	 * 登録メソッド
	 * @param entities 登録entityリスト
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 */
	public void execute(List<StatsDiffEntity> entities) {
		String[] selList = new String[1];
		selList[0] = "id";

		List<StatsDiffEntity> insertEntities = new ArrayList<StatsDiffEntity>();
		for (StatsDiffEntity entity : entities) {
			String whereStr = "id = '" + entity.getId() + "'";

			List<List<String>> selectResultList = null;
			SqlMainLogic select = new SqlMainLogic();
			try {
				selectResultList = select.executeSelect(null, UniairConst.BM_M022, selList,
						whereStr, null, "1");
			} catch (Exception e) {
				System.err.println("scoring_playstyle_past_data select err searchData: " + e);
			}

			if (selectResultList.isEmpty()) {
				insertEntities.add(entity);
			}
		}

		if (!insertEntities.isEmpty()) {
			CsvRegisterImpl csvRegisterImpl = new CsvRegisterImpl();
			try {
				csvRegisterImpl.executeInsert(UniairConst.BM_M022,
						insertEntities, 1, insertEntities.size());
			} catch (Exception e) {
				System.err.println("scoring_playstyle_past_data insert err execute: " + e);
			}
		}
	}
}
