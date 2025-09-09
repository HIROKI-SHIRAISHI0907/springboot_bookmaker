package dev.mng.csvmng;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 返却DTO */
public class CsvBuildPlan {

	/** 新規作成対象（テキストに無かった組み合わせ） */
	Map<String, List<Integer>> newTargets = new TreeMap<>();

	/** 再作成対象のマップ：<旧CSV番号, その番号に紐づく再作成グループ群> */
	Map<Integer, List<Integer>> recreateByCsvNo = new TreeMap<>();

	/**
	 * newメソッド
	 * @param prefix
	 * @param groups
	 * @return
	 */
	CsvBuildPlan onlyNew(String prefix, List<Integer> groups) {
		if (groups == null || groups.isEmpty())
			return this;

		// 1) 同じ prefix のキーのうち最大の連番を取得
		int maxSeq = 0;
		for (String key : newTargets.keySet()) {
			if (key != null && key.startsWith(prefix)) {
				String suffix = key.substring(prefix.length());
				try {
					int n = Integer.parseInt(suffix);
					if (n > maxSeq)
						maxSeq = n;
				} catch (NumberFormatException ignore) {
					/* 数値でないキーは無視 */ }
			}
		}

		// 2) 次の連番でキーを作成
		String nextKey = prefix + (maxSeq + 1);
		this.newTargets.put(nextKey, groups);
		return this;
	}

	/**
	 * recreateメソッド
	 * @param key
	 * @param groups
	 * @return
	 */
	CsvBuildPlan onlyRecreate(Integer key, List<Integer> groups) {
		if (groups == null || groups.isEmpty())
			return this;
		// 2) CSV番号キーを作成
		this.recreateByCsvNo.put(key, groups);
		return this;
	}

}
