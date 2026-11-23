package dev.batch.bm_b011;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.getinfo.GetStatInfo;

@Component
public class ReaderCurrentCsvInfoBean {

	@Autowired
	private GetStatInfo getStatInfo;

	private Map<String, List<String>> csvInfo = Collections.emptyMap();

	public void init() {
		// 2026-11-23 範囲指定なしで全CSVを読む。
		// "0" を渡すと番号付きCSV（123.csv）の範囲絞り込みが働き、
		// 「国-リーグ-ラウンドN/ホーム-アウェー.csv」形式のCSVが全件除外されて「作成済み=0」になっていた。
		this.csvInfo = getStatInfo.getCsvInfo(null, null);
		if (this.csvInfo == null) {
			this.csvInfo = Collections.emptyMap();
		}
	}

	/**
	 * 対象フォルダ配下のみ既存CSV情報を読み込む。
	 *
	 * @param targetFolders 対象フォルダ一覧
	 */
	public void init(Set<String> targetFolders) {
		this.csvInfo = getStatInfo.getCsvInfoByFolders(targetFolders);
		if (this.csvInfo == null) {
			this.csvInfo = Collections.emptyMap();
		}
	}

	public Map<String, List<String>> getCsvInfo() {
		return csvInfo;
	}
}
