package dev.batch.bm_b011;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.getinfo.GetStatInfo;

@Component
public class ReaderCurrentCsvInfoBean {

	@Autowired
	private GetStatInfo getStatInfo;

	/**
	 * key: S3相対キー（例: Japan-J1-ラウンド5/9.csv）
	 * value: seq一覧
	 */
	private Map<String, List<Integer>> csvInfo = Collections.emptyMap();

	public void init() {
		this.csvInfo = getStatInfo.getCsvInfo("0", null);
		if (this.csvInfo == null) {
			this.csvInfo = Collections.emptyMap();
		}
	}

	public Map<String, List<Integer>> getCsvInfo() {
		return csvInfo;
	}
}
