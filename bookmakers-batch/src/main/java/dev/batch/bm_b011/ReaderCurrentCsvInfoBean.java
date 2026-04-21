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

	private Map<String, List<Integer>> csvInfo = Collections.emptyMap();

	public void init() {
		this.csvInfo = getStatInfo.getCsvInfo("0", null);
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

	public Map<String, List<Integer>> getCsvInfo() {
		return csvInfo;
	}
}
