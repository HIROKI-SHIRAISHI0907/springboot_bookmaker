package dev.mng.csvmng;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.BookDataEntity;
import dev.common.getstatinfo.GetStatInfo;
import jakarta.annotation.PostConstruct;

/**
 * 既存CSV情報読み取り処理
 */
@Component
public class ReaderCurrentCsvInfoBean {

	/** Configクラス */
	@Autowired
	private PathConfig config;

	/** CSV読み取り処理 */
	@Autowired
	private GetStatInfo getStatInfo;

	/** CSV通番情報 */
	private Map<String, List<Integer>> csvInfo;

	/**
	 * 既存CSV情報読み取り
	 */
	@PostConstruct
	public void init() {
		// パス
		final String PATH = config.getCsvFolder();

		// 1から順に採番済のCSVデータを全て取得
		Map<String, Map<String, List<BookDataEntity>>> getData = this.getStatInfo.getData(
				"0", null);

		Map<String, List<Integer>> csvInfo = new HashMap<String, List<Integer>>();
		for (Map.Entry<String, Map<String, List<BookDataEntity>>> ite : getData.entrySet()) {
			Map<String, List<BookDataEntity>> maps = ite.getValue();
			List<Integer> list = new ArrayList<Integer>();
			String fullPath = null;
			String versusData = null;
			for (Map.Entry<String, List<BookDataEntity>> ites : maps.entrySet()) {
				versusData = ites.getKey();
				break;
			}
			for (List<BookDataEntity> entities : maps.values()) {
				fullPath = entities.get(0).getFilePath();
				for (BookDataEntity subEntity : entities) {
					list.add(Integer.parseInt(subEntity.getSeq()));
				}
			}
			// CSV番号と通番情報を設定
			csvInfo.put(versusData + "-" +
					Integer.parseInt(fullPath.replace(PATH, "").replace(BookMakersCommonConst.CSV, "")), list);
		}
		this.csvInfo = csvInfo;
	}

	/**
	 * CSV情報を取得
	 * @return
	 */
	public Map<String, List<Integer>> getCsvInfo() {
		return csvInfo;
	}

}
