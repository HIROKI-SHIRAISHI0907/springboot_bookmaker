package dev.batch.bm_b011;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 返却DTO */
public class CsvBuildPlan {

	/** 新規作成対象（既存CSVに無かった組み合わせ） */
	Map<String, List<Integer>> newTargets = new LinkedHashMap<>();

	/** 再作成対象のマップ：<既存CSVのS3相対キー, 再作成対象グループ> */
	Map<String, List<Integer>> recreateByCsvKey = new LinkedHashMap<>();

	/**
	 * newメソッド
	 *
	 * @param prefix プレフィックス
	 * @param groups seqグループ
	 * @return this
	 */
	CsvBuildPlan onlyNew(String prefix, List<Integer> groups) {
		if (groups == null || groups.isEmpty()) {
			return this;
		}

		int maxSeq = 0;
		for (String key : newTargets.keySet()) {
			if (key != null && key.startsWith(prefix)) {
				String suffix = key.substring(prefix.length());
				try {
					int n = Integer.parseInt(suffix);
					if (n > maxSeq) {
						maxSeq = n;
					}
				} catch (NumberFormatException ignore) {
					/* 数値でないキーは無視 */ }
			}
		}

		String nextKey = prefix + (maxSeq + 1);
		this.newTargets.put(nextKey, groups);
		return this;
	}

	/**
	 * recreateメソッド
	 *
	 * @param key 既存CSVのS3相対キー
	 * @param groups seqグループ
	 * @return this
	 */
	CsvBuildPlan onlyRecreate(String key, List<Integer> groups) {
		if (groups == null || groups.isEmpty()) {
			return this;
		}
		this.recreateByCsvKey.put(key, groups);
		return this;
	}

}
